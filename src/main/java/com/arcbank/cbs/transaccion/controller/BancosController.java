package com.arcbank.cbs.transaccion.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arcbank.cbs.transaccion.client.SwitchClient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/bancos")
@RequiredArgsConstructor
@Tag(name = "Bancos", description = "Consulta de bancos conectados al switch interbancario")
public class BancosController {

    private final SwitchClient switchClient;

    private static final List<Map<String, Object>> BANCOS_DISPONIBLES = List.of(
            Map.of("id", "ARCBANK", "nombre", "Banco Arcbank", "codigo", "ARCBANK"),
            Map.of("id", "NEXUS_BANK", "nombre", "Nexus Bank", "codigo", "NEXUS_BANK"),
            Map.of("id", "ECUSOL_BK", "nombre", "Ecusol Bank", "codigo", "ECUSOL_BK"));

    @GetMapping
    @Operation(summary = "Listar bancos disponibles para transferencias interbancarias")
    public ResponseEntity<?> listarBancos() {
        try {
            log.info("[BANTEC] Consultando lista de bancos al Switch via APIM");
            List<Map<String, Object>> bancos = switchClient.obtenerBancos();
            return ResponseEntity.ok(Map.of(
                    "bancos", bancos,
                    "total", bancos.size()));
        } catch (Exception e) {
            log.error("[BANTEC] Error consultando bancos: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "bancos", BANCOS_DISPONIBLES,
                    "total", BANCOS_DISPONIBLES.size(),
                    "note", "Lista de respaldo por fallo en Switch"));
        }
    }

    @GetMapping("/health")
    @Operation(summary = "Verificar conexión con el switch interbancario")
    public ResponseEntity<?> healthCheck() {
        try {
            Map<String, String> health = switchClient.healthCheck();
            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "switch", health));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "status", "DOWN",
                    "error", e.getMessage()));
        }
    }
}
