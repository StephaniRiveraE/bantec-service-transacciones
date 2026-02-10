package com.arcbank.cbs.transaccion.client;

import java.net.URI;
import java.util.Collections;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.arcbank.cbs.transaccion.dto.SwitchTransferRequest;
import com.arcbank.cbs.transaccion.dto.SwitchTransferResponse;
import com.arcbank.cbs.transaccion.exception.BusinessException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SwitchClientService {

    private final RestTemplate restTemplate;

    @Value("${app.switch.network-url:http://localhost:8081}")
    private String switchUrl;

    @Value("${app.switch.apikey:DEFAULT_API_KEY}")
    private String apiKey;

    public SwitchClientService(org.springframework.boot.web.client.RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    public SwitchTransferResponse enviarTransferencia(SwitchTransferRequest request) {
        try {
            String url = switchUrl + "/transacciones";
            log.info("🚀 Sending transfer to Switch: {}", url);

            if (request.getBody().getInstructionId() == null) {
                request.getBody().setInstructionId(UUID.randomUUID().toString());
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            headers.set("apikey", apiKey);

            HttpEntity<SwitchTransferRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<String> rawResponse = restTemplate.postForEntity(
                    URI.create(url),
                    entity,
                    String.class);

            log.info("📥 Switch raw response - Status: {}, Body: {}",
                    rawResponse.getStatusCode(), rawResponse.getBody());

            if (rawResponse.getBody() == null || rawResponse.getBody().isBlank()) {
                if (rawResponse.getStatusCode().is2xxSuccessful()) {
                    log.info("✅ Switch returned 2xx with empty body - treating as success");
                    return SwitchTransferResponse.builder()
                            .success(true)
                            .build();
                }
                throw new BusinessException("Switch returned empty body with non-2xx status");
            }

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            SwitchTransferResponse switchResp = mapper.readValue(rawResponse.getBody(), SwitchTransferResponse.class);

            if (rawResponse.getStatusCode().is2xxSuccessful() && switchResp.getError() == null) {
                log.info("✅ Switch returned 2xx without error - treating as success");
                switchResp.setSuccess(true);
            }

            return switchResp;

        } catch (Exception e) {
            log.error("Error sending transfer to switch: {}", e.getMessage(), e);
            return SwitchTransferResponse.builder()
                    .success(false)
                    .error(SwitchTransferResponse.ErrorBody.builder()
                            .code("SYSTEM_ERROR")
                            .message(e.getMessage())
                            .build())
                    .build();
        }
    }
}
