package com.example.transportationserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 좌표 정보 통합 및 보완 서비스
 */
@Service
public class CoordinateIntegrationService {
    
    private static final Logger logger = LoggerFactory.getLogger(CoordinateIntegrationService.class);
    
    @Autowired
    private OpenStreetMapService osmService;
    
    @Autowired
    private StationNameResolver nameResolver;
    
    // 좌표 캐시 (참고 코드의 Hive 캐싱 로직 응용)
    private final Map<String, CachedCoordinate> coordinateCache = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRATION_HOURS = 24 * 7; // 7일
    
    // 좌표 우선순위 (높을수록 신뢰도 높음)
    private static final Map<String, Integer> COORDINATE_PRIORITY = Map.of(
        "MOLIT_API", 10,        // 국토교통부 - 가장 신뢰
        "SEOUL_API", 8,         // 서울시 API
        "OSM", 6,               // OpenStreetMap
        "MANUAL", 9,            // 수동 입력
        "CALCULATED", 4         // 계산된 좌표
    );
    
    /**
     * 역 그룹의 대표 좌표 결정
     */
    public CoordinateResult determineGroupCoordinate(List<StationCoordinate> stationCoordinates) {
        if (stationCoordinates.isEmpty()) {
            return new CoordinateResult(null, null, "NO_DATA", 0);
        }
        
        // 1. 유효한 좌표만 필터링
        List<StationCoordinate> validCoordinates = stationCoordinates.stream()
            .filter(this::isValidCoordinate)
            .collect(Collectors.toList());
            
        if (validCoordinates.isEmpty()) {
            return new CoordinateResult(null, null, "INVALID_DATA", 0);
        }
        
        // 2. 좌표 클러스터링 (근접한 좌표들 그룹화)
        List<CoordinateCluster> clusters = clusterCoordinates(validCoordinates);
        
        // 3. 가장 큰 클러스터의 대표 좌표 선택
        CoordinateCluster primaryCluster = clusters.stream()
            .max(Comparator.comparing(cluster -> cluster.getWeight()))
            .orElse(clusters.get(0));
            
        // 4. 클러스터 내에서 최고 우선순위 좌표 선택
        StationCoordinate bestCoordinate = primaryCluster.getCoordinates().stream()
            .max(Comparator.comparing(coord -> COORDINATE_PRIORITY.getOrDefault(coord.getSource(), 0)))
            .orElse(primaryCluster.getCoordinates().get(0));
            
        return new CoordinateResult(
            bestCoordinate.getLatitude(),
            bestCoordinate.getLongitude(),
            bestCoordinate.getSource(),
            calculateConfidence(primaryCluster, clusters.size())
        );
    }
    
    /**
     * 좌표 클러스터링 (200m 이내 좌표들을 같은 그룹으로 묶음)
     */
    private List<CoordinateCluster> clusterCoordinates(List<StationCoordinate> coordinates) {
        List<CoordinateCluster> clusters = new ArrayList<>();
        List<StationCoordinate> unprocessed = new ArrayList<>(coordinates);
        
        while (!unprocessed.isEmpty()) {
            StationCoordinate seed = unprocessed.remove(0);
            CoordinateCluster cluster = new CoordinateCluster();
            cluster.addCoordinate(seed);
            
            // 200m 이내 좌표들을 같은 클러스터로 묶음
            Iterator<StationCoordinate> iterator = unprocessed.iterator();
            while (iterator.hasNext()) {
                StationCoordinate coord = iterator.next();
                if (nameResolver.isSameStationGroup(
                    seed.getLatitude(), seed.getLongitude(),
                    coord.getLatitude(), coord.getLongitude())) {
                    cluster.addCoordinate(coord);
                    iterator.remove();
                }
            }
            
            clusters.add(cluster);
        }
        
        return clusters;
    }
    
    /**
     * 좌표 유효성 검증 (참고 코드 로직 적용)
     */
    public boolean isValidCoordinate(StationCoordinate coord) {
        if (coord == null) return false;
        return isCoordinateValid(coord.getLatitude(), coord.getLongitude());
    }
    
    /**
     * 좌표 유효성 검증 (참고 코드 isCoordinateValid 로직)
     */
    public boolean isCoordinateValid(double latitude, double longitude) {
        // 한국 영역 내 좌표 검증 + 기본 유효성 검증
        return latitude >= 33.0 && latitude <= 43.0 &&    // 위도 범위
               longitude >= 124.0 && longitude <= 132.0 && // 경도 범위
               latitude != 0.0 && longitude != 0.0 &&      // 0,0 좌표 제외
               !Double.isNaN(latitude) && !Double.isNaN(longitude) && // NaN 검증
               !Double.isInfinite(latitude) && !Double.isInfinite(longitude); // 무한대 검증
    }
    
