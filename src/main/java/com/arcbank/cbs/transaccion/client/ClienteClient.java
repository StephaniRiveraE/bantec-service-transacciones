package com.arcbank.cbs.transaccion.client;

import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "ms-clientes", url = "${app.feign.clientes-url:http://micro-clientes:8080}")
public interface ClienteClient {

    @GetMapping("/api/v1/clientes/{id}")
    Map<String, Object> obtenerCliente(@PathVariable("id") Integer id);
}
