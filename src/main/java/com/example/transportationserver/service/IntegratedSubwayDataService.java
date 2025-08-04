package com.example.transportationserver.service;

import com.example.transportationserver.util.DataMapper;
import com.example.transportationserver.util.ReactiveRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import com.example.transportationserver.dto.SubwayStationApiDto;

/**
 * 통합 지하철 데이터 동기화 서비스
 * 서울시 API + 국토교통부 API + OSM 데이터를 통합하여 정규화된 데이터베이스 구조 생성
 */
@Service
public class IntegratedSubwayDataService {
    
    private static final Logger logger = LoggerFactory.getLogger(IntegratedSubwayDataService.class);
    
    @Autowired
    private KoreanSubwayApiClient seoulApiClient;
    
    @Autowired
    private MolitApiClient molitApiClient;
    
    @Autowired
    private StationNameResolver nameResolver;
    
    @Autowired
    private CoordinateIntegrationService coordinateService;
    
    @Autowired
    private OpenStreetMapService openStreetMapService;
    
    @Autowired
    private SubwayStationService stationService;
    
    @Autowired
    private ReactiveRateLimiter rateLimiter;
    
    @Autowired
    private RateLimitService rateLimitService;
    
    @Value("${api.korea.subway.key}")
    private String apiKey;
    
    @Value("${api.molit.service.key}")
    private String molitServiceKey;
    
    // 좌표 보완 작업 진행 상황 추적
    private final AtomicInteger coordinateProgressTotal = new AtomicInteger(0);
    private final AtomicInteger coordinateProgressCurrent = new AtomicInteger(0);
    private final AtomicInteger coordinateProgressSuccess = new AtomicInteger(0);
    private final AtomicInteger coordinateProgressFailed = new AtomicInteger(0);
    private final AtomicReference<String> coordinateProgressStatus = new AtomicReference<>("IDLE");
    private final AtomicReference<String> coordinateProgressCurrentStation = new AtomicReference<>("");
    
    // TODO: Enhanced 데이터베이스 매퍼들 추가 필요
    // @Autowired
    // private StationGroupMapper stationGroupMapper;
    // @Autowired 
    // private SubwayLineMapper subwayLineMapper;
    // @Autowired
    // private EnhancedStationMapper enhancedStationMapper;
    
    /**
     * 전체 데이터 통합 동기화 프로세스
     */
    @Async
    @Transactional
    public CompletableFuture<SyncResult> performIntegratedSync() {
        logger.info("=== 통합 지하철 데이터 동기화 시작 ===");
        
        try {
            SyncResult result = new SyncResult();
            
            // 1단계: 서울시 API에서 기본 역명 수집
            logger.info("1단계: 서울시 API 데이터 수집");
            Set<String> seoulStationNames = collectSeoulStationNames();
            result.setSeoulStationsFound(seoulStationNames.size());
            logger.info("서울시 API: {} 개 역명 수집", seoulStationNames.size());
            
            // 2단계: 국토교통부 API로 상세정보 수집 (비동기)
            logger.info("2단계: 국토교통부 API 상세정보 수집");
            Map<String, List<MolitApiClient.MolitStationInfo>> molitData = collectMolitDataAsync(seoulStationNames).get();
            result.setMolitStationsFound(molitData.values().stream().mapToInt(List::size).sum());
            logger.info("국토교통부 API: {} 개 역 상세정보 수집", result.getMolitStationsFound());
            
            // 3단계: 데이터 정규화 및 그룹화
            logger.info("3단계: 데이터 정규화 및 역 그룹화");
            List<StationGroup> stationGroups = normalizeAndGroupStations(seoulStationNames, molitData);
            result.setStationGroupsCreated(stationGroups.size());
            logger.info("생성된 역 그룹: {} 개", stationGroups.size());
            
            // 4단계: 좌표 정보 통합 및 보완
            logger.info("4단계: 좌표 정보 통합 및 보완");
            int coordinatesEnriched = enrichCoordinatesAsync(stationGroups).get();
            result.setCoordinatesEnriched(coordinatesEnriched);
            logger.info("좌표 정보 보완: {} 개 그룹", coordinatesEnriched);
            
            // 5단계: 데이터베이스 저장
            logger.info("5단계: 데이터베이스 저장");
            saveToEnhancedDatabase(stationGroups);
            result.setSuccess(true);
            
            logger.info("=== 통합 지하철 데이터 동기화 완료 ===");
            logger.info("결과: {}", result);
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            logger.error("통합 동기화 중 오류 발생", e);
            return CompletableFuture.completedFuture(SyncResult.failed(e.getMessage()));
        }
    }
    
