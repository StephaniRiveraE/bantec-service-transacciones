package com.arcbank.cbs.transaccion.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class MensajeISO {
    private Header header;
    private Body body;

    @Data
    public static class Header {
        private String messageId; // ID único del mensaje
        private String creationDateTime; // Timestamp ISO 8601
        private String originatingBankId; // BIC del banco origen (quien envía)
    }

    @Data
    public static class Body {
        private String instructionId; // UUID de la instrucción
        private String endToEndId; // Referencia del cliente
        private Amount amount;
        private Debtor debtor; // Ordenante
        private Creditor creditor; // Beneficiario
        private String remittanceInformation; // Concepto
    }

    @Data
    public static class Amount {
        private String currency; // "USD"
        private BigDecimal value; // Monto
    }

    @Data
    public static class Debtor {
        private String name;
        private String accountId;
        private String accountType; // CHECKING, SAVINGS
    }

    @Data
    public static class Creditor {
        private String name;
        private String accountId;
        private String accountType;
        private String targetBankId; // ROUTING KEY - BIC destino
    }
}
