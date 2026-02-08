package com.arcbank.cbs.transaccion.config;

import com.arcbank.cbs.transaccion.service.JwsService;
import com.arcbank.cbs.transaccion.service.TokenService;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SecurityInterceptor {

    private final TokenService tokenService;
    private final JwsService jwsService;

    @Bean
    public RequestInterceptor oauthAndJwsInterceptor() {
        return template -> {
            // 1. Inject OAuth Token
            String token = tokenService.getAccessToken();
            template.header("Authorization", "Bearer " + token);

            // 2. Sign Body (JWS)
            if (template.body() != null) {
                String body = new String(template.body(), StandardCharsets.UTF_8);
                try {
                    String jwsSignature = jwsService.sign(body);
                    template.header("X-JWS-Signature", jwsSignature);
                    log.debug("✍️ Request Signed for {}", template.url());
                } catch (Exception e) {
                    log.error("❌ Failed to sign request: {}", e.getMessage());
                    throw new RuntimeException("Signing failed", e);
                }
            }
        };
    }
}
