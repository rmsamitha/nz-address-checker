package org.nz.postal.address;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.apache.http.client.methods.HttpGet;

public class NZPostAddressCheckerLambda implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

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
            headers.put("Content-Type", "application/json");
            //TODO: change 'Access-Control-Allow-Origin' to specific domain in production
            headers.put("Access-Control-Allow-Origin", "*");
            response.setHeaders(headers);
            response.setBody(apiResponse);

        } catch (Exception e) {
            response.setStatusCode(500);
            Map<String, String> headers = new HashMap<>();
            //TODO: change 'Access-Control-Allow-Origin' to specific domain in production
            headers.put("Access-Control-Allow-Origin", "*");
            response.setHeaders(headers);
            response.setBody("{\"error\": \"" + e.getMessage() + "\"}");
        }
        return response;
    }

    private String getAuthToken() throws Exception {
        long now = System.currentTimeMillis() / 1000;

        if (cachedToken != null && tokenExpiry > now) {
            if (log.isDebugEnabled()) {
                log.debug("Using cached token, expires at: " + tokenExpiry);
            }
            return cachedToken;
        }

        String tokenEPurl = "https://oauth.nzpost.co.nz/as/token.oauth2"; // TODO: get from config

        httpClient = Utils.getHttpClient();

        URIBuilder uriBuilder = new URIBuilder(tokenEPurl);
        uriBuilder.addParameter("grant_type", "client_credentials");
        uriBuilder.addParameter("client_id", Utils.getClientId());
        uriBuilder.addParameter("client_secret", Utils.getClientSecret());
        //uriBuilder.addParameter("client_id", "38875319c09148048016152834a2dde4");
        //uriBuilder.addParameter("client_secret", "06bd03bEE99C4D92ab7cc8F441450bBd");

        HttpPost getTokenHttpRequest = new HttpPost(uriBuilder.build());
        getTokenHttpRequest.addHeader("Content-Type", "application/x-www-form-urlencoded");

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
        URIBuilder uriBuilder = new URIBuilder("https://api.nzpost.co.nz/addresschecker/1.0/suggest");
        uriBuilder.addParameter("q", queryValue);
        uriBuilder.addParameter("max", maxValue);

        HttpGet nzPostSuggestAPIRequest = new HttpGet(uriBuilder.build());
        nzPostSuggestAPIRequest.addHeader("Authorization", "Bearer " + token);
        nzPostSuggestAPIRequest.addHeader("client_id", Utils.getClientId());
        //nzPostSuggestAPIRequest.addHeader("client_id", "38875319c09148048016152834a2dde4");
        nzPostSuggestAPIRequest.addHeader("Accept", "application/json");

        try (CloseableHttpResponse response = httpClient.execute(nzPostSuggestAPIRequest)) {
            if (response.getStatusLine().getStatusCode() == 200) {
                log.debug("NZPostSuggest API returned success response.");
                return EntityUtils.toString(response.getEntity());
            } else {
                log.error("Failed to call NZPostSuggest API. Status code: " + response.getStatusLine().getStatusCode());
                throw new Exception("Error calling NZPostSuggest API");
            }
        }
    }
}
