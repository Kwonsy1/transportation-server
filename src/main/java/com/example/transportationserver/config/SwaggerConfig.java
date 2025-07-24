package com.example.transportationserver.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Value("${server.port:5300}")
    private String serverPort;

    @Bean
    public OpenAPI transportationServerOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Transportation Server API")
                        .description("한국 지하철 정보 및 교통 데이터를 제공하는 REST API 서버입니다. " +
                                   "Flutter 앱과 연동하여 실시간 지하철 도착정보, 시간표, 주변 역 검색 등의 기능을 제공합니다.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Transportation Server Team")
                                .email("support@transportationserver.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development"),
                        new Server()
                                .url("http://kkssyy.ipdisk.co.kr:" + serverPort)
                                .description("External Server"),
                        new Server()
                                .url("https://kkssyy.ipdisk.co.kr:" + serverPort)
                                .description("External Server (HTTPS)")
                ));
    }
}