    /**
     * 좌표가 비어있는지 확인 (참고 코드 isCoordinateEmpty 로직)
     */
    public boolean isCoordinateEmpty(Double latitude, Double longitude) {
        return latitude == null || longitude == null || 
               latitude == 0.0 || longitude == 0.0;
    }
    
    /**
     * 신뢰도 계산
     */
    private int calculateConfidence(CoordinateCluster primaryCluster, int totalClusters) {
        int baseConfidence = 50;
        
        // 클러스터 내 좌표 개수가 많을수록 신뢰도 증가
        baseConfidence += Math.min(primaryCluster.getCoordinates().size() * 10, 30);
        
        // 클러스터가 하나뿐이면 신뢰도 증가
        if (totalClusters == 1) {
            baseConfidence += 20;
        }
        
        // 최고 우선순위 소스 가중치
        String topSource = primaryCluster.getCoordinates().stream()
            .max(Comparator.comparing(coord -> COORDINATE_PRIORITY.getOrDefault(coord.getSource(), 0)))
            .map(StationCoordinate::getSource)
            .orElse("UNKNOWN");
            
        baseConfidence += COORDINATE_PRIORITY.getOrDefault(topSource, 0);
        
        return Math.min(baseConfidence, 100);
    }
    
    /**
     * 좌표 정보가 없는 역에 대한 좌표 보완 (캐싱 적용)
     */
    @Cacheable(value = "coordinateCache", key = "#stationName + '_' + #region")
    public Mono<CoordinateResult> supplementCoordinate(String stationName, String region, String city) {
        String cacheKey = generateCacheKey(stationName, region);
        
        // 캐시 확인 (참고 코드 로직)
        CachedCoordinate cached = coordinateCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            logger.debug("Using cached coordinate for {}", stationName);
            return Mono.just(cached.toCoordinateResult());
        }
        
