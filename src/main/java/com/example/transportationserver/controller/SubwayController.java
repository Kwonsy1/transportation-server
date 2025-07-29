package com.example.transportationserver.controller;

import com.example.transportationserver.dto.StandardApiResponse;
import com.example.transportationserver.model.SubwayStation;
import com.example.transportationserver.service.IntegratedSubwayDataService;
import com.example.transportationserver.service.MolitApiClient;
import com.example.transportationserver.service.MolitApiClient.MolitStationInfo;
import com.example.transportationserver.service.SubwayStationService;
import com.example.transportationserver.service.OpenStreetMapService;
import com.example.transportationserver.service.BatchCoordinateService;
import com.example.transportationserver.service.StreamingStationService;
import com.example.transportationserver.util.ErrorHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 통합 지하철 API 컨트롤러
 * 기존의 여러 컨트롤러를 하나로 통합
 */
@RestController
@RequestMapping("/api/subway")
@CrossOrigin(origins = "*", maxAge = 3600, allowedHeaders = "*")
public class SubwayController {

    private static final Logger logger = LoggerFactory.getLogger(SubwayController.class);

    @Autowired
    private IntegratedSubwayDataService integratedService;
    
    @Autowired
    private MolitApiClient molitApiClient;
    
    @Autowired
    private SubwayStationService stationService;
    
    @Autowired
    private OpenStreetMapService openStreetMapService;
    
    @Autowired
    private BatchCoordinateService batchCoordinateService;
    
    @Autowired
    private StreamingStationService streamingStationService;

    // === 데이터 동기화 === //
    
    @PostMapping("/sync/full")
    @Operation(
        summary = "전체 지하철 데이터 동기화", 
        description = "다층 데이터 수집 프로세스 시작",
        tags = {"1. 데이터 동기화 (외부 → DB)"}
    )
    public ResponseEntity<StandardApiResponse<String>> syncAllData() {
        try {
            integratedService.synchronizeAllData();
            return ResponseEntity.ok(StandardApiResponse.success(
                "전체 데이터 동기화가 시작되었습니다.", 
                "백그라운드에서 처리됩니다. 로그를 확인해주세요."
            ));
        } catch (Exception e) {
            ErrorHandler.logAndHandle(logger, "데이터 동기화 시작", e);
            return ErrorHandler.createErrorFromException(e, "데이터 동기화");
        }
    }


    // === 역 검색 (데이터베이스 기반) === //
    
    @GetMapping("/stations/search")
    @Operation(
        summary = "역명 검색 (로컬 DB)",
        description = "역명으로 지하철역 정보 검색 (로컬 데이터베이스 사용)",
        tags = {"2. 클라이언트 API (DB → 클라이언트)"}
    )
    public ResponseEntity<StandardApiResponse<List<SubwayStation>>> searchStations(
            @Parameter(description = "검색할 역명", required = true)
            @RequestParam String stationName) {
        
        logger.info("DB 역명 검색 요청: {}", stationName);
        
        return ErrorHandler.handleListWithTryCatch(
            () -> {
                List<SubwayStation> stations = stationService.searchStationsByName(stationName);
                logger.info("{} DB 검색 결과: {}개 역", stationName, stations.size());
                
                if (!stations.isEmpty()) {
                    logger.info("첫 번째 결과: {} ({}호선)", 
                        stations.get(0).getName(), stations.get(0).getLineNumber());
                }
                
                return stations;
            },
            "DB 역명 검색 (" + stationName + ")",
            logger
        );
    }
    
