package com.arcbank.cbs.transaccion.controller;

import com.arcbank.cbs.transaccion.client.SwitchClient;
import com.arcbank.cbs.transaccion.dto.SwitchTransferResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v2/switch")
@RequiredArgsConstructor
@Tag(name = "Switch Proxy", description = "Proxy seguro para consultas al Switch")
public class SwitchProxyController {

    private final SwitchClient switchClient;

    @GetMapping("/transfers/{instructionId}")
    @Operation(summary = "Consultar estado de transferencia en Switch")
    public ResponseEntity<SwitchTransferResponse> consultarEstadoTransferencia(@PathVariable String instructionId) {
        // Delegamos la consulta al Feign Client que ya tiene mTLS configurado
        SwitchTransferResponse response = switchClient.consultarEstadoTransferencia(instructionId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/account-lookup")
    @Operation(summary = "Validar cuenta externa en Switch (Proxy)")
    public ResponseEntity<com.arcbank.cbs.transaccion.dto.AccountLookupResponse> lookupAccount(
            @RequestBody com.arcbank.cbs.transaccion.dto.AccountLookupRequest request) {
        return ResponseEntity.ok(switchClient.lookupAccount(request));
    }
}