    /**
     * 서울시 API에서 역명 수집
     */
    private Set<String> collectSeoulStationNames() {
        logger.info("서울시 API에서 전체 지하철역 데이터 수집 시작");
        Set<String> stationNames = new HashSet<>();
        
        try {
            // 서울 지하철 API에서 전체 데이터 수집 (페이징 처리)
            int pageSize = 1000; // 한 번에 가져올 데이터 수
            int maxPages = 10;   // 최대 페이지 수 (안전장치)
            
            for (int page = 0; page < maxPages; page++) {
                int startIndex = page * pageSize + 1;
                int endIndex = (page + 1) * pageSize;
                
                logger.info("서울 API 페이지 {} 요청: {}-{}", page + 1, startIndex, endIndex);
                
                try {
                    List<SubwayStationApiDto> stations = seoulApiClient.getAllStations(startIndex, endIndex)
                        .block(Duration.ofSeconds(30)); // 30초 타임아웃
                    
                    if (stations == null || stations.isEmpty()) {
                        logger.info("서울 API 페이지 {}에서 데이터 없음. 수집 종료", page + 1);
                        break;
                    }
                    
                    // 역명 추출 및 정리
                    Set<String> pageStationNames = stations.stream()
                        .map(SubwayStationApiDto::getStationName)
                        .filter(name -> name != null && !name.trim().isEmpty())
                        .map(String::trim)
                        .collect(Collectors.toSet());
                    
                    stationNames.addAll(pageStationNames);
                    logger.info("서울 API 페이지 {} 완료: {}개 역명 수집 (누적: {}개)", 
                              page + 1, pageStationNames.size(), stationNames.size());
                    
                    // 데이터가 pageSize보다 적으면 마지막 페이지
                    if (stations.size() < pageSize) {
                        logger.info("마지막 페이지 도달. 서울 API 수집 완료");
                        break;
                    }
                    
                    // API 호출 제한 준수
                    rateLimitService.waitForSeoulApi();
                    
                } catch (Exception e) {
                    logger.warn("서울 API 페이지 {} 호출 실패: {}", page + 1, e.getMessage());
                    // 에러가 발생해도 다음 페이지 시도
                    continue;
                }
            }
            
            logger.info("서울시 API 데이터 수집 완료: 총 {}개 역명", stationNames.size());
            
        } catch (Exception e) {
            logger.error("서울시 API 데이터 수집 중 전체적인 오류 발생", e);
            // 오류 발생 시 기본 주요 역명 반환
            stationNames.addAll(Set.of("강남", "서울역", "시청", "교대", "잠실", "신림", "홍대입구", "건대입구", "성신여대입구"));
            logger.warn("서울 API 오류로 기본 역명 사용: {}개 역명", stationNames.size());
        }
        
        return stationNames;
    }
    
