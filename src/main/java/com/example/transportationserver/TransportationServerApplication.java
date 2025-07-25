package com.example.transportationserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class TransportationServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransportationServerApplication.class, args);
    }
}