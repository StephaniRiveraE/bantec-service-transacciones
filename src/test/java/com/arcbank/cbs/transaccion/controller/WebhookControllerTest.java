package com.arcbank.cbs.transaccion.controller;

import com.arcbank.cbs.transaccion.dto.SwitchTransferRequest;
import com.arcbank.cbs.transaccion.service.JwsService;
import com.arcbank.cbs.transaccion.service.TransaccionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebhookController.class)
public class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransaccionService transaccionService;

    @MockBean
    private JwsService jwsService;

    // Usamos el ObjectMapper real de Spring context o creamos uno nuevo si falla la
    // inyección
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Preparar mocks generales
    }

    @Test
    void recibirTransferencia_DatosValidos_Retorna200() throws Exception {
        // Arrange: Usar el DTO para construir la petición de forma segura
        SwitchTransferRequest.Header header = SwitchTransferRequest.Header.builder()
                .messageId("MSG-001")
                .originatingBankId("NEXUS")
                .creationDateTime("2025-01-01T10:00:00Z")
                .build();

        SwitchTransferRequest.Body body = SwitchTransferRequest.Body.builder()
                .instructionId("TX-12345")
                .endToEndId("E2E-12345")
                .amount(SwitchTransferRequest.Amount.builder()
                        .currency("USD")
                        .value(new BigDecimal("100.50"))
                        .build())
                .debtor(SwitchTransferRequest.Party.builder()
                        .name("Juan Perez")
                        .accountId("1234567890")
                        .build())
                .creditor(SwitchTransferRequest.Party.builder()
                        .name("Maria Lopez")
                        .accountId("0987654321")
                        .build())
                .remittanceInformation("Pago Servicio")
                .build();

        SwitchTransferRequest request = SwitchTransferRequest.builder()
                .header(header)
                .body(body)
                .build();

        // El controlador espera un formato específico que incluye el namespace en el
        // header para enrutamiento
        // Pero el DTO SwitchTransferRequest no tiene "messageNamespace" en su clase
        // Header interna según vi.
        // Espera, el controlador accede a: header.get("messageNamespace") desde el MAPA
        // crudo.
        // Si uso el DTO y lo serializo, "messageNamespace" NO ESTARÁ si no es campo del
        // DTO.

        // Revertimos estrategia parcial: Convertir DTO a Map y agregar el campo
        // faltante si es necesario para la lógica de enrutamiento
        Map<String, Object> payload = objectMapper.convertValue(request, Map.class);

        // Inyectar el namespace requerido por la lógica del controlador
        @SuppressWarnings("unchecked")
        Map<String, Object> headerMap = (Map<String, Object>) payload.get("header");
        headerMap.put("messageNamespace", "pacs.008.001.08");

        // Mock del servicio
        doNothing().when(transaccionService).procesarTransferenciaEntrante(
                anyString(), anyString(), any(BigDecimal.class), anyString());

        // Act & Assert
        mockMvc.perform(post("/api/v2/switch/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .header("x-jws-signature", "dummy-signature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACK"))
                .andExpect(jsonPath("$.instructionId").value("TX-12345"));
    }

    @Test
    void recibirTransferencia_BodyFaltante_Retorna400() throws Exception {
        // Arrange: Payload incompleto
        Map<String, Object> payload = new HashMap<>();
        payload.put("header", Map.of("messageNamespace", "pacs.008"));
        // Falta "body"

        // Act & Assert
        mockMvc.perform(post("/api/v2/switch/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest()) // El controlador maneja manualmente el body check y devuelve
                                                    // request o falla
                // Nota: En el código actual del controlador, si falla el mapeo devuelve
                // BadRequest custom.
                // Revisando el código: if (body == null) return ResponseEntity.badRequest()...
                .andExpect(jsonPath("$.status").value("NACK"));
    }
}