    /**
     * 국토교통부 API에서 상세정보 수집 (비동기 개선)
     */
    private CompletableFuture<Map<String, List<MolitApiClient.MolitStationInfo>>> collectMolitDataAsync(Set<String> stationNames) {
        logger.info("MOLIT API 데이터 수집 시작: {} 개 역", stationNames.size());
        
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        int totalCount = stationNames.size();
        
        return Flux.fromIterable(stationNames)
            .flatMap(stationName -> 
                rateLimiter.executeLimited(
                    ReactiveRateLimiter.ApiType.MOLIT,
                    molitApiClient.getStationDetails(stationName)
                        .doOnSuccess(data -> {
                            int processed = processedCount.incrementAndGet();
                            if (data != null && !data.isEmpty()) {
                                int success = successCount.incrementAndGet();
                                logger.info("MOLIT 데이터 수집 성공: {} -> {} 개 결과 [{}/{}]", 
                                          stationName, data.size(), processed, totalCount);
                            } else {
                                logger.debug("MOLIT 데이터 없음: {} [{}/{}]", stationName, processed, totalCount);
                            }
                            
                            // 진행률 로깅 (10% 단위)
                            if (processed % Math.max(1, totalCount / 10) == 0 || processed == totalCount) {
                                double progress = (double) processed / totalCount * 100;
                                logger.info("MOLIT API 진행률: {:.1f}% ({}/{}) - 성공: {}개", 
                                          progress, processed, totalCount, successCount.get());
                            }
                        })
                        .onErrorResume(error -> {
                            int processed = processedCount.incrementAndGet();
                            logger.warn("MOLIT API 호출 실패: {} - {} [{}/{}]", 
                                      stationName, error.getMessage(), processed, totalCount);
                            return Mono.just(Collections.emptyList());
                        })
                        .map(data -> Map.entry(stationName, data))
                ), 3) // 동시 처리 수를 3개로 조정 (안정성)
            .filter(entry -> !entry.getValue().isEmpty())
            .collectMap(Map.Entry::getKey, Map.Entry::getValue)
            .doOnSuccess(result -> {
                logger.info("MOLIT 데이터 수집 완료: 성공 {}개 / 전체 {}개 역 (성공률: {:.1f}%)", 
                          result.size(), totalCount, (double) result.size() / totalCount * 100);
            })
            .doOnError(error -> {
                logger.error("MOLIT 데이터 수집 중 전체적인 오류 발생", error);
            })
            .toFuture();
    }
    
    /**
     * 데이터 정규화 및 역 그룹화
     */
    private List<StationGroup> normalizeAndGroupStations(Set<String> seoulStations, 
                                                        Map<String, List<MolitApiClient.MolitStationInfo>> molitData) {
        Map<String, StationGroup> groupMap = new HashMap<>();
        
        // 서울시 데이터 처리
        for (String stationName : seoulStations) {
            StationNameResolver.StandardizedStation standardized = 
                nameResolver.standardizeStationName(stationName, "서울특별시", null);
            
            StationGroup group = groupMap.computeIfAbsent(standardized.getCanonicalName(), 
                k -> new StationGroup(standardized));
            
            // 서울시 데이터로 역 정보 추가 (임시)
            group.addStation(createSeoulStationInfo(stationName, standardized));
        }
        
        // MOLIT 데이터 통합
        for (Map.Entry<String, List<MolitApiClient.MolitStationInfo>> entry : molitData.entrySet()) {
            for (MolitApiClient.MolitStationInfo molitStation : entry.getValue()) {
                StationNameResolver.StandardizedStation standardized = 
                    nameResolver.standardizeStationName(
                        molitStation.getStationName(), 
                        molitStation.getSidoName(), 
                        molitStation.getSggName()
                    );
                
                StationGroup group = groupMap.computeIfAbsent(standardized.getCanonicalName(), 
                    k -> new StationGroup(standardized));
                
                group.addStation(createStationInfoFromMolit(molitStation, standardized));
            }
        }
        
        return new ArrayList<>(groupMap.values());
    }
    
