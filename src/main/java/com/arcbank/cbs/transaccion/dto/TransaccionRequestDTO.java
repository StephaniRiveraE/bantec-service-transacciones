package com.arcbank.cbs.transaccion.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransaccionRequestDTO {

    private String referencia;

    @NotNull(message = "El tipo de operación es obligatorio")
    private String tipoOperacion;

    private Integer idCuentaOrigen;
    private Integer idCuentaDestino;

    private String cuentaExterna;
    private String idBancoExterno;
    private Integer idTransaccionReversa;

    @NotNull(message = "El monto es obligatorio")
    @Positive(message = "El monto debe ser positivo")
    private BigDecimal monto;

    private String descripcion;
    private String beneficiario;
    private String canal;
    private Integer idSucursal;
}