package com.arcbank.cbs.transaccion.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountLookupResponse {
    private String status;
    private Data data;

    @lombok.Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Data {
        private boolean exists;
        private String ownerName;
        private String currency;
        private String status;
        private String mensaje; // For error cases
    }
}