    /**
     * 좌표 정보 보완 (비동기 개선)
     */
    private CompletableFuture<Integer> enrichCoordinatesAsync(List<StationGroup> stationGroups) {
        logger.info("좌표 정보 보완 시작: {} 개 그룹", stationGroups.size());
        
        return Flux.fromIterable(stationGroups)
            .flatMap(group -> {
                // 기존 좌표들로 대표 좌표 결정
                List<CoordinateIntegrationService.StationCoordinate> coordinates = 
                    group.getStations().stream()
                        .filter(station -> station.getLatitude() != null && station.getLongitude() != null)
                        .map(station -> new CoordinateIntegrationService.StationCoordinate(
                            station.getLatitude(), station.getLongitude(), 
                            station.getDataSource(), station.getStationId()))
                        .collect(Collectors.toList());
                
                CoordinateIntegrationService.CoordinateResult result = 
                    coordinateService.determineGroupCoordinate(coordinates);
                
                if (result.isValid()) {
                    group.setRepresentativeCoordinate(result.getLatitude(), result.getLongitude(), result.getConfidence());
                    return Mono.just(1);
                } else {
                    // OSM으로 좌표 보완 시도 (비동기)
                    return rateLimiter.executeLimited(
                        ReactiveRateLimiter.ApiType.OPENSTREETMAP,
                        coordinateService.supplementCoordinate(
                            group.getStandardizedStation().getOriginalName(),
                            group.getStandardizedStation().getRegion(),
                            group.getStandardizedStation().getCity()
                        )
                    )
                    .map(osmResult -> {
                        if (osmResult != null && osmResult.isValid()) {
                            group.setRepresentativeCoordinate(
                                osmResult.getLatitude(), osmResult.getLongitude(), osmResult.getConfidence());
                            return 1;
                        }
                        return 0;
                    })
                    .onErrorResume(error -> {
                        logger.warn("OSM 좌표 보완 실패: {}", group.getStandardizedStation().getCanonicalName());
                        return Mono.just(0);
                    });
                }
            }, 3) // 최대 3개 동시 처리 (OpenStreetMap API 제한 고려)
            .reduce(0, Integer::sum)
            .doOnSuccess(count -> logger.info("좌표 정보 보완 완료: {} 개 그룹", count))
            .toFuture();
    }
    
    /**
     * Enhanced 데이터베이스에 저장
     */
    private void saveToEnhancedDatabase(List<StationGroup> stationGroups) {
        // TODO: Enhanced 스키마에 맞춰 저장 로직 구현
        logger.info("Enhanced 데이터베이스 저장 로직 구현 필요 (현재는 로깅만)");
        
        for (StationGroup group : stationGroups) {
            logger.debug("저장할 그룹: {}, 역 수: {}, 좌표: {},{}", 
                group.getStandardizedStation().getCanonicalName(),
                group.getStations().size(),
                group.getRepresentativeLatitude(),
                group.getRepresentativeLongitude());
        }
    }
    
    // Helper 메서드들
    private StationInfo createSeoulStationInfo(String stationName, StationNameResolver.StandardizedStation standardized) {
        StationInfo info = new StationInfo();
        info.setStationName(stationName);
        info.setDataSource("SEOUL_API");
        info.setStationId("SEOUL_" + stationName.hashCode());
        return info;
    }
    
    private StationInfo createStationInfoFromMolit(MolitApiClient.MolitStationInfo molitStation, 
                                                  StationNameResolver.StandardizedStation standardized) {
        StationInfo info = new StationInfo();
        info.setStationName(molitStation.getStationName());
        info.setLineNumber(extractLineFromRouteName(molitStation.getRouteName()));
        info.setLatitude(DataMapper.parseDouble(molitStation.getLatitude()));
        info.setLongitude(DataMapper.parseDouble(molitStation.getLongitude()));
        info.setAddress(molitStation.getRoadAddress());
        info.setDataSource("MOLIT_API");
        info.setStationId(molitStation.getStationId());
        return info;
    }
    
    private String extractLineFromRouteName(String routeName) {
        if (routeName == null) return "1";
        // "서울 1호선" -> "1" 추출
        if (routeName.contains("호선")) {
            return routeName.replaceAll(".*?(\\d+)호선.*", "$1");
        }
        return "1";
    }
    
    // DataMapper.DataMapper.parseDouble() 사용으로 중복 제거됨
    
