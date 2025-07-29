package com.example.transportationserver.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * WebClient 설정을 통합한 Configuration 클래스
 * 중복된 WebClient 설정을 하나로 통합
 */
@Configuration
public class WebClientConfig {
    
    @Value("${api.korea.subway.base.url}")
    private String seoulApiBaseUrl;
    
    private static final String MOLIT_BASE_URL = "https://apis.data.go.kr/1613000";
    private static final String NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org";
    
    // 공통 설정
    private static final String USER_AGENT = "Transportation-Server/1.0";
    private static final String USER_AGENT_WITH_CONTACT = "Transportation-Server/1.0 (contact@example.com)";
    private static final int MAX_MEMORY_SIZE_1MB = 1024 * 1024;
    private static final int MAX_MEMORY_SIZE_2MB = 2 * 1024 * 1024;
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(60);
    
    /**
     * 서울시 공공데이터 API용 WebClient
     */
    @Bean("seoulApiWebClient")
    public WebClient seoulApiWebClient() {
        return WebClient.builder()
                .baseUrl(seoulApiBaseUrl)
                .defaultHeader("User-Agent", USER_AGENT)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Accept-Charset", "UTF-8")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_MEMORY_SIZE_1MB))
                .build();
    }
    
    /**
     * 국토교통부 MOLIT API용 WebClient
     */
    @Bean("molitApiWebClient")
    public WebClient molitApiWebClient() {
        return WebClient.builder()
                .baseUrl(MOLIT_BASE_URL)
                .defaultHeader("User-Agent", USER_AGENT)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Accept-Charset", "UTF-8")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_MEMORY_SIZE_2MB))
                .build();
    }
    
    /**
     * OpenStreetMap Nominatim API용 WebClient
     */
    @Bean("nominatimWebClient")
    public WebClient nominatimWebClient() {
        return WebClient.builder()
                .baseUrl(NOMINATIM_BASE_URL)
                .defaultHeader("User-Agent", USER_AGENT_WITH_CONTACT)
                .defaultHeader("Accept", "application/json")
                .build();
    }
    
    /**
     * 일반적인 외부 API 호출용 WebClient (기본 설정)
     */
    @Bean("defaultWebClient")
    public WebClient defaultWebClient() {
        return WebClient.builder()
                .defaultHeader("User-Agent", USER_AGENT)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Accept-Charset", "UTF-8")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_MEMORY_SIZE_1MB))
                .build();
    }
}