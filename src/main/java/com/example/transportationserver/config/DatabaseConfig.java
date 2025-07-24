package com.example.transportationserver.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.example.transportationserver.repository")
public class DatabaseConfig {
}