    // 내부 클래스들
    public static class StationGroup {
        private final StationNameResolver.StandardizedStation standardizedStation;
        private final List<StationInfo> stations = new ArrayList<>();
        private Double representativeLatitude;
        private Double representativeLongitude;
        private Integer coordinateConfidence;
        
        public StationGroup(StationNameResolver.StandardizedStation standardizedStation) {
            this.standardizedStation = standardizedStation;
        }
        
        public void addStation(StationInfo station) {
            stations.add(station);
        }
        
        public void setRepresentativeCoordinate(Double lat, Double lon, Integer confidence) {
            this.representativeLatitude = lat;
            this.representativeLongitude = lon;
            this.coordinateConfidence = confidence;
        }
        
        // Getters
        public StationNameResolver.StandardizedStation getStandardizedStation() { return standardizedStation; }
        public List<StationInfo> getStations() { return stations; }
        public Double getRepresentativeLatitude() { return representativeLatitude; }
        public Double getRepresentativeLongitude() { return representativeLongitude; }
        public Integer getCoordinateConfidence() { return coordinateConfidence; }
    }
    
    public static class StationInfo implements StationNameResolver.StationInfo {
        private String stationId;
        private String stationName;
        private String lineNumber;
        private String stationCode;
        private Double latitude;
        private Double longitude;
        private String address;
        private String dataSource;
        
        // StationNameResolver.StationInfo interface 구현
        @Override
        public String getStationName() { return stationName; }
        @Override
        public String getLineNumber() { return lineNumber; }
        @Override
        public Double getLatitude() { return latitude; }
        @Override
        public Double getLongitude() { return longitude; }
        @Override
        public String getStationId() { return stationId; }
        
        // 추가 Getters and Setters
        public void setStationId(String stationId) { this.stationId = stationId; }
        public void setStationName(String stationName) { this.stationName = stationName; }
        public void setLineNumber(String lineNumber) { this.lineNumber = lineNumber; }
        public String getStationCode() { return stationCode; }
        public void setStationCode(String stationCode) { this.stationCode = stationCode; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getDataSource() { return dataSource; }
        public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    }
    
    public static class SyncResult {
        private boolean success;
        private String errorMessage;
        private int seoulStationsFound;
        private int molitStationsFound;
        private int stationGroupsCreated;
        private int coordinatesEnriched;
        
        public static SyncResult failed(String errorMessage) {
            SyncResult result = new SyncResult();
            result.success = false;
            result.errorMessage = errorMessage;
            return result;
        }
        
        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public int getSeoulStationsFound() { return seoulStationsFound; }
        public void setSeoulStationsFound(int seoulStationsFound) { this.seoulStationsFound = seoulStationsFound; }
        public int getMolitStationsFound() { return molitStationsFound; }
        public void setMolitStationsFound(int molitStationsFound) { this.molitStationsFound = molitStationsFound; }
        public int getStationGroupsCreated() { return stationGroupsCreated; }
        public void setStationGroupsCreated(int stationGroupsCreated) { this.stationGroupsCreated = stationGroupsCreated; }
        public int getCoordinatesEnriched() { return coordinatesEnriched; }
        public void setCoordinatesEnriched(int coordinatesEnriched) { this.coordinatesEnriched = coordinatesEnriched; }
        
        @Override
        public String toString() {
            return String.format("SyncResult{success=%s, seoulStations=%d, molitStations=%d, groups=%d, coordinates=%d}", 
                success, seoulStationsFound, molitStationsFound, stationGroupsCreated, coordinatesEnriched);
        }
    }
    
    /**
     * 단순화된 전체 데이터 동기화 (컨트롤러용)
     */
    @Async
    public void synchronizeAllData() {
        logger.info("전체 지하철 데이터 동기화 시작");
        performIntegratedSync().whenComplete((result, throwable) -> {
            if (throwable != null) {
                logger.error("데이터 동기화 실패", throwable);
            } else {
                logger.info("데이터 동기화 완료: {}", result);
            }
        });
    }
    
