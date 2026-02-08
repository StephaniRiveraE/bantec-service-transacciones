package com.arcbank.cbs.transaccion.service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class JwsService {

    @Value("classpath:certs/jws_private.pem")
    private Resource privateKeyResource;

    @Value("classpath:certs/switch_public_key.pem")
    private Resource publicKeyResource;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @jakarta.annotation.PostConstruct
    public void init() {
        try {
            this.privateKey = loadPrivateKey();
            this.publicKey = loadPublicKey();
            log.info("✅ keys loaded successfully for JWS (RS256).");
        } catch (Exception e) {
            log.warn("⚠️ Could not load JWS keys: {}. JWS signing/verification will fail.", e.getMessage());
        }
    }

    public String sign(String payload) {
        if (privateKey == null)
            throw new IllegalStateException("Private key not loaded");

        // Create a detached JWS (compact serialization)
        // We put the payload in the body, sign it, but in many bank implementations
        // they want the "Detached" signature which is usually:
        // JWS = Base64(Header) + "." + Base64(Payload) + "." + Base64(Signature)
        // A "Detached" signature referenced in headers usually implies sending JUST the
        // signature part
        // or the flattened structure.
        // FOR THIS IMPLEMENTATION: We will generate a standard JWS Compact string.
        // If the requirement is "Header with Signature", we usually send the full JWS
        // string.

        return Jwts.builder()
                .content(payload)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public boolean verify(String jws) {
        if (publicKey == null)
            throw new IllegalStateException("Public key not loaded");
        try {
            Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedContent(jws);
            return true;
        } catch (Exception e) {
            log.error("❌ JWS Verification failed: {}", e.getMessage());
            return false;
        }
    }

    // Helper to verify payload against a specific signature header (Detached mode
    // simulation)
    // Many APIs send: Header: x-jws-signature: <base64> and Body: <json>
    // This requires re-constructing the JWS to verify.
    // For simplicity, we'll assume we are exchanging FULL JWS TOKENS or valid
    // compact JWS.
    // If the requirement is strict "HTTP Body + Detached Header", implementation
    // changes.
    // Let's implement a standard "Verify content matches signature" if needed.

    private PrivateKey loadPrivateKey() throws Exception {
        String key = readResource(privateKeyResource)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(key);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private PublicKey loadPublicKey() throws Exception {
        String key = readResource(publicKeyResource)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(key);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private String readResource(Resource resource) throws Exception {
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
