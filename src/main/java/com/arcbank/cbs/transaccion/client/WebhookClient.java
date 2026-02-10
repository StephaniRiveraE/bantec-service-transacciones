package com.arcbank.cbs.transaccion.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "webhook-client", url = "${app.switch.network-url}")
public interface WebhookClient {

    @PostMapping("/api/v1/callbacks/transfer-confirmation")
    void confirmarTransaccion(@RequestParam("targetBankId") String targetBankId,
            @RequestParam("instructionId") String instructionId,
            @RequestParam("status") String status);
}