    @GetMapping("/stations/search-external")
    @Operation(
        summary = "역명 검색 (외부 API)",
        description = "역명으로 지하철역 정보 검색 (MOLIT API 사용)",
        tags = {"1. 데이터 동기화 (외부 → DB)"}
    )
    public Mono<ResponseEntity<StandardApiResponse<List<MolitStationInfo>>>> searchStationsExternal(
            @Parameter(description = "검색할 역명", required = true)
            @RequestParam String stationName) {
        
        logger.info("외부 API 역명 검색 요청: {}", stationName);
        
        return molitApiClient.getStationDetails(stationName)
            .map(stations -> {
                logger.info("{} 외부 API 검색 결과: {}개 역", stationName, stations.size());
                if (!stations.isEmpty()) {
                    logger.info("첫 번째 결과: {}", stations.get(0).getStationName());
                }
                return ResponseEntity.ok(StandardApiResponse.successWithCount(
                    stations, 
                    stations.size() + "개 역 검색 완료 (외부 API)",
                    stations.size()
                ));
            })
            .onErrorResume(error -> {
                logger.error("{} 외부 API 검색 실패: {}", stationName, error.getMessage());
                return Mono.just(ResponseEntity.internalServerError()
                    .body(StandardApiResponse.error("역 검색 실패: " + error.getMessage(), 500)));
            });
    }

    @GetMapping("/lines/{lineNumber}/stations")
    @Operation(
        summary = "노선별 역 목록 (로컬 DB)",
        description = "특정 노선의 모든 역 목록 조회 (로컬 데이터베이스 사용)",
        tags = {"2. 클라이언트 API (DB → 클라이언트)"}
    )
    public ResponseEntity<StandardApiResponse<List<SubwayStation>>> getStationsByLine(
            @Parameter(description = "노선번호 (예: 01호선, 02호선, 경의선)", required = true)
            @PathVariable String lineNumber) {
        
        logger.info("DB 노선별 역 조회 요청: {}", lineNumber);
        
        try {
            List<SubwayStation> stations = stationService.getStationsByLine(lineNumber);
            logger.info("{} DB 조회 결과: {}개 역", lineNumber, stations.size());
            
            return ResponseEntity.ok(StandardApiResponse.successWithCount(
                stations,
                lineNumber + " " + stations.size() + "개 역 조회 완료 (로컬 DB)",
                stations.size()
            ));
        } catch (Exception e) {
            logger.error("{} DB 조회 실패: {}", lineNumber, e.getMessage());
            return ResponseEntity.internalServerError()
                .body(StandardApiResponse.error("노선별 역 조회 실패: " + e.getMessage(), 500));
        }
    }
    
    @GetMapping("/lines/{lineNumber}/stations-external")
    @Operation(
        summary = "노선별 역 목록 (외부 API)",
        description = "특정 노선의 모든 역 목록 조회 (MOLIT API 사용)",
        tags = {"1. 데이터 동기화 (외부 → DB)"}
    )
    public Mono<ResponseEntity<StandardApiResponse<List<MolitStationInfo>>>> getStationsByLineExternal(
            @Parameter(description = "노선번호 (예: 1, 2, 경의중앙)", required = true)
            @PathVariable String lineNumber) {
        
        logger.info("외부 API 노선별 역 조회 요청: {}호선", lineNumber);
        
        return molitApiClient.getStationsByLine(lineNumber)
            .map(stations -> {
                logger.info("{}호선 외부 API 조회 결과: {}개 역", lineNumber, stations.size());
                return ResponseEntity.ok(StandardApiResponse.successWithCount(
                    stations,
                    lineNumber + "호선 " + stations.size() + "개 역 조회 완료 (외부 API)",
                    stations.size()
                ));
            })
            .onErrorResume(error -> {
                logger.error("{}호선 외부 API 조회 실패: {}", lineNumber, error.getMessage());
                return Mono.just(ResponseEntity.internalServerError()
                    .body(StandardApiResponse.error("노선별 역 조회 실패: " + error.getMessage(), 500)));
            });
    }

    // === 상태 확인 === //
    
    @GetMapping("/status")
    @Operation(
        summary = "시스템 상태 확인",
        description = "지하철 API 시스템의 현재 상태 및 설정 확인",
        tags = {"3. 기본 유틸리티"}
    )
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> getSystemStatus() {
        try {
            Map<String, Object> status = integratedService.getSystemStatus();
            return ResponseEntity.ok(StandardApiResponse.success(status, "시스템 상태 정상"));
        } catch (Exception e) {
            logger.error("시스템 상태 확인 실패", e);
            
            // 기본 상태 정보 반환
            Map<String, Object> fallbackStatus = new HashMap<>();
            fallbackStatus.put("status", "ERROR");
            fallbackStatus.put("error", e.getMessage());
            
            return ResponseEntity.internalServerError()
                .body(StandardApiResponse.error("상태 확인 실패", 500));
        }
    }

