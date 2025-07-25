package com.example.transportationserver.controller;

import com.example.transportationserver.dto.NextTrainDto;
import com.example.transportationserver.dto.StandardApiResponse;
import com.example.transportationserver.dto.SubwayScheduleApiDto;
import com.example.transportationserver.model.SubwayStation;
import com.example.transportationserver.service.KoreanSubwayApiClient;
import com.example.transportationserver.service.SubwayStationService;
import com.example.transportationserver.service.SubwayDataSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subway")
@Tag(name = "지하철 정보 API", description = "Flutter 앱용 실시간 지하철 정보 및 검색 API를 제공합니다")
public class SubwayApiController {
    
    @Autowired
    private SubwayStationService stationService;
    
    @Autowired
    private KoreanSubwayApiClient apiClient;
    
    @Autowired
    private SubwayDataSyncService syncService;
    
    @Operation(summary = "역 이름으로 검색", description = "입력된 이름으로 지하철역을 검색합니다 (부분 일치 지원)")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "검색 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 요청 파라미터")
    })
    @GetMapping("/search")
    public ResponseEntity<StandardApiResponse<List<SubwayStation>>> searchStations(
            @Parameter(description = "검색할 역 이름", required = true, example = "강남")
            @RequestParam String name) {
        List<SubwayStation> stations = stationService.searchStationsByName(name);
        return ResponseEntity.ok(StandardApiResponse.successWithCount(stations, "지하철역 검색 결과", stations.size()));
    }
    
    @Operation(summary = "주변 지하철역 검색", description = "현재 위치 기준으로 지정된 반경 내 지하철역을 검색합니다")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "검색 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 좌표 정보")
    })
    @GetMapping("/nearby")
    public ResponseEntity<StandardApiResponse<List<SubwayStation>>> getNearbyStations(
            @Parameter(description = "위도", required = true, example = "37.5665")
            @RequestParam Double lat,
            @Parameter(description = "경도", required = true, example = "126.9780")
            @RequestParam Double lng,
            @Parameter(description = "검색 반경 (km)", example = "1.0")
            @RequestParam(defaultValue = "1.0") Double radius) {
        List<SubwayStation> stations = stationService.getNearbyStations(lat, lng, radius);
        return ResponseEntity.ok(StandardApiResponse.successWithCount(stations, "주변 지하철역 조회 성공", stations.size()));
    }
    
    @Operation(summary = "실시간 지하철 도착정보", description = "지정된 역의 실시간 지하철 도착 정보를 조회합니다")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "404", description = "해당 역의 정보를 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "외부 API 호출 실패")
    })
    @GetMapping("/stations/{stationName}/arrivals")
    public Mono<ResponseEntity<List<NextTrainDto>>> getRealTimeArrivals(
            @Parameter(description = "지하철역 이름", required = true, example = "강남")
            @PathVariable String stationName) {
        return apiClient.getRealTimeArrival(stationName)
                .map(arrivals -> ResponseEntity.ok(arrivals))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    /**
     * Flutter 앱용 - 역 시간표 조회
     */
    @GetMapping("/stations/{stationName}/schedules")
    public Mono<ResponseEntity<Map<String, Object>>> getStationSchedules(
            @PathVariable String stationName,
            @RequestParam(defaultValue = "1") String dayType,
            @RequestParam(defaultValue = "1") String inOutTag) {
        
        return apiClient.getStationSchedule(stationName, dayType, inOutTag)
                .map(schedules -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("stationName", stationName);
                    response.put("dayType", getDayTypeLabel(dayType));
                    response.put("direction", getDirectionLabel(inOutTag));
                    response.put("schedules", schedules);
                    response.put("totalCount", schedules.size());
                    
                    return ResponseEntity.ok(response);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
    
    /**
     * Flutter 앱용 - 노선별 역 목록
     */
    @GetMapping("/lines/{lineNumber}/stations")
    public ResponseEntity<StandardApiResponse<List<SubwayStation>>> getStationsByLine(@PathVariable String lineNumber) {
        List<SubwayStation> stations = stationService.getStationsByLine(lineNumber);
        return ResponseEntity.ok(StandardApiResponse.successWithCount(stations, lineNumber + "호선 지하철역 목록 조회 성공", stations.size()));
    }
    
    /**
     * Flutter 앱용 - 즐겨찾기용 역 상세 정보
     */
    @GetMapping("/stations/{id}/detail")
    public ResponseEntity<Map<String, Object>> getStationDetail(@PathVariable Long id) {
        try {
            SubwayStation station = stationService.getStationById(id);
            
            Map<String, Object> detail = new HashMap<>();
            detail.put("station", station);
            detail.put("hasSchedule", true);
            detail.put("hasRealTimeInfo", true);
            detail.put("supportedServices", List.of("schedule", "realtime", "nearby"));
            
            return ResponseEntity.ok(detail);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 관리자용 - 데이터 동기화 트리거
     */
    @PostMapping("/admin/sync")
    public ResponseEntity<Map<String, String>> triggerDataSync() {
        syncService.triggerFullSync();
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "started");
        response.put("message", "Data synchronization has been triggered");
        
        return ResponseEntity.ok(response);
    }
    
    @Operation(summary = "API 상태 확인", description = "서버 및 데이터베이스 연결 상태를 확인합니다")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "상태 확인 완료")
    })
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "Transportation Server");
        health.put("timestamp", System.currentTimeMillis());
        
        try {
            long stationCount = stationService.getAllStations().size();
            health.put("stationCount", stationCount);
            health.put("database", "connected");
        } catch (Exception e) {
            health.put("database", "error");
            health.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(health);
    }
    
    // Helper methods
    private String getDayTypeLabel(String dayType) {
        switch (dayType) {
            case "1": return "weekday";
            case "2": return "saturday";
            case "3": return "sunday";
            default: return "unknown";
        }
    }
    
    private String getDirectionLabel(String inOutTag) {
        switch (inOutTag) {
            case "1": return "inbound";
            case "2": return "outbound";
            default: return "unknown";
        }
    }
}