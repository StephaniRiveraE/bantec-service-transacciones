package com.arcbank.cbs.transaccion.controller;

import com.arcbank.cbs.transaccion.dto.RefoundRequestDTO;
import com.arcbank.cbs.transaccion.dto.TransaccionRequestDTO;
import com.arcbank.cbs.transaccion.dto.TransaccionResponseDTO;
import com.arcbank.cbs.transaccion.service.TransaccionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/transacciones")
@RequiredArgsConstructor
@Tag(name = "Transacciones", description = "Gestión de movimientos y cumplimiento de lógica financiera")
public class Controller {

    private final TransaccionService transaccionService;

    @PostMapping
    @Operation(summary = "Ejecutar transacción financiera")
    public ResponseEntity<TransaccionResponseDTO> crear(@Valid @RequestBody TransaccionRequestDTO request) {
        return new ResponseEntity<>(transaccionService.crearTransaccion(request), HttpStatus.CREATED);
    }

    @GetMapping("/cuenta/{idCuenta}")
    @Operation(summary = "Historial por cuenta (Origen o Destino)")
    public ResponseEntity<List<TransaccionResponseDTO>> listarPorCuenta(@PathVariable Integer idCuenta) {
        return ResponseEntity.ok(transaccionService.obtenerPorCuenta(idCuenta));
    }

    @PostMapping("/reverso")
    @Operation(summary = "Solicitar reverso de transacción")
    public ResponseEntity<Object> solicitarReverso(@Valid @RequestBody RefoundRequestDTO request) {
        transaccionService.solicitarReverso(request);
        return ResponseEntity.ok(java.util.Map.of("message", "Solicitud de reverso procesada exitosamente"));
    }

    @GetMapping("/buscar/{referencia}")
    @Operation(summary = "Buscar transacción por referencia")
    public ResponseEntity<TransaccionResponseDTO> buscarPorReferencia(@PathVariable String referencia) {
        return ResponseEntity.ok(transaccionService.buscarPorReferencia(referencia));
    }

    @GetMapping("/motivos-devolucion")
    @Operation(summary = "Listar motivos de devolución (desde Switch)")
    public ResponseEntity<List<java.util.Map<String, String>>> obtenerMotivosDevolucion() {
        return ResponseEntity.ok(transaccionService.obtenerMotivosDevolucion());
    }

    @PostMapping("/validar-externa")
    @Operation(summary = "Validar cuenta externa en otro banco")
    public ResponseEntity<com.arcbank.cbs.transaccion.dto.AccountLookupResponse> validarCuentaExterna(
            @RequestBody java.util.Map<String, String> request) {

        String targetBankId = request.get("targetBankId");
        String targetAccountNumber = request.get("targetAccountNumber");

        return ResponseEntity.ok(transaccionService.validarCuentaExterna(targetBankId, targetAccountNumber));
    }
}