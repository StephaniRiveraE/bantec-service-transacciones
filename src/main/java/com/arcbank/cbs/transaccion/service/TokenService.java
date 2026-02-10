package com.arcbank.cbs.transaccion.service;

import com.arcbank.cbs.transaccion.client.AuthClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final AuthClient authClient;

    @Value("${app.security.oauth.client-id}")
    private String clientId;

    @Value("${app.security.oauth.client-secret}")
    private String clientSecret;

    @Value("${app.security.oauth.scope}")
    private String scope;

    private String accessToken;
    private LocalDateTime tokenExpiry;

    public synchronized String getAccessToken() {
        if (accessToken == null || LocalDateTime.now().isAfter(tokenExpiry)) {
            refreshAccessToken();
        }
        return accessToken;
    }

    private void refreshAccessToken() {
        log.info("🔄 Refreshing OAuth Access Token...");
        try {
            String authHeader = "Basic " + Base64.getEncoder()
                    .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

            Map<String, String> formParams = new HashMap<>();
            formParams.put("grant_type", "client_credentials");
            formParams.put("scope", scope);

            Map<String, Object> response = authClient.getToken(authHeader, formParams);

            if (response != null && response.containsKey("access_token")) {
                this.accessToken = (String) response.get("access_token");
                Integer expiresIn = (Integer) response.get("expires_in");
                // Refresh 5 minutes before actual expiry
                this.tokenExpiry = LocalDateTime.now().plusSeconds(expiresIn - 300);
                log.info("✅ Token refreshed successfully. Expires in: {}s", expiresIn);
            } else {
                throw new RuntimeException("Failed to retrieve access token: " + response);
            }
        } catch (Exception e) {
            log.error("❌ Error refreshing token: {}", e.getMessage());
            throw new RuntimeException("Authentication failed", e);
        }
    }
}
