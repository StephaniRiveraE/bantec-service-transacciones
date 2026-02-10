package com.arcbank.cbs.transaccion.dto;

import java.math.BigDecimal;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaldoDTO {
    private BigDecimal saldo;
}
