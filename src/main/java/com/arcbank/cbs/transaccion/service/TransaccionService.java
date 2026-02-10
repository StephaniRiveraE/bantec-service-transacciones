package com.arcbank.cbs.transaccion.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.arcbank.cbs.transaccion.dto.TransaccionRequestDTO;
import com.arcbank.cbs.transaccion.dto.TransaccionResponseDTO;
import com.arcbank.cbs.transaccion.dto.RefoundRequestDTO;
import com.arcbank.cbs.transaccion.dto.AccountLookupResponse;

public interface TransaccionService {

        TransaccionResponseDTO crearTransaccion(TransaccionRequestDTO request);

        TransaccionResponseDTO obtenerPorId(Integer id);

        TransaccionResponseDTO buscarPorReferencia(String referencia);

        List<TransaccionResponseDTO> obtenerPorCuenta(Integer cuentaId);

        void solicitarReverso(RefoundRequestDTO request);

        String consultarEstadoTransferencia(String instructionId);

        AccountLookupResponse validarCuentaExterna(String bankBic, String accountId);

        AccountLookupResponse validarCuentaLocal(String accountId);

        void procesarDeposito(String cuentaDestino, BigDecimal monto, String ordenante, String instructionId);

        List<Map<String, String>> obtenerMotivosDevolucion();

        void procesarTransferenciaEntrante(String instructionId, String cuentaDestino, BigDecimal monto,
                        String bancoOrigen);

        void procesarDevolucionEntrante(com.arcbank.cbs.transaccion.dto.SwitchRefundRequest request);
}