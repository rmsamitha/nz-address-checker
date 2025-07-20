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

public class Utils {
    private static Map<String, String> cachedSecrets;
    private static CloseableHttpClient httpClient;
    private static final Logger log = LogManager.getLogger(Utils.class);

    public static void loadSecrets() throws Exception {
        if (cachedSecrets != null) {
            log.debug("Secrets loaded from cache.");
            return; // Secrets already loaded
        }
        String secretName = "NZPostAPIClientCredentials"; // TODO: check if need to get this from environment variable
        Region region = Region.of("ap-southeast-2");

        // Create a Secrets Manager client
        try (SecretsManagerClient client = SecretsManagerClient.builder()
                .region(region)
                .build()) {
            GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();
            log.info("Retrieving secret value for " + secretName);
            GetSecretValueResponse getSecretValueResponse = client.getSecretValue(getSecretValueRequest);

            String secretString = getSecretValueResponse.secretString();
            log.debug("Retrieved secret string: " + secretString);
            ObjectMapper mapper = new ObjectMapper();
            cachedSecrets = mapper.readValue(secretString, Map.class);
        } catch (JsonMappingException e) {
            // Handle JSON processing exceptions
            log.error("Error mapping secret JSON to Map: " + e.getMessage());
            throw e;
        } catch (JsonParseException e) {
            throw e;
        } catch (IOException e) {
            log.error("Error parsing JSON to Map: " + e.getMessage());
            throw e;
        }
    }

    public static String getClientId() throws Exception {
        loadSecrets();
        return cachedSecrets.get("client_id");
    }

    public static String getClientSecret() throws Exception {
        loadSecrets();
        return cachedSecrets.get("client_secret");
    }

    /**
     * Sets the HTTP client with custom configurations.
     */
    public static void setHttpClient() {
        httpClient = HttpClients.custom()
                .setMaxConnTotal(100) // Set maximum total connections
                .setMaxConnPerRoute(10) // Set maximum connections per route
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
