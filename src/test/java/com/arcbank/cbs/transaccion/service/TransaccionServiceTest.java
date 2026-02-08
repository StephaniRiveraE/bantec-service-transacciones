package com.arcbank.cbs.transaccion.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.arcbank.cbs.transaccion.client.CuentaCliente;
import com.arcbank.cbs.transaccion.dto.SwitchRefundRequest;
import com.arcbank.cbs.transaccion.dto.SaldoDTO;
import com.arcbank.cbs.transaccion.model.Transaccion;
import com.arcbank.cbs.transaccion.repository.TransaccionRepository;

@ExtendWith(MockitoExtension.class)
public class TransaccionServiceTest {

    @Mock
    private TransaccionRepository transaccionRepository;

    @Mock
    private CuentaCliente cuentaCliente;

    @InjectMocks
    private TransaccionServiceImpl transaccionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(transaccionService, "codigoBanco", "BANTEC");
    }

    @Test
    void procesarDevolucionEntrante_Success_Credit() {
        // Arrange
        String originalRef = "ORIG-123";
        String returnRef = "RET-999";
        String bankId = "BANK-002";

        SwitchRefundRequest request = SwitchRefundRequest.builder()
                .header(SwitchRefundRequest.Header.builder()
                        .originatingBankId(bankId)
                        .creationDateTime("2026-01-22T10:00:00Z")
                        .build())
                .body(SwitchRefundRequest.Body.builder()
                        .originalInstructionId(originalRef)
                        .returnInstructionId(returnRef)
                        .returnReason("AC01")
                        .returnAmount(SwitchRefundRequest.Amount.builder().value(new BigDecimal("100.00")).build())
                        .build())
                .build();

        Transaccion originalTx = Transaccion.builder()
                .idTransaccion(1)
                .referencia(originalRef)
                .tipoOperacion("TRANSFERENCIA_SALIDA")
                .idCuentaOrigen(101)
                .estado("COMPLETADA")
                .descripcion("Transferencia salida")
                .build();

        when(transaccionRepository.findByReferencia(originalRef)).thenReturn(Optional.of(originalTx));
        when(cuentaCliente.obtenerSaldo(101)).thenReturn(new BigDecimal("500.00"));

        // Act
        transaccionService.procesarDevolucionEntrante(request);

        // Assert
        // Verify balance update (Credit)
        verify(cuentaCliente).actualizarSaldo(any(Integer.class), any(SaldoDTO.class));

        // Verify original tx updated
        verify(transaccionRepository).save(originalTx);

        // Verify new return tx saved
        verify(transaccionRepository, times(2)).save(any(Transaccion.class));
    }

    @Test
    void procesarDevolucionEntrante_Success_Debit() {
        // Arrange
        String originalRef = "ORIG-456";
        String returnRef = "RET-888";

        SwitchRefundRequest request = SwitchRefundRequest.builder()
                .header(SwitchRefundRequest.Header.builder()
                        .originatingBankId("BANK-003")
                        .creationDateTime("2026-01-22T12:00:00Z")
                        .build())
                .body(SwitchRefundRequest.Body.builder()
                        .originalInstructionId(originalRef)
                        .returnInstructionId(returnRef)
                        .returnReason("AM04")
                        .returnAmount(SwitchRefundRequest.Amount.builder().value(new BigDecimal("50.00")).build())
                        .build())
                .build();

        Transaccion originalTx = Transaccion.builder()
                .idTransaccion(2)
                .referencia(originalRef)
                .tipoOperacion("TRANSFERENCIA_ENTRADA")
                .idCuentaDestino(202)
                .estado("COMPLETADA")
                .descripcion("Transferencia entrada")
                .build();

        when(transaccionRepository.findByReferencia(originalRef)).thenReturn(Optional.of(originalTx));
        // Mock sufficient balance for debit
        when(cuentaCliente.obtenerSaldo(202)).thenReturn(new BigDecimal("1000.00"));

        // Act
        transaccionService.procesarDevolucionEntrante(request);

        // Assert
        // Verify balance update (Debit)
        verify(cuentaCliente).actualizarSaldo(any(Integer.class), any(SaldoDTO.class));
        verify(transaccionRepository, times(2)).save(any(Transaccion.class));
    }
}
