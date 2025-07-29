package com.example.transportationserver.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import jakarta.annotation.PreDestroy;
import java.time.Duration;

/**
 * WebClient 설정을 통합한 Configuration 클래스
 * 중복된 WebClient 설정을 하나로 통합
 */
@Configuration
public class WebClientConfig {
    
    private ConnectionProvider connectionProvider;
    private HttpClient httpClient;
    
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
     * 공통 연결 풀 설정
     */
    @Bean
    public ConnectionProvider connectionProvider() {
        this.connectionProvider = ConnectionProvider.builder("transport-api")
                .maxConnections(50)
                .maxIdleTime(Duration.ofSeconds(20))
                .maxLifeTime(Duration.ofMinutes(5))
                .pendingAcquireTimeout(CONNECTION_TIMEOUT)
                .evictInBackground(Duration.ofSeconds(30))
                .build();
        return this.connectionProvider;
    }
    
    /**
     * 공통 HttpClient 설정
     */
    @Bean
    public HttpClient httpClient(ConnectionProvider connectionProvider) {
        this.httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECTION_TIMEOUT.toMillis())
                .responseTimeout(RESPONSE_TIMEOUT)
                .keepAlive(true);
        return this.httpClient;
    }
    
    /**
     * 서울시 공공데이터 API용 WebClient
     */
    @Bean("seoulApiWebClient")
    public WebClient seoulApiWebClient(HttpClient httpClient) {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
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
    public WebClient molitApiWebClient(HttpClient httpClient) {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
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
    public WebClient nominatimWebClient(HttpClient httpClient) {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(NOMINATIM_BASE_URL)
                .defaultHeader("User-Agent", USER_AGENT_WITH_CONTACT)
                .defaultHeader("Accept", "application/json")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_MEMORY_SIZE_1MB))
                .build();
    }
    
    /**
     * 일반적인 외부 API 호출용 WebClient (기본 설정)
     */
    @Bean("defaultWebClient")
    public WebClient defaultWebClient(HttpClient httpClient) {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("User-Agent", USER_AGENT)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Accept-Charset", "UTF-8")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_MEMORY_SIZE_1MB))
                .build();
    }
    
    /**
     * 리소스 정리
     */
    @PreDestroy
    public void cleanup() {
        if (connectionProvider != null && !connectionProvider.isDisposed()) {
            connectionProvider.disposeLater().block(Duration.ofSeconds(5));
        }
    }
}