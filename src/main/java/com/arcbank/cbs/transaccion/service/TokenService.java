package com.arcbank.cbs.transaccion.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Service
public class TokenService {

    @Value("${app.security.oauth.token-url}")
    private String tokenUrl;

    @Value("${app.security.oauth.client-id}")
    private String clientId;

    @Value("${app.security.oauth.client-secret}")
    private String clientSecret;

    @Value("${app.security.oauth.scope}")
    private String scope;

    private String accessToken;
    private LocalDateTime tokenExpiry;
    private final RestTemplate restTemplate = new RestTemplate();

    public synchronized String getAccessToken() {
        if (accessToken != null && tokenExpiry != null && LocalDateTime.now().isBefore(tokenExpiry)) {
            return accessToken;
        }
        refreshAccessToken();
        return accessToken;
    }

    private void refreshAccessToken() {
        log.info("🔄 Refreshing OAuth Access Token via RestTemplate...");
        try {
            String fullUrl = tokenUrl + "/oauth2/token";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String auth = clientId + ":" + clientSecret;
            String authHeader = "Basic " + Base64.getEncoder()
                    .encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", authHeader);

            MultiValueMap<String, String> formParams = new LinkedMultiValueMap<>();
            formParams.add("grant_type", "client_credentials");
            if (scope != null && !scope.isBlank()) {
                formParams.add("scope", scope);
            }

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formParams, headers);

            log.debug("POST {} con scope {}", fullUrl, scope);
            ResponseEntity<Map> response = restTemplate.postForEntity(fullUrl, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map body = response.getBody();
                this.accessToken = (String) body.get("access_token");

                Number expiresIn = (Number) body.get("expires_in");
                long seconds = expiresIn != null ? expiresIn.longValue() : 3600;
                // Refresh 5 minutes before actual expiry
                this.tokenExpiry = LocalDateTime.now().plusSeconds(seconds - 300);
                log.info("✅ Token refreshed successfully. Expires in: {}s", seconds);
            } else {
                throw new RuntimeException("Failed to retrieve access token: " + response);
            }
        } catch (Exception e) {
            log.error("❌ Error refreshing token: {}", e.getMessage());
            throw new RuntimeException("Authentication failed", e);
        }
    }
}
