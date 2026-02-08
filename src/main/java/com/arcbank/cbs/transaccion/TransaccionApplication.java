package com.arcbank.cbs.transaccion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class TransaccionApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransaccionApplication.class, args);
    }

}