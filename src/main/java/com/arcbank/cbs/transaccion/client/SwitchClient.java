package com.arcbank.cbs.transaccion.client;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.arcbank.cbs.transaccion.dto.SwitchTransferRequest;
import com.arcbank.cbs.transaccion.dto.SwitchRefundRequest;
import com.arcbank.cbs.transaccion.dto.SwitchTransferResponse;

@FeignClient(name = "digiconecu-switch", url = "${app.switch.network-url:http://34.16.106.7:8000}", configuration = {
                com.arcbank.cbs.transaccion.config.MTLSConfig.class,
                com.arcbank.cbs.transaccion.config.SwitchFeignDecoderConfig.class,
                com.arcbank.cbs.transaccion.config.SecurityInterceptor.class })
public interface SwitchClient {

        @PostMapping("/api/v2/switch/transfers")
        SwitchTransferResponse enviarTransferencia(@RequestBody SwitchTransferRequest request);

        @GetMapping("/api/v1/red/bancos")
        List<Map<String, Object>> obtenerBancos();

        @GetMapping("/api/v2/transfers/health")
        Map<String, String> healthCheck();

        @PostMapping("/api/v2/switch/transfers/return")
        SwitchTransferResponse solicitarDevolucion(@RequestBody SwitchRefundRequest request);

        @GetMapping("/api/v2/switch/transfers/{instructionId}")
        SwitchTransferResponse consultarEstadoTransferencia(
                        @org.springframework.web.bind.annotation.PathVariable("instructionId") String instructionId);

        @GetMapping("/api/v1/reference/iso20022/errors")
        List<Map<String, String>> obtenerMotivosDevolucion();

        @PostMapping("/api/v2/switch/accounts/lookup")
        com.arcbank.cbs.transaccion.dto.AccountLookupResponse lookupAccount(
                        @RequestBody com.arcbank.cbs.transaccion.dto.AccountLookupRequest request);

        @PostMapping("/api/v2/switch/transfers/callback")
        Object enviarCallback(@RequestBody com.arcbank.cbs.transaccion.dto.StatusReportDTO callback);
}
