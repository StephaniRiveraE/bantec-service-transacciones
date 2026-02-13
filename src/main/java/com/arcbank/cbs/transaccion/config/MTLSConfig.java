package com.arcbank.cbs.transaccion.config;

import feign.Client;
import feign.RequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.security.KeyStore;

import org.apache.hc.client5.http.io.HttpClientConnectionManager;

@Configuration
@Slf4j
public class MTLSConfig {

    @Value("${app.mtls.keystore.path:classpath:certs/bantec-keystore.p12}")
    private Resource keystoreResource;

    @Value("${app.mtls.keystore.password:bantec123}")
    private String keystorePassword;

    @Value("${app.mtls.truststore.path:classpath:certs/bantec-truststore.p12}")
    private Resource truststoreResource;

    @Value("${app.mtls.truststore.password:bantec123}")
    private String truststorePassword;

    @Value("${app.mtls.enabled:false}")
    private boolean mtlsEnabled;

    @Value("${app.switch.apikey:}")
    private String apiKey;

    @org.springframework.beans.factory.annotation.Autowired
    private com.arcbank.cbs.transaccion.service.JwsService jwsService;

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            // 1. Add API Key
            if (apiKey != null && !apiKey.isBlank()) {
                requestTemplate.header("apikey", apiKey);
            }

            // 2. Add JWS Signature (Detached / Header based)
            // If body exists, sign it. If GET, maybe sign empty string or URL?
            // Standard practice: Sign the body if present.
            if (requestTemplate.body() != null) {
                String body = new String(requestTemplate.body(), java.nio.charset.StandardCharsets.UTF_8);
                try {
                    String signature = jwsService.sign(body);
                    requestTemplate.header("x-jws-signature", signature);
                    log.debug("📝 Signed request body. Sig: {}", signature.substring(0, 10) + "...");
                } catch (Exception e) {
                    log.error("❌ Failed to sign request: {}", e.getMessage());
                }
            }
        };
    }

    @Bean
    public Client feignClient() throws Exception {
        if (!mtlsEnabled) {
            log.info("mTLS desactivado para el cliente Feign.");
            return new Client.Default(null, null);
        }

        if (!keystoreResource.exists()) {
            log.error("❌ CRÍTICO: Keystore NO encontrado en {}. La conexión con el Switch fallará si requiere mTLS.",
                    keystoreResource);
            return new Client.Default(null, null);
        }

        log.info("Cargando Keystore desde: {}", keystoreResource);
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream keyStoreStream = keystoreResource.getInputStream()) {
            keyStore.load(keyStoreStream, keystorePassword.toCharArray());
        }

        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        boolean trustStoreLoaded = false;
        if (truststoreResource.exists()) {
            try (InputStream trustStoreStream = truststoreResource.getInputStream()) {
                trustStore.load(trustStoreStream, truststorePassword.toCharArray());
                if (trustStore.size() > 0) {
                    trustStoreLoaded = true;
                    log.info("✅ Truststore cargado exitosamente desde {} ({} certificados)",
                            truststoreResource, trustStore.size());
                } else {
                    log.warn("⚠️ Truststore personalizado está VACÍO. Se usará el del sistema.");
                }
            } catch (Exception e) {
                log.warn("⚠️ Error cargando truststore personalizado: {}. Se usará el del sistema.", e.getMessage());
            }
        } else {
            log.warn("ℹ️ Truststore personalizado no encontrado en {}. Se usará el del sistema.", truststoreResource);
        }

        SSLContextBuilder sslContextBuilder = SSLContextBuilder.create()
                .loadKeyMaterial(keyStore, keystorePassword.toCharArray());

        if (trustStoreLoaded) {
            // Cargar el truststore personalizado pero PERMITIR cualquier certificado (para
            // aceptar AWS APIM y otros)
            sslContextBuilder.loadTrustMaterial(trustStore, (chain, authType) -> true);
            log.warn("⚠️ Truststore cargado pero se ha forzado TRUST ALL para compatibilidad con AWS.");
        } else {
            // Si no hay truststore, confiar en todo
            sslContextBuilder.loadTrustMaterial(null, (chain, authType) -> true);
            log.warn("⚠️ MODO_INSECURO: Se ha desactivado la verificación SSL para avanzar en pruebas.");
        }

        SSLContext sslContext = sslContextBuilder.build();
        SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext,
                org.apache.hc.client5.http.ssl.NoopHostnameVerifier.INSTANCE);

        HttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(sslSocketFactory)
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();

        log.info("🚀 Cliente Feign configurado (SSL Verification: DISABLED).");
        return new feign.hc5.ApacheHttp5Client(httpClient);
    }
}
