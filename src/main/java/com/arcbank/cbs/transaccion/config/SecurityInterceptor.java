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
            // 0. Add Trace-ID for Observability
            template.header("X-Trace-Id", java.util.UUID.randomUUID().toString());

            try {
                // 1. Inject OAuth Token
                String token = tokenService.getAccessToken();
                template.header("Authorization", "Bearer " + token);
            } catch (Exception e) {
                log.warn("⚠️ No se pudo obtener el token OAuth para {}: {}", template.url(), e.getMessage());
                // No relanzamos para permitir llamadas internas o fallos temporales de Cognito
            }

            // 2. Sign Body (JWS)
            if (template.body() != null) {
                String body = new String(template.body(), StandardCharsets.UTF_8);
                try {
                    String jwsSignature = jwsService.sign(body);
                    template.header("X-JWS-Signature", jwsSignature);
                    log.debug("✍️ Request Signed for {}", template.url());
                } catch (Exception e) {
                    log.error("❌ Failed to sign request: {}", e.getMessage());
                    // No relanzamos para evitar que una falla en firma bloquee la operación
                }
            }
        };
    }
}
