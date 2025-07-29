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
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class OpenStreetMapService {
    
    private static final Logger logger = LoggerFactory.getLogger(OpenStreetMapService.class);
    
    private final WebClient webClient;
    private final RateLimitService rateLimitService;
    
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);
    
    @Autowired
    public OpenStreetMapService(@Qualifier("nominatimWebClient") WebClient webClient,
                               RateLimitService rateLimitService) {
        this.webClient = webClient;
        this.rateLimitService = rateLimitService;
    }
    
    public Mono<Optional<Coordinate>> searchStationCoordinates(String stationName, String region) {
        // 입력값 공백 제거 (final 변수로 선언)
        final String finalStationName = stationName != null ? stationName.trim() : "";
        final String finalRegion = region != null ? region.trim() : "";
        
        logger.info("OpenStreetMap 좌표 검색 시작: {} (지역: {})", finalStationName, finalRegion);
        
        // 1차 검색 시도
        return performSearch(finalStationName, finalRegion, buildSearchQuery(finalStationName, finalRegion))
            .flatMap(result -> {
                if (result.isPresent()) {
                    logger.info("1차 검색 성공: {} -> {}", finalStationName, result.get());
                    return Mono.just(result);
                } else {
                    logger.info("1차 검색 실패, 2차 검색 시도: {}", finalStationName);
                    // 2차 검색: 역명만 (끝의 "역"만 제거)
                    String simpleQuery = finalStationName.trim();
                    if (simpleQuery.endsWith("역")) {
                        simpleQuery = simpleQuery.substring(0, simpleQuery.length() - 1);
                    }
                    return performSearch(finalStationName, finalRegion, simpleQuery);
                }
            })
            .doOnSuccess(finalResult -> {
                if (finalResult.isPresent()) {
                    logger.info("좌표 검색 성공: {} -> {}", finalStationName, finalResult.get());
                } else {
                    logger.warn("모든 검색 시도 실패: {}", finalStationName);
                }
            });
    }
    
    private Mono<Optional<Coordinate>> performSearch(String stationName, String region, String query) {
        logger.info("OSM 검색 수행: '{}' -> 쿼리: '{}'", stationName, query);
        
        return enforceRateLimit()
            .then(webClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/search")
                    .queryParam("q", query)
                    .queryParam("format", "json")
                    .queryParam("addressdetails", "1")
                    .queryParam("limit", "10")
                    .queryParam("countrycodes", "kr")
                    .build())
                .retrieve()
                .bodyToFlux(NominatimResult.class)
                .collectList()
                .timeout(REQUEST_TIMEOUT)
                .retryWhen(Retry.fixedDelay(1, RETRY_DELAY))
                .doOnNext(results -> {
                    logger.debug("OSM API 응답: {}개 결과 수신 (쿼리: '{}')", results.size(), query);
                    for (int i = 0; i < Math.min(results.size(), 2); i++) {
                        NominatimResult result = results.get(i);
                        logger.debug("결과 {}: {} ({}°, {}°) - 카테고리: {}", 
                            i+1, result.getDisplayName(), result.getLat(), result.getLon(), result.getCategory());
                    }
                })
                .map(this::selectBestResult)
                .onErrorResume(ErrorHandler.createOptionalErrorHandler(logger, "OSM 검색 (쿼리: '" + query + "')")));
    }
    
    /**
     * OpenStreetMap API 정책 준수: 1초에 1번 요청 제한
     */
    private Mono<Void> enforceRateLimit() {
        return Mono.fromRunnable(() -> {
            rateLimitService.waitForOpenStreetMap();
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()).then();
    }
    
    private String buildSearchQuery(String stationName, String region) {
        // 역명 정규화 (끝의 "역"만 제거)
        String cleanStationName = stationName.trim();
        if (cleanStationName.endsWith("역")) {
            cleanStationName = cleanStationName.substring(0, cleanStationName.length() - 1);
        }
        
        // 단순한 검색 쿼리: 역명만 사용
        return cleanStationName;
    }
    
    // 대체 검색 쿼리 생성 (fallback)
    private String buildAlternativeQuery(String stationName, String region) {
        String cleanStationName = stationName.trim();
        if (cleanStationName.endsWith("역")) {
            cleanStationName = cleanStationName.substring(0, cleanStationName.length() - 1);
        }
        
        // 2차: 지역 정보 추가
        if (region != null && !region.trim().isEmpty() && !region.equals("서울특별시")) {
            return cleanStationName + "역 " + region;
        }
        
        // 기본: station 키워드 추가
        return cleanStationName + " station";
    }
    
    private Optional<Coordinate> selectBestResult(List<NominatimResult> results) {
        if (results == null || results.isEmpty()) {
            return Optional.empty();
        }
        
        // 1순위: 지하철역으로 명확히 식별되는 결과
        for (NominatimResult result : results) {
            if (isValidSubwayStation(result)) {
                try {
                    double lat = Double.parseDouble(result.getLat());
                    double lon = Double.parseDouble(result.getLon());
                    
                    if (isValidKoreanCoordinate(lat, lon)) {
                        logger.debug("지하철역 매칭 성공: {}", result.getDisplayName());
                        return Optional.of(new Coordinate(lat, lon, result.getDisplayName()));
                    }
                } catch (NumberFormatException e) {
                    logger.warn("좌표 파싱 실패: {} {}", result.getLat(), result.getLon());
                }
            }
        }
        
        // 2순위: 역 이름이 포함된 모든 결과 (관대한 검색)
        for (NominatimResult result : results) {
            if (result.getDisplayName() != null && result.getDisplayName().contains("역")) {
                try {
                    double lat = Double.parseDouble(result.getLat());
                    double lon = Double.parseDouble(result.getLon());
                    
                    if (isValidKoreanCoordinate(lat, lon)) {
                        logger.debug("역명 포함 매칭: {}", result.getDisplayName());
                        return Optional.of(new Coordinate(lat, lon, result.getDisplayName()));
                    }
                } catch (NumberFormatException e) {
                    logger.warn("좌표 파싱 실패: {} {}", result.getLat(), result.getLon());
                }
            }
        }
        
        // 3순위: 첫 번째 유효한 한국 좌표 (최후 수단)
        for (NominatimResult result : results) {
            try {
                double lat = Double.parseDouble(result.getLat());
                double lon = Double.parseDouble(result.getLon());
                
                if (isValidKoreanCoordinate(lat, lon)) {
                    logger.debug("일반 좌표 매칭: {}", result.getDisplayName());
                    return Optional.of(new Coordinate(lat, lon, result.getDisplayName()));
                }
            } catch (NumberFormatException e) {
                // 무시하고 다음 결과 시도
            }
        }
        
        return Optional.empty();
    }
    
    private boolean isValidSubwayStation(NominatimResult result) {
        if (result.getDisplayName() == null) {
            return false;
        }
        
        String displayName = result.getDisplayName().toLowerCase();
        
        // 지하철역 관련 키워드 체크 (더 관대하게)
        boolean hasStationKeyword = displayName.contains("역") || 
                                   displayName.contains("station") ||
                                   displayName.contains("subway") || 
                                   displayName.contains("지하철") || 
                                   displayName.contains("metro");
        
        // 카테고리 체크 (railway 등)
        boolean hasRailwayCategory = result.getCategory() != null && 
                                   (result.getCategory().contains("railway") || 
                                    result.getCategory().contains("public_transport"));
        
        return hasStationKeyword || hasRailwayCategory;
    }
    
    private boolean isValidKoreanCoordinate(double lat, double lon) {
        return CoordinateValidator.isCoordinateInSeoulRange(lat, lon);
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NominatimResult {
        @JsonProperty("lat")
        private String lat;
        
        @JsonProperty("lon") 
        private String lon;
        
        @JsonProperty("display_name")
        private String displayName;
        
        @JsonProperty("category")
        private String category;
        
        @JsonProperty("type")
        private String type;
        
        @JsonProperty("importance")
        private Double importance;
        
        public String getLat() { return lat; }
        public void setLat(String lat) { this.lat = lat; }
        
        public String getLon() { return lon; }
        public void setLon(String lon) { this.lon = lon; }
        
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public Double getImportance() { return importance; }
        public void setImportance(Double importance) { this.importance = importance; }
    }
    
    public static class Coordinate {
        private final double latitude;
        private final double longitude;
        private final String source;
        
        public Coordinate(double latitude, double longitude, String source) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.source = source;
        }
        
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public String getSource() { return source; }
        
        @Override
        public String toString() {
            return String.format("%.6f,%.6f (%s)", latitude, longitude, source);
        }
    }
}