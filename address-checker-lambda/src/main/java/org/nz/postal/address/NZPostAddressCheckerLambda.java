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

import static java.net.HttpURLConnection.HTTP_OK;
import static org.nz.postal.address.Constants.*;

public class NZPostAddressCheckerLambda implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String NZ_POST_API_OAUTH_TOKEN_URL = System.getenv("NZ_POST_API_OAUTH_TOKEN_URL");
    private static final String NZ_POST_ADDRESS_CHECKER_SUGGEST_API_URL = System.getenv("NZ_POST_ADDRESS_CHECKER_SUGGEST_API_URL");
    private static String cachedToken = null;
    private static long tokenExpiry = 0;

    private static final Logger log = LogManager.getLogger(NZPostAddressCheckerLambda.class);

    private CloseableHttpClient httpClient;

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();

        try {
            Map<String, String> queryParams = event.getQueryStringParameters();
            if (queryParams == null || !queryParams.containsKey("q")) {
                response.setStatusCode(400);
                response.setBody("{\"error\": \"Missing 'q' query parameter.\"}");
                return response;
            }

            String query = queryParams.get("q");
            String max = queryParams.getOrDefault("max", "5");

            log.info("Received request to check NZ Post Address. Query: " + query + ", Max: " + max);

            // Get valid OAuth token
            String token = getAuthToken();

            // Call NZ Post suggest API
            String apiResponse = callNZPostSuggestAPI(query, max, token);
            log.trace("NZPostSuggest API response: " + apiResponse);

            response.setStatusCode(200);
            Map<String, String> headers = new HashMap<>();
            headers.put(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON);
            //TODO: In production, change 'Access-Control-Allow-Origin' to the specific domain that the web app is hosted
            headers.put(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            response.setHeaders(headers);
            response.setBody(apiResponse);

        } catch (Exception e) {
            response.setStatusCode(500);
            Map<String, String> headers = new HashMap<>();
            //TODO: change 'Access-Control-Allow-Origin' to specific domain in production
            headers.put(ACCESS_CONTROL_ALLOW_ORIGIN, "*");
            response.setHeaders(headers);
            response.setBody("{\"error\": \"" + e.getMessage() + "\"}");
        }
        return response;
    }

    private String getAuthToken() throws Exception {
        long now = System.currentTimeMillis() / 1000;

        if (cachedToken != null && tokenExpiry > now) {
            if (log.isDebugEnabled()) {
                log.debug("Using cached token. This cached token expires at: " + tokenExpiry);
            }
            return cachedToken;
        }

        String tokenEPurl = System.getenv(NZ_POST_API_OAUTH_TOKEN_URL);

        httpClient = Utils.getHttpClient();

        URIBuilder uriBuilder = new URIBuilder(tokenEPurl);
        uriBuilder.addParameter("grant_type", CLIENT_CREDENTIALS);
        uriBuilder.addParameter("client_id", Utils.getClientId());
        uriBuilder.addParameter("client_secret", Utils.getClientSecret());
        //uriBuilder.addParameter("client_id", "38875319c09148048016152834a2dde4");
        //uriBuilder.addParameter("client_secret", "06bd03bEE99C4D92ab7cc8F441450bBd");

        HttpPost getTokenHttpRequest = new HttpPost(uriBuilder.build());
        getTokenHttpRequest.addHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_X_WWW_FORM_URLENCODED);

        try (CloseableHttpResponse response = httpClient.execute(getTokenHttpRequest)) {
            if (response.getStatusLine().getStatusCode() == 200) {
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
                throw new Exception("Error fetching access token");
            }
        }

        return cachedToken;
    }

    private String callNZPostSuggestAPI(String queryValue, String maxValue, String token) throws Exception {
        URIBuilder uriBuilder = new URIBuilder(NZ_POST_ADDRESS_CHECKER_SUGGEST_API_URL);
        uriBuilder.addParameter("q", queryValue);
        uriBuilder.addParameter("max", maxValue);

        HttpGet nzPostSuggestAPIRequest = new HttpGet(uriBuilder.build());
        nzPostSuggestAPIRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        nzPostSuggestAPIRequest.addHeader("client_id", Utils.getClientId());
        //nzPostSuggestAPIRequest.addHeader("client_id", "38875319c09148048016152834a2dde4");
        nzPostSuggestAPIRequest.addHeader(HttpHeaders.ACCEPT, APPLICATION_JSON);

        try (CloseableHttpResponse response = httpClient.execute(nzPostSuggestAPIRequest)) {
            if (response.getStatusLine().getStatusCode() == HTTP_OK) {
                log.debug("NZPostSuggest API returned success response. status code: " + HTTP_OK);
                return EntityUtils.toString(response.getEntity());
            } else {
                log.error("Failed to call NZPostSuggest API. Status code: " + response.getStatusLine().getStatusCode());
                throw new Exception("Error calling NZPostSuggest API");
            }
        }
    }
}
