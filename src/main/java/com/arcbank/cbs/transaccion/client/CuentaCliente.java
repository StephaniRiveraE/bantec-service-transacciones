package com.arcbank.cbs.transaccion.client;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.arcbank.cbs.transaccion.dto.SaldoDTO;

@FeignClient(name = "ms-cuentas", url = "${app.feign.cuentas-url:http://localhost:8081}")
public interface CuentaCliente {

    @GetMapping("/api/v1/cuentas/ahorros/{id}/saldo")
    BigDecimal obtenerSaldo(@PathVariable("id") Integer id);

    @PutMapping("/api/v1/cuentas/ahorros/{id}/saldo")
    void actualizarSaldo(@PathVariable("id") Integer id, @RequestBody SaldoDTO saldoDTO);

    @GetMapping("/api/v1/cuentas/ahorros/{id}")
    Map<String, Object> obtenerCuenta(@PathVariable("id") Integer id);

    @GetMapping("/api/v1/cuentas/ahorros/buscar/{numeroCuenta}")
    Map<String, Object> buscarPorNumero(@PathVariable("numeroCuenta") String numeroCuenta);
}