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

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

/**
 * Utility class for managing AWS Secrets Manager secrets and HTTP client configurations.
 */
public class Utils {
    private static Map<String, String> cachedSecrets;
    private static CloseableHttpClient httpClient;
    private static final Logger log = LogManager.getLogger(Utils.class);

    /**
     * Loads secrets (client_id and client_secret related to NZ Post AddressChecker API invocation) from AWS Secrets
     * Manager and caches them.
     *
     * @throws Exception if there is an error retrieving or parsing the secret
     */
    public static void loadSecrets() throws Exception {
        if (cachedSecrets != null) {
            log.debug("Secrets are already available in cache");
            return; // Secrets already loaded
        }
        String secretName = System.getenv("NZ_POST_API_CLIENT_SECRET_NAME");
        Region region = Region.of(System.getenv("AWS_REGION"));

        // Create a Secrets Manager client
        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .region(region)
                .build()) {
            GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();
            GetSecretValueResponse getSecretValueResponse = client.getSecretValue(getSecretValueRequest);
            String secretString = getSecretValueResponse.secretString();
            log.debug("Retrieved secret values related to NZ Post AddressChecker API invocation");
            ObjectMapper mapper = new ObjectMapper();
            cachedSecrets = mapper.readValue(secretString, Map.class);
        } catch (JsonMappingException e) {
            log.error("Error mapping secret JSON to Map: " + e.getMessage());
            throw e;
        } catch (JsonParseException e) {
            throw e;
        } catch (IOException e) {
            log.error("Error parsing JSON to Map: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Retrieves the client ID from the loaded secrets.
     *
     * @return the client ID
     * @throws Exception if secrets are not loaded or if there is an error retrieving the client ID
     */
    public static String getClientId() throws Exception {
        loadSecrets();
        return cachedSecrets.get("client_id");
    }

    /**
     * Retrieves the client secret from the loaded secrets.
     *
     * @return the client secret
     * @throws Exception if secrets are not loaded or if there is an error retrieving the client secret
     */
    public static String getClientSecret() throws Exception {
        loadSecrets();
        return cachedSecrets.get("client_secret");
    }

    /**
     * Sets the HTTP client with custom configurations.
     */
    public static void setHttpClient() {
        httpClient = HttpClients.custom()
                .setMaxConnTotal(100)
                .setMaxConnPerRoute(10)
                .build();
        log.debug("New HTTP client initialized with custom configurations.");
    }

    /**
     * Returns the HTTP client.
     *
     * @return CloseableHttpClient instance
     */
    public static CloseableHttpClient getHttpClient() {
        if (httpClient == null) {
            setHttpClient();
        } else {
            log.debug("Using existing HTTP client instance.");
        }
        return httpClient;
    }
}
