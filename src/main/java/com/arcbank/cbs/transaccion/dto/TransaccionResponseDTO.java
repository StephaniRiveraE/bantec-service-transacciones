package com.arcbank.cbs.transaccion.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransaccionResponseDTO {
    private Integer idTransaccion;
    private String referencia;
    private String codigoReferencia;
    private String tipoOperacion;

    private Integer idCuentaOrigen;
    private Integer idCuentaDestino;

    private String cuentaExterna;
    private String idBancoExterno;

    private BigDecimal monto;
    private BigDecimal saldoResultante;

    private LocalDateTime fechaCreacion;
    private String descripcion;
    private String canal;
    private String estado;
    private String mensajeUsuario;

    // Campos adicionales para mostrar información del origen/destino
    private String numeroCuentaOrigen;
    private String nombreOrigen;
    private String numeroCuentaDestino;
    private String nombreDestino;
}
