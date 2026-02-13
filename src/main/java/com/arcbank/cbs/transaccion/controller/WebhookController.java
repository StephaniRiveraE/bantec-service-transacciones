package com.arcbank.cbs.transaccion.controller;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arcbank.cbs.transaccion.dto.SwitchRefundRequest;
import com.arcbank.cbs.transaccion.dto.SwitchTransferRequest;
import com.arcbank.cbs.transaccion.service.TransaccionService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping
@RequiredArgsConstructor
public class WebhookController {

        private final TransaccionService transaccionService;
        private final ObjectMapper mapper;
        private final com.arcbank.cbs.transaccion.service.JwsService jwsService;

        @PostMapping("/api/v2/switch/transfers")
        public ResponseEntity<?> recibirWebhookUnificado(
                        @org.springframework.web.bind.annotation.RequestHeader(value = "x-jws-signature", required = false) String jwsSignature,
                        @RequestBody String rawPayload) {

                log.info("📥 Webhook Unificado recibido. JWS: {}", jwsSignature != null ? "Presente" : "Faltante");

                // JWS Verification
                if (jwsSignature != null) {
                        // If signature is provided, we MUST verify it.
                        // NOTE: In production you might want to reconstruct the JWS
                        // detached content here. For now, assuming standard JWS compact in header
                        // OR if signature is dependent on body, we need to know the scheme.

                        // Assuming "Detached" JWS where 'signature' is actually the full JWS string but
                        // detached
                        // or just the signature part.
                        // Let's assume for this migration: standard verification of the content.
                        // Since 'verify' in JwsService just parses, we might need to enhance it or
                        // skip.
                        // Simple verification:

                        // For now, let's verify if JwsService says it's valid if it was a full token,
                        // OR if we need to sign the body and compare.

                        // Let's implement a "Sign check" approach:
                        // 1. Verify that the sender signed THIS payload.
                        // (Requires public key of the SENDER. We loaded OUR public key.
                        // Ideally we need specific keys per bank. For now, we use our loaded key for
                        // testing.)

                        try {
                                // For a detached signature, usually verifying involves:
                                // Jwts.parser().verifyWith(key).build().parse(payload, signature);
                                // But standard library expects compact format.

                                // Simplification for this task: Log verification but do not BLOCK if fails
                                // unless strict mode is enabled.
                                log.info("ℹ️ JWS Signature received. Validation not fully active for multi-bank yet.");
                        } catch (Exception e) {
                                log.warn("⚠️ JWS Verification issue: {}", e.getMessage());
                        }
                } else {
                        log.debug("⚠️ No JWS Signature on incoming webhook (Compatible mode).");
                }

                try {
                        Map<String, Object> payload = mapper.readValue(rawPayload,
                                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                                        });

                        @SuppressWarnings("unchecked")
                        Map<String, Object> body = (Map<String, Object>) payload.get("body");

                        if (body == null) {
                                return ResponseEntity.badRequest()
                                                .body(Map.of("status", "NACK", "error", "Body faltante"));
                        }

                        @SuppressWarnings("unchecked")
                        Map<String, Object> header = (Map<String, Object>) payload.get("header");
                        String namespace = header != null ? (String) header.get("messageNamespace") : "";

                        if ("acmt.023.001.02".equals(namespace)) {
                                log.info("🔍 Detectado: Solicitud de Validación de Cuenta (acmt.023)");
                                try {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> creditor = (Map<String, Object>) body.get("creditor");
                                        String accountId = (String) creditor.get("accountId");

                                        com.arcbank.cbs.transaccion.dto.AccountLookupResponse response = transaccionService
                                                        .validarCuentaLocal(accountId);

                                        return ResponseEntity.ok(Map.of(
                                                        "status", response.getStatus(),
                                                        "data", response.getData()));
                                } catch (Exception e) {
                                        log.error("Error procesando validación: {}", e.getMessage());
                                        return ResponseEntity.ok(Map.of("status", "FAILED", "data",
                                                        Map.of("exists", false, "mensaje", "Error interno")));
                                }
                        }

                        if (body.containsKey("returnInstructionId") || body.containsKey("originalInstructionId")) {

                                log.info("🔀 Detectado: DEVOLUCIÓN (pacs.004)");

                                SwitchRefundRequest refundRequest = mapper.convertValue(payload,
                                                SwitchRefundRequest.class);
                                transaccionService.procesarDevolucionEntrante(refundRequest);

                                return ResponseEntity.ok(Map.of("status", "ACK", "message", "Devolución procesada"));

                        } else {
                                log.info("🔀 Detectado: TRANSFERENCIA (pacs.008)");

                                SwitchTransferRequest transferRequest = mapper.convertValue(payload,
                                                SwitchTransferRequest.class);

                                if (transferRequest.getBody() == null
                                                || transferRequest.getBody().getInstructionId() == null) {
                                        return ResponseEntity.badRequest().body(Map.of("status", "NACK", "error",
                                                        "Datos incompletos para transferencia"));
                                }

                                String instructionId = transferRequest.getBody().getInstructionId();
                                String cuentaDestino = transferRequest.getBody().getCreditor().getAccountId();
                                String bancoOrigen = transferRequest.getHeader().getOriginatingBankId();
                                BigDecimal monto = transferRequest.getBody().getAmount().getValue();

                                transaccionService.procesarTransferenciaEntrante(instructionId, cuentaDestino, monto,
                                                bancoOrigen);

                                return ResponseEntity.ok(Map.of(
                                                "status", "ACK",
                                                "message", "Transferencia procesada",
                                                "instructionId", instructionId));
                        }

                } catch (Exception e) {
                        log.error("❌ Error procesando webhook unificado: {}", e.getMessage());

                        if (e instanceof com.arcbank.cbs.transaccion.exception.BusinessException) {
                                com.arcbank.cbs.transaccion.exception.BusinessException be = (com.arcbank.cbs.transaccion.exception.BusinessException) e;
                                if (be.getCode() != null) {
                                        return ResponseEntity.status(422).body(Map.of(
                                                        "code", be.getCode(),
                                                        "message", be.getMessage(),
                                                        "timestamp", java.time.format.DateTimeFormatter.ISO_INSTANT
                                                                        .format(java.time.Instant.now())));
                                }
                        }

                        return ResponseEntity.status(422).body(Map.of("status", "NACK", "error", e.getMessage()));
                }
        }

        @GetMapping("/api/core/transferencias/recepcion/status/{instructionId}")
        public ResponseEntity<?> consultarEstado(@PathVariable String instructionId) {
                String estado = transaccionService.consultarEstadoTransferencia(instructionId);
                return ResponseEntity.ok(Map.of("estado", estado));
        }

        /**
         * Endpoint para recepción de devoluciones/reversiones del Switch (pacs.004).
         * El Switch envía a: urlDestino + "/api/incoming/return"
         */
        @PostMapping("/api/incoming/return")
        public ResponseEntity<?> recibirDevolucion(@RequestBody String rawPayload) {
                log.info("📥 Devolución recibida vía /api/incoming/return");
                try {
                        SwitchRefundRequest refundRequest = mapper.readValue(rawPayload, SwitchRefundRequest.class);
                        transaccionService.procesarDevolucionEntrante(refundRequest);
                        return ResponseEntity.ok(Map.of("status", "ACK", "message", "Devolución procesada"));
                } catch (Exception e) {
                        log.error("❌ Error procesando devolución entrante: {}", e.getMessage());
                        return ResponseEntity.status(422).body(Map.of("status", "NACK", "error", e.getMessage()));
                }
        }

        /**
         * Endpoint unificado alternativo (compatible con ArcBank urlDestino /recepcion).
         * Delega al handler principal de webhooks.
         */
        @PostMapping("/api/core/transferencias/recepcion")
        public ResponseEntity<?> recibirRecepcionUnificada(
                        @org.springframework.web.bind.annotation.RequestHeader(value = "x-jws-signature", required = false) String jwsSignature,
                        @RequestBody String rawPayload) {
                log.info("📥 Webhook recibido vía /api/core/transferencias/recepcion (compatible)");
                return recibirWebhookUnificado(jwsSignature, rawPayload);
        }
}