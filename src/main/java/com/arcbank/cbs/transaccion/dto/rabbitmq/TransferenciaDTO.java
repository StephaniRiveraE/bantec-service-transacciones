package com.arcbank.cbs.transaccion.dto.rabbitmq;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class TransferenciaDTO {
    private Header header;
    private Body body;

    @Data
    public static class Header {
        private String messageId;
        private String creationDateTime;
        private String originatingBankId;
    }

    @Data
    public static class Body {
        private String instructionId;
        private String endToEndId;
        private Amount amount;
        private Debtor debtor;
        private Creditor creditor;
        private String remittanceInformation;
    }

    @Data
    public static class Amount {
        private String currency;
        private BigDecimal value;
    }

    @Data
    public static class Debtor {
        private String name;
        private String accountId;
        private String accountType;
    }

    @Data
    public static class Creditor {
        private String name;
        private String accountId;
        private String accountType;
        private String targetBankId;
    }
}
