package com.example.transportationserver.service;

import com.example.transportationserver.util.CoordinateValidator;
import com.example.transportationserver.util.ErrorHandler;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final WebClient webClient;
    private final RateLimitService rateLimitService;
    
    @Autowired
    public OpenStreetMapClient(@Qualifier("nominatimWebClient") WebClient webClient,
                              RateLimitService rateLimitService) {
        this.webClient = webClient;
        this.rateLimitService = rateLimitService;
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
                .onErrorResume(ErrorHandler.createReactiveErrorHandler(null, logger, "좌표 검색 (" + stationName + ")"));
    }
    
    /**
     * Rate limiting 적용 (1초 제한)
     */
    private Mono<Void> enforceRateLimit() {
        return Mono.fromRunnable(() -> {
            rateLimitService.waitForOpenStreetMap();
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()).then();
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
                Double lat = CoordinateValidator.parseCoordinate(response.lat);
                Double lon = CoordinateValidator.parseCoordinate(response.lon);
                if (CoordinateValidator.isValidKoreanCoordinate(lat, lon)) {
                    return new CoordinateResult(lat, lon, response.display_name, response.importance);
                }
            }
        }
        
        // 지하철역 결과가 없으면 첫 번째 결과 사용
        NominatimResponse first = responses[0];
        Double lat = CoordinateValidator.parseCoordinate(first.lat);
        Double lon = CoordinateValidator.parseCoordinate(first.lon);
        if (CoordinateValidator.isValidKoreanCoordinate(lat, lon)) {
            return new CoordinateResult(lat, lon, first.display_name, first.importance);
        }
        
        return null; // 유효한 좌표가 없을 경우
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
            return CoordinateValidator.isValidKoreanCoordinate(latitude, longitude);
        }
        
        public Double getLatitude() { return latitude; }
        public Double getLongitude() { return longitude; }
        public String getAddress() { return address; }
        public Double getConfidence() { return confidence; }
    }
}