    /**
     * 좌표 보완 전용 작업 (OpenStreetMap 전용, 1초 간격 엄격 준수)
     */
    @Async
    public void supplementMissingCoordinates() {
        logger.info("=== OpenStreetMap 좌표 보완 작업 시작 ===");
        
        try {
            // 진행 상황 초기화
            coordinateProgressStatus.set("RUNNING");
            coordinateProgressCurrent.set(0);
            coordinateProgressSuccess.set(0);
            coordinateProgressFailed.set(0);
            coordinateProgressCurrentStation.set("");
            
            // 좌표가 없는 역들 조회
            List<com.example.transportationserver.model.SubwayStation> stationsWithoutCoords = 
                stationService.getStationsWithoutCoordinates();
            
            int totalStations = stationsWithoutCoords.size();
            coordinateProgressTotal.set(totalStations);
            
            logger.info("좌표가 없는 역 수: {}개", totalStations);
            
            if (stationsWithoutCoords.isEmpty()) {
                logger.info("모든 역에 좌표가 이미 존재합니다.");
                coordinateProgressStatus.set("COMPLETED");
                return;
            }
            
            int successCount = 0;
            int skipCount = 0;
            
            logger.info("OpenStreetMap API 정책 준수: 1초당 1회 요청, 예상 소요 시간: {}분", 
                Math.ceil(totalStations / 60.0));
            
            for (int i = 0; i < stationsWithoutCoords.size(); i++) {
                com.example.transportationserver.model.SubwayStation station = stationsWithoutCoords.get(i);
                
                try {
                    // 진행 상황 업데이트
                    coordinateProgressCurrent.set(i + 1);
                    coordinateProgressCurrentStation.set(station.getName() + " (" + station.getLineNumber() + ")");
                    
                    logger.info("[{}/{}] 좌표 보완 시도: {} ({}호선)", 
                        i + 1, totalStations, station.getName(), station.getLineNumber());
                    
                    long startTime = System.currentTimeMillis();
                    
                    // OpenStreetMap에서 좌표 검색 (내부에서 1초 간격 자동 관리)
                    Optional<OpenStreetMapService.Coordinate> coordinate = 
                        openStreetMapService.searchStationCoordinates(
                            station.getName(), 
                            station.getRegion() != null ? station.getRegion() : "서울특별시"
                        ).block();
                    
                    if (coordinate.isPresent()) {
                        OpenStreetMapService.Coordinate coord = coordinate.get();
                        
                        // 데이터베이스에 좌표 업데이트
                        boolean updated = stationService.updateStationCoordinates(
                            station.getId(), coord.getLatitude(), coord.getLongitude());
                        
                        if (updated) {
                            successCount++;
                            coordinateProgressSuccess.incrementAndGet();
                            logger.info("✅ 좌표 보완 성공: {} -> ({:.6f}, {:.6f})", 
                                station.getName(), coord.getLatitude(), coord.getLongitude());
                        } else {
                            skipCount++;
                            coordinateProgressFailed.incrementAndGet();
                            logger.warn("❌ 좌표 업데이트 실패: {}", station.getName());
                        }
                    } else {
                        skipCount++;
                        coordinateProgressFailed.incrementAndGet();
                        logger.warn("⚠️ 좌표를 찾을 수 없음: {}", station.getName());
                    }
                    
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    logger.debug("요청 처리 시간: {}ms", elapsedTime);
                    
                    // 진행률 표시 (10개마다)
                    if ((i + 1) % 10 == 0 || i == totalStations - 1) {
                        double progress = ((double)(i + 1) / totalStations) * 100;
                        logger.info("📊 진행률: {:.1f}% ({}/{}) - 성공: {}, 실패: {}", 
                            progress, i + 1, totalStations, successCount, skipCount);
                    }
                    
                } catch (Exception e) {
                    skipCount++;
                    coordinateProgressFailed.incrementAndGet();
                    logger.error("❌ 좌표 보완 실패: {} - {}", station.getName(), e.getMessage());
                    
                    // 에러 발생 시에도 API 정책 준수
                    rateLimitService.waitForOpenStreetMap();
                }
            }
            
            // 작업 완료 상태 업데이트
            coordinateProgressStatus.set("COMPLETED");
            coordinateProgressCurrentStation.set("");
            
            logger.info("=== 좌표 보완 작업 완료 ===");
            logger.info("🎯 처리 결과: 전체 {}개, 성공 {}개, 실패/스킵 {}개", 
                totalStations, successCount, skipCount);
            logger.info("📈 성공률: {:.1f}%", 
                totalStations > 0 ? (double)successCount / totalStations * 100 : 0);
            
            // 최종 통계 출력
            SubwayStationService.CoordinateStatistics finalStats = stationService.getCoordinateStatistics();
            logger.info("📊 최종 좌표 완성률: {:.1f}% ({}/{})", 
                finalStats.getCompletionRate(), 
                finalStats.getHasCoordinates(), 
                finalStats.getTotal());
                
        } catch (Exception e) {
            coordinateProgressStatus.set("ERROR");
            logger.error("좌표 보완 작업 중 치명적 오류 발생", e);
        }
    }
    
