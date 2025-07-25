package com.example.transportationserver.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class OpenStreetMapClient {
    
    private static final Logger logger = LoggerFactory.getLogger(OpenStreetMapClient.class);
    private static final String NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org";
    private static final Duration RATE_LIMIT_DELAY = Duration.ofSeconds(1); // 1초 제한
    
    private final WebClient webClient;
    private final AtomicReference<LocalDateTime> lastRequestTime = new AtomicReference<>(LocalDateTime.MIN);
    
    public OpenStreetMapClient() {
        this.webClient = WebClient.builder()
                .baseUrl(NOMINATIM_BASE_URL)
                .defaultHeader("User-Agent", "Transportation-Server/1.0 (contact@example.com)")
                .build();
    }
    
    /**
     * 지하철역 좌표 검색 (1초 제한 적용)
     */
    public Mono<CoordinateResult> searchStationCoordinates(String stationName, String region) {
        return enforceRateLimit()
                .then(performSearch(stationName, region))
                .doOnSubscribe(s -> logger.debug("Searching coordinates for: {} in {}", stationName, region))
                .doOnSuccess(result -> {
                    if (result != null && result.isValid()) {
                        logger.info("Found coordinates for {}: lat={}, lon={}", 
                                stationName, result.getLatitude(), result.getLongitude());
                    } else {
                        logger.warn("No valid coordinates found for: {} in {}", stationName, region);
                    }
                })
                .doOnError(error -> logger.error("Error searching coordinates for {}: {}", stationName, error.getMessage()));
    }
    
    /**
     * Rate limiting 적용 (1초 제한)
     */
    private Mono<Void> enforceRateLimit() {
        return Mono.fromRunnable(() -> {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime lastRequest = lastRequestTime.get();
            
            Duration timeSinceLastRequest = Duration.between(lastRequest, now);
            
            if (timeSinceLastRequest.compareTo(RATE_LIMIT_DELAY) < 0) {
                Duration sleepTime = RATE_LIMIT_DELAY.minus(timeSinceLastRequest);
                try {
                    Thread.sleep(sleepTime.toMillis());
                    logger.debug("Rate limit applied: waited {}ms", sleepTime.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Rate limit wait interrupted", e);
                }
            }
            
            lastRequestTime.set(LocalDateTime.now());
        });
    }
    
    /**
     * 실제 검색 수행
     */
    private Mono<CoordinateResult> performSearch(String stationName, String region) {
        // 검색 쿼리 구성: "역명 지하철역 지역명"
        String query = String.format("%s 지하철역 %s", 
                stationName.replace("역", ""), region != null ? region : "");
        
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search")
                        .queryParam("q", query)
                        .queryParam("format", "json")
                        .queryParam("limit", 3)
                        .queryParam("addressdetails", 1)
                        .queryParam("extratags", 1)
                        .build())
                .retrieve()
                .bodyToMono(NominatimResponse[].class)
                .map(this::extractBestResult)
                .onErrorReturn(new CoordinateResult()); // 에러 시 빈 결과 반환
    }
    
    /**
     * 검색 결과에서 가장 적합한 결과 선택
     */
    private CoordinateResult extractBestResult(NominatimResponse[] responses) {
        if (responses == null || responses.length == 0) {
            return new CoordinateResult();
        }
        
        // 지하철역 관련 결과 우선 선택
        for (NominatimResponse response : responses) {
            if (isSubwayStationResult(response)) {
                return new CoordinateResult(
                        Double.parseDouble(response.lat),
                        Double.parseDouble(response.lon),
                        response.display_name,
                        response.importance
                );
            }
        }
        
        // 지하철역 결과가 없으면 첫 번째 결과 사용
        NominatimResponse first = responses[0];
        return new CoordinateResult(
                Double.parseDouble(first.lat),
                Double.parseDouble(first.lon),
                first.display_name,
                first.importance
        );
    }
    
    /**
     * 지하철역 관련 결과인지 확인
     */
    private boolean isSubwayStationResult(NominatimResponse response) {
        String displayName = response.display_name.toLowerCase();
        return displayName.contains("subway") || 
               displayName.contains("지하철") || 
               displayName.contains("역") ||
               (response.extratags != null && 
                (response.extratags.containsKey("railway") || 
                 response.extratags.containsKey("subway")));
    }
    
    /**
     * Nominatim API 응답 구조
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NominatimResponse {
        public String lat;
        public String lon;
        public String display_name;
        public Double importance;
        
        @JsonProperty("extratags")
        public java.util.Map<String, String> extratags;
        
        @JsonProperty("address")
        public java.util.Map<String, String> address;
    }
    
    /**
     * 좌표 검색 결과
     */
    public static class CoordinateResult {
        private final Double latitude;
        private final Double longitude;
        private final String address;
        private final Double confidence;
        
        public CoordinateResult() {
            this.latitude = null;
            this.longitude = null;
            this.address = null;
            this.confidence = 0.0;
        }
        
        public CoordinateResult(Double latitude, Double longitude, String address, Double confidence) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.address = address;
            this.confidence = confidence != null ? confidence : 0.0;
        }
        
        public boolean isValid() {
            return latitude != null && longitude != null;
        }
        
        public Double getLatitude() { return latitude; }
        public Double getLongitude() { return longitude; }
        public String getAddress() { return address; }
        public Double getConfidence() { return confidence; }
    }
}