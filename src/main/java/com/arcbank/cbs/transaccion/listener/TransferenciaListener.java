package com.arcbank.cbs.transaccion.listener;

import com.arcbank.cbs.transaccion.client.SwitchClient;
import com.arcbank.cbs.transaccion.dto.MensajeISO;
import com.arcbank.cbs.transaccion.dto.StatusReportDTO;
import com.arcbank.cbs.transaccion.exception.BusinessException;
import com.arcbank.cbs.transaccion.service.TransaccionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransferenciaListener {

    private final TransaccionService transaccionService;
    private final SwitchClient switchClient;

    @Value("${app.banco.codigo:BANTEC}")
    private String bancoCodigo;

    @RabbitListener(queues = "${BANK_QUEUE_NAME:q.bank.BANTEC.in}")
    public void recibirTransferencia(MensajeISO mensaje) {
        String instructionId = mensaje.getBody().getInstructionId();
        log.info("Recibida transferencia: {} por ${}",
                instructionId,
                mensaje.getBody().getAmount().getValue());

        try {
            // 1. Procesar la transferencia (Validación + Depósito)
            transaccionService.procesarTransferenciaEntrante(
                    instructionId,
                    mensaje.getBody().getCreditor().getAccountId(),
                    mensaje.getBody().getAmount().getValue(),
                    mensaje.getHeader().getOriginatingBankId());

            log.info("Transacción procesada exitosamente: {}", instructionId);

            // 2. Enviar Callback de ÉXITO
            StatusReportDTO callback = construirCallback(instructionId, "COMPLETED", null, null);
            switchClient.enviarCallback(callback);

        } catch (BusinessException e) {
            log.error("Error de negocio procesando transferencia: {}", e.getMessage());

            // Determinar código de error ISO
            String reasonCode = e.getCode() != null ? e.getCode() : "MS03";
            if (reasonCode.equals("MS03") && e.getMessage().contains("AC01")) {
                reasonCode = "AC01";
            }

            // 3. Enviar Callback de RECHAZO
            StatusReportDTO callback = construirCallback(instructionId, "REJECTED", reasonCode, e.getMessage());
            try {
                switchClient.enviarCallback(callback);
            } catch (Exception ex) {
                log.error("Error enviando callback de rechazo al Switch: {}", ex.getMessage());
                // No re-lanzamos excepción aquí para evitar bucle infinito si el switch está
                // caído
                // aunque si el switch está caído, RabbitMQ podría reintentar el mensaje
                // original...
                // Pero como es un error de negocio (Cuenta inválida), NO debemos reintentar el
                // procesamiento.
            }

            // IMPORTANTE: No reencolar mensajes con errores de negocio (cuentas no
            // existentes, etc.)
            throw new AmqpRejectAndDontRequeueException(e.getMessage());

        } catch (Exception e) {
            log.error("Error técnico inesperado procesando transferencia", e);
            // Lanzar excepción para que RabbitMQ reintente (backoff exponencial)
            throw new RuntimeException(e);
        }
    }

    private StatusReportDTO construirCallback(String instructionId, String status,
            String reasonCode, String reasonDescription) {
        return StatusReportDTO.builder()
                .header(StatusReportDTO.Header.builder()
                        .messageId("RESP-" + UUID.randomUUID().toString())
                        .respondingBankId(bancoCodigo)
                        .creationDateTime(LocalDateTime.now().toString())
                        .build())
                .body(StatusReportDTO.Body.builder()
                        .originalInstructionId(UUID.fromString(instructionId))
                        .status(status)
                        .reasonCode(reasonCode)
                        .reasonDescription(reasonDescription)
                        .processedDateTime(LocalDateTime.now().toString())
                        .build())
                .build();
    }
}
