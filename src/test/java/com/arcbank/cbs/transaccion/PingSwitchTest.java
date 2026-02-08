package com.arcbank.cbs.transaccion;

import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.Map;

public class PingSwitchTest {
    public static void main(String[] args) {
        String switchUrl = "https://switch-interbank.ddns.net/api/v1/red/bancos";
        System.out.println("🌐 [Diagnostic] Attempting to reach Switch at: " + switchUrl);

        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<List> response = restTemplate.getForEntity(switchUrl, List.class);

            System.out.println("✅ [Diagnostic] Success! Status: " + response.getStatusCode());
            System.out.println("📦 [Diagnostic] Response Body: " + response.getBody());
        } catch (Exception e) {
            System.err.println("❌ [Diagnostic] Failed to reach Switch.");
            System.err.println("⚠️ [Diagnostic] Error Message: " + e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("trustAnchors")) {
                System.err.println(
                        "💡 [Diagnostic] SSL/mTLS Trust issue detected. You need the certificate in your local truststore.");
            }
        }
    }
}
