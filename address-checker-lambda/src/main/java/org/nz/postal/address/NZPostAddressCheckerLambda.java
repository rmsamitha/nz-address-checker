/*
 * Copyright 2025 Samitha Chathuranga
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nz.postal.address;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.HttpHeaders;
import org.nz.postal.address.exception.BadRequestException;
import org.nz.postal.address.exception.UpstreamServerException;

import static java.net.HttpURLConnection.*;
import static org.nz.postal.address.Constants.*;

/**
 * Lambda function to check NZ Post addresses using the NZ Post Address Checker API.
 * It retrieves an OAuth token and uses it to call the address suggestion API.
 */
public class NZPostAddressCheckerLambda implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static final String ADDRESS_SUGGEST_API_URL_ENV_VAR = "NZ_POST_ADDRESS_CHECKER_SUGGEST_API_URL";
    private static final String NZ_POST_API_OAUTH_TOKEN_URL_ENV_VAR = "NZ_POST_API_OAUTH_TOKEN_URL";
    private static String cachedToken = null;
    private static long tokenExpiry = 0;
    private static final Logger log = LogManager.getLogger(NZPostAddressCheckerLambda.class);
    private CloseableHttpClient httpClient;
    private static final String QUERY_PARAM_Q = "q";
    private static final String QUERY_PARAM_MAX = "max";

    /**
     * Entry point for your AWS Lambda function which processes incoming API Gateway requests
     */
    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        try {
            Map<String, String> queryParams = event.getQueryStringParameters();
            if (queryParams == null || !queryParams.containsKey(QUERY_PARAM_Q)) {
                Utils.handleBadRequestError("Missing " + QUERY_PARAM_Q + "query parameter.", HTTP_BAD_REQUEST);
            }

            String query = queryParams.get(QUERY_PARAM_Q);
            String max = queryParams.getOrDefault(QUERY_PARAM_MAX, "5");

            if (log.isDebugEnabled()) {
                log.debug("Received request to check NZ Post Address. Query: " + query + ", Max: " + max);
            }

            // Get valid OAuth token
            String token = getAuthToken();
            // Call NZ Post suggest API
            String apiResponse = callNZPostSuggestAPI(query, max, token);
            log.trace("NZPostSuggest API response: " + apiResponse);
            response.setStatusCode(HTTP_OK);
            Map<String, String> headers = new HashMap<>();
            headers.put(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON);
            //TODO: In production, change 'Access-Control-Allow-Origin' to the specific domain that the web app is hosted
            headers.put(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            response.setHeaders(headers);
            response.setBody(apiResponse);
        } catch (UpstreamServerException e) {
            return Utils.buildBadGatewayResponse("Error in upstream services");
        } catch (BadRequestException e) {
            return Utils.buildBadRequestResponse(e.getMessage());
        } catch (Exception e) {
            return Utils.buildInternalServerErrorResponse(e.getMessage());
        }
        return response;
    }

    /**
     * Retrieves an OAuth token from the NZ Post API.
     * Caches the token and its expiry time to avoid unnecessary requests.
     *
     * @return OAuth token as a String
     * @throws Exception if there is an error fetching the token
     */
    private String getAuthToken() throws Exception {
        long now = System.currentTimeMillis() / 1000;

        if (cachedToken != null && tokenExpiry > now) {
            if (log.isDebugEnabled()) {
                log.debug("Using cached token. This cached token expires at: " + tokenExpiry);
            }
            return cachedToken;
        }

        String tokenEPurl = System.getenv(NZ_POST_API_OAUTH_TOKEN_URL_ENV_VAR);
        httpClient = Utils.getHttpClient();

        URIBuilder uriBuilder = new URIBuilder(tokenEPurl);
        uriBuilder.addParameter("grant_type", CLIENT_CREDENTIALS);
        uriBuilder.addParameter("client_id", Utils.getClientId());
        uriBuilder.addParameter("client_secret", Utils.getClientSecret());

        HttpPost getTokenHttpRequest = new HttpPost(uriBuilder.build());
        getTokenHttpRequest.addHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED);

        try (CloseableHttpResponse response = httpClient.execute(getTokenHttpRequest)) {
            if (response.getStatusLine().getStatusCode() == HTTP_OK) {
                String responseContent = EntityUtils.toString(response.getEntity());
                ObjectMapper mapper = new ObjectMapper();
                JsonNode jsonNode = mapper.readTree(responseContent);

                String accessToken = jsonNode.get("access_token").asText();
                String expiresInStr = jsonNode.get("expires_in").asText();

                long expiresIn = Long.parseLong(expiresInStr);
                cachedToken = accessToken;
                tokenExpiry = now + expiresIn - 60; // refresh 1 min earlier
                log.info("Fetched new token with expiry time " + tokenExpiry);
            } else {
                String errorMessage = "Error fetching access token. " +
                        response.getStatusLine().getReasonPhrase();
                Utils.handleUpstreamServerError(errorMessage, response.getStatusLine().getStatusCode());
            }
        }
        return cachedToken;
    }

    /**
     * Calls the NZ Post Address Checker Suggest API with the provided query and max value.
     *
     * @param queryValue The search query for address suggestions
     * @param maxValue   The maximum number of suggestions to return
     * @param token      The OAuth token for authentication
     * @return JSON response from the NZ-Post-Suggest API
     * @throws Exception if there is an error calling the API
     */
    private String callNZPostSuggestAPI(String queryValue, String maxValue, String token) throws Exception {
        String nzPostAddressCheckerSuggestApiUrl = System.getenv(ADDRESS_SUGGEST_API_URL_ENV_VAR);
        URIBuilder uriBuilder = new URIBuilder(nzPostAddressCheckerSuggestApiUrl);
        uriBuilder.addParameter(QUERY_PARAM_Q, queryValue);
        uriBuilder.addParameter(QUERY_PARAM_MAX, maxValue);

        HttpGet nzPostSuggestAPIRequest = new HttpGet(uriBuilder.build());
        nzPostSuggestAPIRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        nzPostSuggestAPIRequest.addHeader("client_id", Utils.getClientId());
        nzPostSuggestAPIRequest.addHeader(HttpHeaders.ACCEPT, APPLICATION_JSON);

        try (CloseableHttpResponse response = httpClient.execute(nzPostSuggestAPIRequest)) {
            if (response.getStatusLine().getStatusCode() == HTTP_OK) {
                log.debug("NZPostSuggest API returned success response. status code: " + HTTP_OK);
                return EntityUtils.toString(response.getEntity());
            } else {
                String errorMessage = "Error occurred while calling NZPostSuggest API: " +
                        response.getStatusLine().getReasonPhrase();
                Utils.handleUpstreamServerError(errorMessage, response.getStatusLine().getStatusCode());
            }
        }
        return null;
    }
}