    @GetMapping("/health")
    @Operation(
        summary = "헬스체크",
        description = "간단한 헬스체크 엔드포인트",
        tags = {"3. 기본 유틸리티"}
    )
    public ResponseEntity<StandardApiResponse<String>> healthCheck() {
        return ResponseEntity.ok(StandardApiResponse.success(
            "OK", 
            "지하철 API 서비스가 정상 작동 중입니다."
        ));
    }

    @GetMapping("/test")
    @Operation(
        summary = "간단한 테스트",
        description = "서버가 정상 작동하는지 확인하는 간단한 테스트",
        tags = {"4. 테스트 및 디버깅"}
    )
    public ResponseEntity<Map<String, String>> simpleTest() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "OK");
        response.put("message", "SubwayController is working");
        response.put("timestamp", java.time.LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/debug/api-test")
    @Operation(
        summary = "API 연결 테스트",
        description = "MOLIT API 연결 상태를 실제로 테스트",
        tags = {"4. 테스트 및 디버깅"}
    )
    public Mono<ResponseEntity<Map<String, Object>>> testApiConnection() {
        logger.info("API 연결 테스트 시작");
        
        return molitApiClient.getStationDetails("강남")
            .map(stations -> {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "SUCCESS");
                response.put("testStation", "강남");
                response.put("resultCount", stations.size());
                response.put("timestamp", java.time.LocalDateTime.now().toString());
                
                if (!stations.isEmpty()) {
                    response.put("firstResult", Map.of(
                        "stationName", stations.get(0).getStationName(),
                        "routeName", stations.get(0).getRouteName(),
                        "sidoName", stations.get(0).getSidoName()
                    ));
                }
                
                logger.info("API 테스트 성공: {}개 결과", stations.size());
                return ResponseEntity.ok(response);
            })
            .onErrorResume(error -> {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "ERROR");
                response.put("error", error.getMessage());
                response.put("testStation", "강남");
                response.put("timestamp", java.time.LocalDateTime.now().toString());
                
                logger.error("API 테스트 실패: {}", error.getMessage());
                return Mono.just(ResponseEntity.ok(response));
            });
    }
    
    @GetMapping("/db/test")
    @Operation(
        summary = "데이터베이스 테스트",
        description = "로컬 데이터베이스 연결 및 데이터 확인",
        tags = {"4. 테스트 및 디버깅"}
    )
    public ResponseEntity<Map<String, Object>> testDatabase() {
        logger.info("데이터베이스 테스트 시작");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 전체 역 수 확인
            List<SubwayStation> allStations = stationService.getAllStations();
            response.put("totalStations", allStations.size());
            
            // 강남역 검색 테스트
            List<SubwayStation> gangnamStations = stationService.searchStationsByName("강남");
            response.put("gangnamResults", gangnamStations.size());
            
            // 1호선 역 수 확인
            List<SubwayStation> line1Stations = stationService.getStationsByLine("01호선");
            response.put("line1Stations", line1Stations.size());
            
            // 2호선 역 수 확인
            List<SubwayStation> line2Stations = stationService.getStationsByLine("02호선");
            response.put("line2Stations", line2Stations.size());
            
            // 샘플 데이터
            if (!allStations.isEmpty()) {
                SubwayStation sample = allStations.get(0);
                response.put("sampleStation", Map.of(
                    "name", sample.getName(),
                    "lineNumber", sample.getLineNumber(),
                    "stationCode", sample.getStationCode() != null ? sample.getStationCode() : "N/A"
                ));
            }
            
            response.put("status", "SUCCESS");
            response.put("message", "데이터베이스 연결 성공");
            response.put("timestamp", java.time.LocalDateTime.now().toString());
            
            logger.info("DB 테스트 성공 - 총 {}개 역, 강남 {}개, 1호선 {}개, 2호선 {}개", 
                allStations.size(), gangnamStations.size(), line1Stations.size(), line2Stations.size());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "ERROR");
            response.put("error", e.getMessage());
            response.put("timestamp", java.time.LocalDateTime.now().toString());
            
            logger.error("DB 테스트 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // === 좌표 보완 기능 === //

    @GetMapping("/stations/missing-coordinates")
    @Operation(
        summary = "좌표가 없는 역 목록 조회",
        description = "좌표 정보가 없는 지하철역들의 목록을 조회",
        tags = {"2. 클라이언트 API (DB → 클라이언트)"}
    )
    public ResponseEntity<StandardApiResponse<List<SubwayStation>>> getStationsWithoutCoordinates() {
        try {
            List<SubwayStation> stations = stationService.getStationsWithoutCoordinates();
            logger.info("좌표 없는 역 조회: {}개", stations.size());
            
            return ResponseEntity.ok(StandardApiResponse.successWithCount(
                stations,
                stations.size() + "개 역의 좌표가 없습니다",
                stations.size()
            ));
        } catch (Exception e) {
            logger.error("좌표 없는 역 조회 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(StandardApiResponse.error("조회 실패: " + e.getMessage(), 500));
        }
    }

    @GetMapping("/coordinates/statistics")
    @Operation(
        summary = "좌표 완성도 통계",
        description = "전체 역 대비 좌표 보유 현황 통계",
        tags = {"2. 클라이언트 API (DB → 클라이언트)"}
    )
    public ResponseEntity<StandardApiResponse<SubwayStationService.CoordinateStatistics>> getCoordinateStatistics() {
        try {
            SubwayStationService.CoordinateStatistics stats = stationService.getCoordinateStatistics();
            logger.info("좌표 통계: 전체 {}개, 완성 {}개, 누락 {}개 (완성률: {:.1f}%)", 
                stats.getTotal(), stats.getHasCoordinates(), stats.getMissingCoordinates(), stats.getCompletionRate());
            
            return ResponseEntity.ok(StandardApiResponse.success(
                stats,
                String.format("좌표 완성률: %.1f%%", stats.getCompletionRate())
            ));
        } catch (Exception e) {
            logger.error("좌표 통계 조회 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(StandardApiResponse.error("통계 조회 실패: " + e.getMessage(), 500));
        }
    }

    @PostMapping("/coordinates/supplement")
    @Operation(
        summary = "좌표 없는 역 좌표 보완",
        description = "좌표가 없는 역들만 대상으로 OpenStreetMap API에서 좌표를 검색하여 DB에 저장 (1초 간격 준수)",
        tags = {"1. 데이터 동기화 (외부 → DB)"}
    )
    public ResponseEntity<StandardApiResponse<String>> supplementCoordinates() {
        try {
            integratedService.supplementMissingCoordinates();
            return ResponseEntity.ok(StandardApiResponse.success(
                "좌표 보완 작업이 시작되었습니다",
                "백그라운드에서 처리됩니다. 진행 상황은 로그를 확인해주세요."
            ));
        } catch (Exception e) {
            logger.error("좌표 보완 시작 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(StandardApiResponse.error("좌표 보완 실패: " + e.getMessage(), 500));
        }
    }

    // === OpenStreetMap 테스트 API === //

    @GetMapping("/debug/osm-test")
    @Operation(
        summary = "OpenStreetMap API 테스트",
        description = "지정된 역명으로 OpenStreetMap API 연동 테스트",
        tags = {"4. 테스트 및 디버깅"}
    )
    public Mono<ResponseEntity<Map<String, Object>>> testOpenStreetMapApi(
            @RequestParam(defaultValue = "강남") String stationName,
            @RequestParam(defaultValue = "서울특별시") String region) {
        
        logger.info("OpenStreetMap API 테스트 시작: {} ({})", stationName, region);
        
        return openStreetMapService.searchStationCoordinates(stationName, region)
            .map(coordinate -> {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "SUCCESS");
                response.put("testStation", stationName);
                response.put("region", region);
                response.put("timestamp", java.time.LocalDateTime.now().toString());
                
                if (coordinate.isPresent()) {
                    OpenStreetMapService.Coordinate coord = coordinate.get();
                    response.put("found", true);
                    response.put("coordinate", Map.of(
                        "latitude", coord.getLatitude(),
                        "longitude", coord.getLongitude(),
                        "source", coord.getSource()
                    ));
                    logger.info("OSM 테스트 성공: {} -> {}°, {}°", 
                        stationName, coord.getLatitude(), coord.getLongitude());
                } else {
                    response.put("found", false);
                    response.put("message", "좌표를 찾을 수 없습니다");
                    logger.info("OSM 테스트: {} 좌표 없음", stationName);
                }
                
                return ResponseEntity.ok(response);
            })
            .onErrorResume(error -> {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "ERROR");
                response.put("testStation", stationName);
                response.put("region", region);
                response.put("error", error.getMessage());
                response.put("timestamp", java.time.LocalDateTime.now().toString());
                
                logger.error("OSM API 테스트 실패: {} - {}", stationName, error.getMessage());
                return Mono.just(ResponseEntity.ok(response));
            });
    }

    @GetMapping("/debug/coordinate-progress")
    @Operation(
        summary = "좌표 보완 진행 상황 확인",
        description = "현재 좌표 보완 작업의 실시간 진행 상황 및 통계 확인",
        tags = {"4. 테스트 및 디버깅"}
    )
    public ResponseEntity<Map<String, Object>> getCoordinateProgress() {
        try {
            // 실시간 진행 상황 조회
            Map<String, Object> progress = integratedService.getCoordinateSupplementProgress();
            
            // 전체 좌표 통계 추가
            SubwayStationService.CoordinateStatistics stats = stationService.getCoordinateStatistics();
            progress.put("overallStatistics", Map.of(
                "total", stats.getTotal(),
                "hasCoordinates", stats.getHasCoordinates(),
                "missingCoordinates", stats.getMissingCoordinates(),
                "completionRate", String.format("%.1f%%", stats.getCompletionRate())
            ));
            
            // 현재 진행 중인 작업이 없을 때 샘플 데이터 제공
            String status = (String) progress.get("status");
            if ("IDLE".equals(status)) {
                List<SubwayStation> missingStations = stationService.getStationsWithoutCoordinates();
                List<Map<String, String>> samples = missingStations.stream()
                    .limit(5)
                    .map(station -> Map.of(
                        "name", station.getName(),
                        "lineNumber", station.getLineNumber() != null ? station.getLineNumber() : "N/A",
                        "region", station.getRegion() != null ? station.getRegion() : "N/A"
                    ))
                    .collect(java.util.stream.Collectors.toList());
                
                progress.put("missingSamples", samples);
                progress.put("note", "OpenStreetMap API 연동: 1초당 1회 요청 제한 준수");
            }
            
            logger.debug("좌표 보완 진행 상황: 상태={}, 진행률={}%", 
                status, progress.get("progressPercent"));
            
            return ResponseEntity.ok(progress);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "ERROR");
            error.put("error", e.getMessage());
            error.put("timestamp", java.time.LocalDateTime.now().toString());
            
            logger.error("좌표 진행 상황 조회 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    // === 메모리 효율적인 스트리밍 API === //
    
    @PostMapping("/coordinates/supplement-streaming")
    @Operation(
        summary = "스트리밍 방식 좌표 보완",
        description = "메모리 효율적인 스트리밍 방식으로 좌표 보완 작업 수행",
        tags = {"1. 데이터 동기화 (외부 → DB)"}
    )
    public ResponseEntity<StandardApiResponse<String>> supplementCoordinatesStreaming() {
        try {
            batchCoordinateService.supplementCoordinatesStreaming();
            
            return ResponseEntity.ok(StandardApiResponse.success(
                "STARTED", 
                "스트리밍 방식 좌표 보완 작업이 시작되었습니다. /api/subway/coordinates/progress 에서 진행 상황을 확인하세요."
            ));
        } catch (Exception e) {
            logger.error("스트리밍 좌표 보완 시작 실패", e);
            return ResponseEntity.internalServerError()
                .body(StandardApiResponse.error("스트리밍 좌표 보완 시작 실패: " + e.getMessage(), 500));
        }
    }
    
    @GetMapping("/memory/status")
    @Operation(
        summary = "메모리 사용량 확인",
        description = "현재 JVM 메모리 사용량 및 상태 확인",
        tags = {"3. 기본 유틸리티"}
    )
    public ResponseEntity<Map<String, Object>> getMemoryStatus() {
        try {
            Runtime runtime = Runtime.getRuntime();
            Map<String, Object> memoryInfo = new HashMap<>();
            
            long maxMemory = runtime.maxMemory();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;
            
            memoryInfo.put("maxMemoryMB", maxMemory / 1024 / 1024);
            memoryInfo.put("totalMemoryMB", totalMemory / 1024 / 1024);
            memoryInfo.put("usedMemoryMB", usedMemory / 1024 / 1024);
            memoryInfo.put("freeMemoryMB", freeMemory / 1024 / 1024);
            memoryInfo.put("usagePercentage", Math.round(((double) usedMemory / maxMemory) * 100 * 100.0) / 100.0);
            
            // 메모리 사용량에 따른 상태 판단
            double usagePercentage = ((double) usedMemory / maxMemory) * 100;
            String status;
            if (usagePercentage < 50) {
                status = "GOOD";
            } else if (usagePercentage < 80) {
                status = "WARNING";
            } else {
                status = "CRITICAL";
            }
            memoryInfo.put("status", status);
            
            // GC 실행 가능성 제안
            if (usagePercentage > 70) {
                memoryInfo.put("recommendation", "높은 메모리 사용량으로 인해 GC 실행을 권장합니다.");
            }
            
            return ResponseEntity.ok(memoryInfo);
        } catch (Exception e) {
            logger.error("메모리 상태 확인 실패", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
    
    @PostMapping("/memory/gc")
    @Operation(
        summary = "가비지 컬렉션 실행",
        description = "System.gc()를 호출하여 메모리 정리 시도 (권장사항일 뿐, 강제 실행은 아님)",
        tags = {"3. 기본 유틸리티"}
    )
    public ResponseEntity<StandardApiResponse<String>> triggerGarbageCollection() {
        try {
            Runtime runtime = Runtime.getRuntime();
            long beforeGC = runtime.totalMemory() - runtime.freeMemory();
            
            System.gc();
            
            // GC 후 잠깐 대기
            Thread.sleep(100);
            
            long afterGC = runtime.totalMemory() - runtime.freeMemory();
            long freed = beforeGC - afterGC;
            
            String message = String.format("GC 실행 완료. 해제된 메모리: %.2f MB", 
                freed / 1024.0 / 1024.0);
            
            return ResponseEntity.ok(StandardApiResponse.success("COMPLETED", message));
        } catch (Exception e) {
            logger.error("GC 실행 실패", e);
            return ResponseEntity.internalServerError()
                .body(StandardApiResponse.error("GC 실행 실패: " + e.getMessage(), 500));
        }
    }
    
    @GetMapping("/performance/batch-progress")
    @Operation(
        summary = "배치 처리 진행 상황",
        description = "현재 실행 중인 배치 작업의 진행 상황 확인",
        tags = {"1. 데이터 동기화 (외부 → DB)"}
    )
    public ResponseEntity<Map<String, Object>> getBatchProgress() {
        try {
            BatchCoordinateService.CoordinateProgress progress = batchCoordinateService.getProgress();
            
            Map<String, Object> response = new HashMap<>();
            response.put("total", progress.total);
            response.put("current", progress.current);
            response.put("success", progress.success);
            response.put("failure", progress.failure);
            response.put("progressPercentage", progress.progressPercentage);
            response.put("timestamp", java.time.LocalDateTime.now().toString());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("배치 진행 상황 조회 실패", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }
}