        return osmService.searchStationCoordinates(stationName, region)
            .map(osmCoordinate -> {
                CoordinateResult result;
                if (osmCoordinate.isPresent()) {
                    OpenStreetMapService.Coordinate coord = osmCoordinate.get();
                    result = new CoordinateResult(
                        coord.getLatitude(),
                        coord.getLongitude(),
                        "OSM",
                        70  // OSM 신뢰도
                    );
                    
                    // 성공한 결과 캐싱
                    cacheCoordinate(cacheKey, result);
                } else {
                    result = new CoordinateResult(null, null, "NOT_FOUND", 0);
                }
                return result;
            })
            .doOnNext(result -> {
                if (result.isValid()) {
                    logger.info("Coordinate supplemented for {}: lat={}, lon={}, confidence={}", 
                        stationName, result.getLatitude(), result.getLongitude(), result.getConfidence());
                } else {
                    logger.warn("Failed to supplement coordinate for {}", stationName);
                }
            });
    }
    
    /**
     * 좌표 캐시 저장 (참고 코드 로직)
     */
    private void cacheCoordinate(String cacheKey, CoordinateResult result) {
        if (result.isValid()) {
            coordinateCache.put(cacheKey, new CachedCoordinate(
                result.getLatitude(),
                result.getLongitude(),
                result.getSource(),
                result.getConfidence(),
                LocalDateTime.now()
            ));
        }
    }
    
    /**
     * 캐시 키 생성
     */
    private String generateCacheKey(String stationName, String region) {
        return stationName + "_" + (region != null ? region : "UNKNOWN");
    }
    
    /**
     * 특정 역의 좌표 업데이트 (참고 코드 updateStationCoordinates 로직)
     */
    public void updateStationCoordinates(String stationName, String lineName, 
                                        double latitude, double longitude) {
        if (!isCoordinateValid(latitude, longitude)) {
            logger.warn("유효하지 않은 좌표로 업데이트 시도: {} - lat: {}, lon: {}", 
                       stationName, latitude, longitude);
            return;
        }
        
        String cacheKey = generateCacheKey(stationName, lineName);
        cacheCoordinate(cacheKey, new CoordinateResult(
            latitude, longitude, "MANUAL", 90
        ));
        
        logger.debug("{} 좌표 업데이트: {}, {}", stationName, latitude, longitude);
    }
    
    /**
     * 좌표가 없는 역들 조회 (참고 코드 getStationsWithoutCoordinates 로직)
     */
    public List<StationCoordinate> getStationsWithoutCoordinates(List<StationCoordinate> allStations) {
        return allStations.stream()
            .filter(station -> isCoordinateEmpty(station.getLatitude(), station.getLongitude()))
            .collect(Collectors.toList());
    }
    
    /**
     * 좌표 통계 정보 (참고 코드 getCoordinateStatistics 로직)
     */
    public CoordinateStatistics getCoordinateStatistics(List<StationCoordinate> allStations) {
        int total = allStations.size();
        int hasValidCoordinates = 0;
        int missingCoordinates = 0;
        
        for (StationCoordinate station : allStations) {
            if (isCoordinateValid(station.getLatitude(), station.getLongitude())) {
                hasValidCoordinates++;
            } else {
                missingCoordinates++;
            }
        }
        
        return new CoordinateStatistics(total, hasValidCoordinates, missingCoordinates);
    }
    
    
    /**
     * 좌표 정보 클래스
     */
    public static class StationCoordinate {
        private final double latitude;
        private final double longitude;
        private final String source;
        private final String stationId;
        
        public StationCoordinate(double latitude, double longitude, String source, String stationId) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.source = source;
            this.stationId = stationId;
        }
        
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public String getSource() { return source; }
        public String getStationId() { return stationId; }
        
        /**
         * 좌표 유효성 확인 (참고 코드 isCoordinateValid 로직)
         */
        public boolean isCoordinateValid() {
            return latitude >= 33.0 && latitude <= 43.0 &&
                   longitude >= 124.0 && longitude <= 132.0 &&
                   latitude != 0.0 && longitude != 0.0 &&
                   !Double.isNaN(latitude) && !Double.isNaN(longitude);
        }
        
        /**
         * 좌표가 비어있는지 확인
         */
        public boolean isCoordinateEmpty() {
            return latitude == 0.0 || longitude == 0.0;
        }
    }
    
    /**
     * 좌표 클러스터 클래스
     */
    private static class CoordinateCluster {
        private final List<StationCoordinate> coordinates = new ArrayList<>();
        
        public void addCoordinate(StationCoordinate coordinate) {
            coordinates.add(coordinate);
        }
        
        public List<StationCoordinate> getCoordinates() {
            return coordinates;
        }
        
        public int getWeight() {
            return coordinates.stream()
                .mapToInt(coord -> COORDINATE_PRIORITY.getOrDefault(coord.getSource(), 0))
                .sum();
        }
    }
    
    /**
     * 좌표 결과 클래스
     */
    public static class CoordinateResult {
        private final Double latitude;
        private final Double longitude;
        private final String source;
        private final int confidence;
        
        public CoordinateResult(Double latitude, Double longitude, String source, int confidence) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.source = source;
            this.confidence = confidence;
        }
        
        public boolean isValid() {
            return latitude != null && longitude != null;
        }
        
        public Double getLatitude() { return latitude; }
        public Double getLongitude() { return longitude; }
        public String getSource() { return source; }
        public int getConfidence() { return confidence; }
    }
    
    /**
     * 캐시된 좌표 클래스 (참고 코드 스타일)
     */
    private static class CachedCoordinate {
        private final Double latitude;
        private final Double longitude;
        private final String source;
        private final int confidence;
        private final LocalDateTime cachedAt;
        
        public CachedCoordinate(Double latitude, Double longitude, String source, 
                              int confidence, LocalDateTime cachedAt) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.source = source;
            this.confidence = confidence;
            this.cachedAt = cachedAt;
        }
        
        public boolean isExpired() {
            return LocalDateTime.now().minusHours(CACHE_EXPIRATION_HOURS).isAfter(cachedAt);
        }
        
        public CoordinateResult toCoordinateResult() {
            return new CoordinateResult(latitude, longitude, source, confidence);
        }
    }
    
    /**
     * 좌표 통계 클래스 (참고 코드 로직)
     */
    public static class CoordinateStatistics {
        private final int total;
        private final int hasCoordinates;
        private final int missingCoordinates;
        
        public CoordinateStatistics(int total, int hasCoordinates, int missingCoordinates) {
            this.total = total;
            this.hasCoordinates = hasCoordinates;
            this.missingCoordinates = missingCoordinates;
        }
        
        public int getTotal() { return total; }
        public int getHasCoordinates() { return hasCoordinates; }
        public int getMissingCoordinates() { return missingCoordinates; }
        
        public double getCompletionRate() {
            return total > 0 ? (double) hasCoordinates / total * 100 : 0;
        }
    }
}