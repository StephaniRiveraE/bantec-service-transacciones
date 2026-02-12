package com.arcbank.cbs.transaccion.controller;

import com.arcbank.cbs.transaccion.service.TransaccionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Controller con los endpoints estándar requeridos por el Switch
 * para recibir transferencias interbancarias y reversiones.
 */
@Slf4j
@RestController
@RequestMapping("/api/core/transferencias")
@RequiredArgsConstructor
@Tag(name = "Core Transferencias", description = "Endpoints estándar para integración con el Switch interbancario")
public class CoreTransferenciaController {

    private final TransaccionService transaccionService;

    /**
     * Recibir dinero de otro banco (transferencia entrante).
     * El Switch envía este request cuando otro banco inicia una transferencia
     * hacia una cuenta de Bantec.
     */
    @PostMapping("/recepcion")
    @Operation(summary = "Recibir transferencia interbancaria entrante")
    public ResponseEntity<?> recibirTransferencia(@RequestBody Map<String, Object> payload) {
        log.info("📥 Recepción de transferencia interbancaria entrante");

        try {
            String instructionId = (String) payload.get("instructionId");
            String cuentaDestino = (String) payload.get("cuentaDestino");
            String bancoOrigen = (String) payload.get("bancoOrigen");
            BigDecimal monto = new BigDecimal(payload.get("monto").toString());

            transaccionService.procesarTransferenciaEntrante(
                    instructionId, cuentaDestino, monto, bancoOrigen);

            return ResponseEntity.ok(Map.of(
                    "status", "ACK",
                    "message", "Transferencia recibida exitosamente",
                    "instructionId", instructionId));
        } catch (Exception e) {
            log.error("❌ Error procesando recepción: {}", e.getMessage());
            return ResponseEntity.status(422).body(Map.of(
                    "status", "NACK",
                    "error", e.getMessage()));
        }
    }

    /**
     * Revertir una transacción fallida.
     * El Switch envía este request cuando necesita deshacer una transferencia
     * previamente procesada.
     */
    @PostMapping("/reversion")
    @Operation(summary = "Revertir transacción interbancaria fallida")
    public ResponseEntity<?> revertirTransferencia(@RequestBody Map<String, Object> payload) {
        log.info("🔄 Solicitud de reversión de transferencia interbancaria");

        try {
            String instructionId = (String) payload.get("instructionId");
            String motivo = (String) payload.getOrDefault("motivo", "Reversión solicitada por Switch");

            // Buscar la transacción original por instructionId y revertirla
            com.arcbank.cbs.transaccion.dto.RefoundRequestDTO refundRequest = new com.arcbank.cbs.transaccion.dto.RefoundRequestDTO();
            refundRequest.setCodigoReferencia(instructionId);
            refundRequest.setMotivo(motivo);

            transaccionService.solicitarReverso(refundRequest);

            return ResponseEntity.ok(Map.of(
                    "status", "ACK",
                    "message", "Reversión procesada exitosamente",
                    "instructionId", instructionId));
        } catch (Exception e) {
            log.error("❌ Error procesando reversión: {}", e.getMessage());
            return ResponseEntity.status(422).body(Map.of(
                    "status", "NACK",
                    "error", e.getMessage()));
        }
    }
}
