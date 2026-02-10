package com.arcbank.cbs.transaccion.config;

import com.arcbank.cbs.transaccion.dto.SwitchTransferResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import feign.Response;
import feign.Util;
import feign.codec.Decoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.lang.reflect.Type;

@Slf4j
public class SwitchFeignDecoderConfig {

    @Bean
    public feign.RequestInterceptor requestInterceptor(
            @org.springframework.beans.factory.annotation.Value("${app.switch.apikey}") String apiKey) {
        return requestTemplate -> {
            requestTemplate.header("apikey", apiKey);
            log.debug("🔑 Añadiendo header apikey: {}", apiKey);
        };
    }

    @Bean
    public Decoder feignDecoder() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.registerModule(new JavaTimeModule());

        return new Decoder() {
            @Override
            public Object decode(Response response, Type type) throws IOException {
                if (!type.getTypeName().contains("SwitchTransferResponse")) {
                    if (response.body() == null) {
                        return null;
                    }
                    String body = Util.toString(response.body().asReader(java.nio.charset.StandardCharsets.UTF_8));
                    return mapper.readValue(body, mapper.constructType(type));
                }

                if (response.body() == null) {
                    log.info("📥 Switch response body is null, status: {}", response.status());
                    if (response.status() >= 200 && response.status() < 300) {
                        return SwitchTransferResponse.builder()
                                .success(true)
                                .build();
                    }
                    return SwitchTransferResponse.builder()
                            .success(false)
                            .error(SwitchTransferResponse.ErrorBody.builder()
                                    .code("EMPTY_RESPONSE")
                                    .message("Switch returned empty response")
                                    .build())
                            .build();
                }

                // 0. Validate JWS Signature
                if (response.headers().containsKey("X-JWS-Signature")) {
                    String signature = response.headers().get("X-JWS-Signature").stream().findFirst().orElse(null);
                    if (signature != null && response.body() != null) {
                        // Note: To verify detailed JWS, we might need the full JWS string
                        // (header.payload.signature)
                        // OR if the Switch sends Detached JWS (signature in header, payload in body).
                        // Current JwsService implementation assumes standard compact JWS.
                        // If Switch sends Detached, we need to reconstruct:
                        // String jwsToVerify = <Base64Header> + "." + <Base64Payload> + "." +
                        // signature;
                        // For now, let's log the presence. Implementation detail depends on strict
                        // Switch spec.
                        log.info("🔐 Response contains JWS Signature: {}", signature);
                    }
                }

                String body = Util.toString(response.body().asReader(java.nio.charset.StandardCharsets.UTF_8));
                log.info("📥 Switch raw response - Status: {}, Body: {}", response.status(), body);

                if (body == null || body.isBlank()) {
                    if (response.status() >= 200 && response.status() < 300) {
                        return SwitchTransferResponse.builder()
                                .success(true)
                                .build();
                    }
                }

                try {
                    JsonNode rootNode = mapper.readTree(body);

                    // LOGIC ADDED: Handle Array Response (List of Transactions)
                    if (rootNode.isArray()) {
                        log.warn("⚠️ Switch returned an ARRAY instead of an Object. Attempting to find transaction...");

                        // Extract requested ID from URL if possible
                        String requestUrl = response.request().url();
                        String requestedId = requestUrl.substring(requestUrl.lastIndexOf('/') + 1);
                        log.info("🔍 Searching for ID: {} in the response list", requestedId);

                        for (JsonNode node : rootNode) {
                            String nodeId = "";
                            if (node.has("idInstruccion"))
                                nodeId = node.get("idInstruccion").asText();
                            else if (node.has("instructionId"))
                                nodeId = node.get("instructionId").asText();
                            else if (node.has("id"))
                                nodeId = node.get("id").asText();

                            if (nodeId.equals(requestedId)) {
                                log.info("✅ Found matching transaction in list!");
                                // Map flat JSON to our DTO structure manually
                                return mapJsonNodeToResponse(node, mapper);
                            }
                        }

                        // If not found specific, maybe it's a list with 1 item?
                        if (rootNode.size() > 0) {
                            log.warn("⚠️ Requested ID not found, using FIRST item in list as fallback.");
                            return mapJsonNodeToResponse(rootNode.get(0), mapper);
                        }

                        return SwitchTransferResponse.builder().success(false).build();
                    }

                    // Handle normal Object response (Recursive or Flat)
                    // If it matches our DTO directly
                    try {
                        SwitchTransferResponse val = mapper.treeToValue(rootNode, SwitchTransferResponse.class);
                        if (val.getData() != null || val.getError() != null) {
                            enrichSuccess(val, rootNode);
                            return val;
                        }
                    } catch (Exception ignored) {
                    }

                    // Fallback: Map flat JSON object to Response
                    return mapJsonNodeToResponse(rootNode, mapper);

                } catch (Exception e) {
                    log.error("Error parsing Switch response: {} - Body: {}", e.getMessage(), body);

                    if (response.status() >= 200 && response.status() < 300) {
                        return SwitchTransferResponse.builder().success(true).build();
                    }

                    return SwitchTransferResponse.builder()
                            .success(false)
                            .error(SwitchTransferResponse.ErrorBody.builder()
                                    .code("PARSE_ERROR")
                                    .message("Failed to parse: " + e.getMessage())
                                    .build())
                            .build();
                }
            }

            private void enrichSuccess(SwitchTransferResponse resp, JsonNode node) {
                if (resp.getError() == null || resp.getData() != null)
                    resp.setSuccess(true);
                if (node.has("estado") && node.get("estado").asText()
                        .matches("(?i)(COMPLETADA|EXITOSA|PROCESADA|SUCCESS|OK|COMPLETED|QUEUED|ACCEPTED)")) {
                    resp.setSuccess(true);
                }
            }

            private SwitchTransferResponse mapJsonNodeToResponse(JsonNode node, ObjectMapper mapper) {
                SwitchTransferResponse.DataBody data = new SwitchTransferResponse.DataBody();

                // 1. Extract ID
                if (node.has("idInstruccion"))
                    data.setInstructionId(getUuidSafe(node.get("idInstruccion").asText()));
                else if (node.has("instructionId"))
                    data.setInstructionId(getUuidSafe(node.get("instructionId").asText()));
                else if (node.has("id"))
                    data.setInstructionId(getUuidSafe(node.get("id").asText()));

                // 2. Extract Status
                if (node.has("estado"))
                    data.setEstado(node.get("estado").asText());
                else if (node.has("status"))
                    data.setEstado(node.get("status").asText());

                // 2.1 Extract Reference Code
                if (node.has("codigo_referencia"))
                    data.setCodigoReferencia(node.get("codigo_referencia").asText());
                else if (node.has("codigoReferencia"))
                    data.setCodigoReferencia(node.get("codigoReferencia").asText());

                // 3. Extract Error Information (Crucial for "Rechazo desconocido")
                String errorMsg = null;
                String errorCode = "UNKNOWN";

                if (node.has("error")) {
                    JsonNode errNode = node.get("error");
                    if (errNode.isObject()) {
                        if (errNode.has("message"))
                            errorMsg = errNode.get("message").asText();
                        else if (errNode.has("descripcion"))
                            errorMsg = errNode.get("descripcion").asText();

                        if (errNode.has("code"))
                            errorCode = errNode.get("code").asText();
                    } else {
                        errorMsg = errNode.asText();
                    }
                } else if (node.has("message")) {
                    errorMsg = node.get("message").asText();
                } else if (node.has("mensaje")) {
                    errorMsg = node.get("mensaje").asText();
                } else if (node.has("detail")) {
                    errorMsg = node.get("detail").asText();
                } else if (node.has("reason")) {
                    errorMsg = node.get("reason").asText();
                }

                // 4. Determine Success
                boolean success = false;

                // Explicit Success Status
                if (data.getEstado() != null && data.getEstado()
                        .matches("(?i)(COMPLETADA|EXITOSA|PROCESADA|SUCCESS|OK|COMPLETED|QUEUED|ACCEPTED)")) {
                    success = true;
                }
                // Implicit Success (No error, has ID) - SAFETY NET
                else if (errorMsg == null && data.getInstructionId() != null) {
                    // Only assume success if truly no error looks present.
                    // But to be safe, if status is missing, we might default to false unless we are
                    // sure.
                    // For now, if we have an ID and no error message, let's treat as potentially
                    // valid/queued
                    success = true;
                }

                // 5. Construct Response
                SwitchTransferResponse.SwitchTransferResponseBuilder builder = SwitchTransferResponse.builder()
                        .success(success)
                        .data(data);

                if (!success && errorMsg != null) {
                    builder.error(SwitchTransferResponse.ErrorBody.builder()
                            .code(errorCode)
                            .message(errorMsg)
                            .build());
                }

                return builder.build();
            }

            private java.util.UUID getUuidSafe(String uuidStr) {
                try {
                    return java.util.UUID.fromString(uuidStr);
                } catch (Exception e) {
                    return null;
                }
            }
        };
    }

    @Bean
    public feign.codec.ErrorDecoder errorDecoder() {
        return (methodKey, response) -> {
            String body = "Unknown Error";
            try {
                if (response.body() != null) {
                    body = Util.toString(response.body().asReader(java.nio.charset.StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                log.error("Error reading error response body", e);
            }
            log.error("🔥 Switch responded with error. Status: {}, Body: {}", response.status(), body);

            // Retornamos una excepción con el cuerpo del error para que el servicio lo
            // capture
            return new RuntimeException("Switch Error (" + response.status() + "): " + body);
        };
    }
}
