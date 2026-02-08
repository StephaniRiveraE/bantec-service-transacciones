package com.arcbank.cbs.transaccion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusReportDTO {
    private Header header;
    private Body body;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Header {
        private String messageId; // ID único de la respuesta
        private String creationDateTime; // Timestamp ISO 8601
        private String respondingBankId; // BIC del banco que responde
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Body {
        private UUID originalInstructionId; // ID de la transacción original
        private String originalMessageId; // MessageId original (opcional)
        private String status; // COMPLETED o REJECTED
        private String reasonCode; // Código ISO si rechazada (AC03, AG01, etc.)
        private String reasonDescription; // Descripción del error si rechazada
        private String processedDateTime; // Timestamp de procesamiento
    }
}