    /**
     * 좌표 보완 진행 상황 조회
     */
    public Map<String, Object> getCoordinateSupplementProgress() {
        Map<String, Object> progress = new HashMap<>();
        
        progress.put("status", coordinateProgressStatus.get());
        progress.put("total", coordinateProgressTotal.get());
        progress.put("current", coordinateProgressCurrent.get());
        progress.put("success", coordinateProgressSuccess.get());
        progress.put("failed", coordinateProgressFailed.get());
        progress.put("currentStation", coordinateProgressCurrentStation.get());
        
        int total = coordinateProgressTotal.get();
        int current = coordinateProgressCurrent.get();
        
        if (total > 0) {
            double progressPercent = ((double) current / total) * 100;
            progress.put("progressPercent", Math.round(progressPercent * 10.0) / 10.0);
            
            // 예상 남은 시간 계산 (1초당 1개 처리)
            int remaining = total - current;
            if (remaining > 0 && !"IDLE".equals(coordinateProgressStatus.get())) {
                progress.put("estimatedRemainingMinutes", Math.ceil(remaining / 60.0));
            }
        } else {
            progress.put("progressPercent", 0.0);
        }
        
        progress.put("timestamp", java.time.LocalDateTime.now().toString());
        
        return progress;
    }
    
    /**
     * 시스템 상태 정보 반환
     */
    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // API 설정 상태
        status.put("seoulApiConfigured", apiKey != null && !apiKey.isEmpty());
        status.put("molitApiConfigured", molitServiceKey != null && !molitServiceKey.isEmpty());
        
        // 서비스 상태
        status.put("services", Map.of(
            "seoulApiClient", "READY",
            "molitApiClient", "READY", 
            "coordinateService", "READY",
            "nameResolver", "READY"
        ));
        
        // API 키 정보 (마스킹)
        if (apiKey != null && !apiKey.isEmpty()) {
            status.put("seoulApiKeyLength", apiKey.length());
            status.put("seoulApiKeyPreview", maskApiKey(apiKey));
        }
        
        if (molitServiceKey != null && !molitServiceKey.isEmpty()) {
            status.put("molitApiKeyLength", molitServiceKey.length());
            status.put("molitApiKeyPreview", maskApiKey(molitServiceKey));
        }
        
        // 데이터베이스 연결 상태 (향후 추가)
        status.put("databaseStatus", "TODO: 데이터베이스 상태 확인 로직 추가");
        
        return status;
    }
    
    /**
     * API 키 마스킹
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 10) {
            return "***";
        }
        
        if (apiKey.length() > 20) {
            return apiKey.substring(0, 5) + "..." + apiKey.substring(apiKey.length() - 5);
        } else {
            return apiKey.substring(0, 3) + "..." + apiKey.substring(apiKey.length() - 3);
        }
    }
}