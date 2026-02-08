package com.arcbank.cbs.transaccion.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {

    @Value("${server.port}")
    private String serverPort;

    @Bean
    public OpenAPI myOpenAPI() {
        Server localServer = new Server();
        localServer.setUrl("http://localhost:" + serverPort);
        localServer.setDescription("Servidor Local (Desarrollo)");

        Contact contact = new Contact();
        contact.setEmail("dev@arcbank.com");
        contact.setName("Equipo de Desarrollo ArcBank");

        License mitLicense = new License().name("MIT License").url("https://choosealicense.com/licenses/mit/");

        Info info = new Info()
                .title("Microservicio de Transacciones API")
                .version("1.0")
                .contact(contact)
                .description(
                        "API RESTful para la gestión de movimientos financieros (Depósitos, Retiros, Transferencias). "
                                +
                                "Sigue el estilo arquitectónico Resource-Oriented.")
                .license(mitLicense);

        return new OpenAPI().info(info).servers(List.of(localServer));
    }
}