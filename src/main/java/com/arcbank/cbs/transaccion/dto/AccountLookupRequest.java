package com.arcbank.cbs.transaccion.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class AccountLookupRequest {
    private Header header;
    private Body body;

    @Getter
    @Setter
    @Builder
    public static class Header {
        private String originatingBankId;
    }

    @Getter
    @Setter
    @Builder
    public static class Body {
        private String targetBankId;
        private String targetAccountNumber;
    }
}
