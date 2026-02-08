package com.arcbank.cbs.transaccion.dto;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TxRequest {
    private String debtorAccount;
    private String debtorName;
    private String creditorAccount;
    private String creditorName;
    private String targetBankId;
    private BigDecimal amount;
    private String description;
}
