package com.arcbank.cbs.transaccion.exception;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ErrorResponse {
    private String mensaje;
    private String codigo;
    private LocalDateTime fecha;
}