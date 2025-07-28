package com.example.transportationserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 기본 CORS 설정 (credentials 없이)
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH")
                .allowedHeaders("*")
                .exposedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
        
        // API 경로별 세부 설정 (특정 도메인만 credentials 허용)
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                    "http://localhost:*",
                    "http://127.0.0.1:*", 
                    "http://kkssyy.ipdisk.co.kr:*",
                    "https://kkssyy.ipdisk.co.kr:*"
                )
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH")
                .allowedHeaders("*")
                .exposedHeaders("Content-Type", "Authorization", "X-Requested-With", "Access-Control-Allow-Origin")
                .allowCredentials(false)
                .maxAge(3600);
    }
}