package com.example.transportationserver.controller;

import com.example.transportationserver.service.MolitApiClient;
import com.example.transportationserver.service.MolitApiClient.MolitStationInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test/molit")
@Tag(name = "MOLIT API Test", description = "국토교통부 API 테스트용 엔드포인트")
public class MolitApiTestController {

    private static final Logger logger = LoggerFactory.getLogger(MolitApiTestController.class);

    @Autowired
    private MolitApiClient molitApiClient;

    @Value("${api.molit.service.key:}")
    private String serviceKey;

    @GetMapping("/station/{stationName}")
    @Operation(
        summary = "역명으로 MOLIT API 테스트",
        description = "지정된 역명으로 국토교통부 API를 직접 호출하여 결과를 확인합니다."
    )
    public Mono<ResponseEntity<Map<String, Object>>> testStationSearch(
            @Parameter(description = "검색할 역명", required = true, example = "강남")
            @PathVariable String stationName) {
        
        logger.info("=== MOLIT API 테스트 시작 ===");
        logger.info("검색 역명: {}", stationName);
        logger.info("Service Key 상태: {}", serviceKey != null && !serviceKey.isEmpty() ? "설정됨 (길이: " + serviceKey.length() + ")" : "설정되지 않음");
        
        if (serviceKey != null && !serviceKey.isEmpty()) {
            // 키의 처음 10자와 마지막 10자만 로깅 (보안)
            String maskedKey = serviceKey.length() > 20 ? 
                serviceKey.substring(0, 10) + "..." + serviceKey.substring(serviceKey.length() - 10) : 
                serviceKey.substring(0, Math.min(10, serviceKey.length())) + "...";
            logger.info("Service Key (masked): {}", maskedKey);
        }
        
        return molitApiClient.getStationDetails(stationName)
                .map(stations -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("requestedStation", stationName);
                    response.put("serviceKeyConfigured", serviceKey != null && !serviceKey.isEmpty());
                    response.put("serviceKeyLength", serviceKey != null ? serviceKey.length() : 0);
                    response.put("resultCount", stations.size());
                    response.put("stations", stations);
                    
                    logger.info("MOLIT API 응답: {} 개 역 정보 수신", stations.size());
                    
                    if (!stations.isEmpty()) {
                        MolitStationInfo firstStation = stations.get(0);
                        logger.info("첫 번째 역 정보:");
                        logger.info("  - 역명: {}", firstStation.getStationName());
                        logger.info("  - 노선: {}", firstStation.getRouteName());
                        logger.info("  - 지역: {} {}", firstStation.getSidoName(), firstStation.getSggName());
                        logger.info("  - 좌표: lat={}, lon={}", firstStation.getLatitude(), firstStation.getLongitude());
                        logger.info("  - 도로명주소: {}", firstStation.getRoadAddress());
                    }
                    
                    return ResponseEntity.ok(response);
                })
                .doOnError(error -> {
                    logger.error("MOLIT API 호출 중 오류 발생: {}", error.getMessage(), error);
                })
                .onErrorReturn(ResponseEntity.internalServerError().body(
                    Map.of("error", "MOLIT API 호출 실패", 
                           "requestedStation", stationName,
                           "serviceKeyConfigured", serviceKey != null && !serviceKey.isEmpty())
                ));
    }

    @GetMapping("/line/{lineNumber}")
    @Operation(
        summary = "노선번호로 MOLIT API 테스트",
        description = "지정된 노선번호로 국토교통부 API를 직접 호출하여 결과를 확인합니다."
    )
    public Mono<ResponseEntity<Map<String, Object>>> testLineSearch(
            @Parameter(description = "검색할 노선번호", required = true, example = "1")
            @PathVariable String lineNumber) {
        
        logger.info("=== MOLIT API 노선 테스트 시작 ===");
        logger.info("검색 노선: {}호선", lineNumber);
        
        return molitApiClient.getStationsByLine(lineNumber)
                .map(stations -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("requestedLine", lineNumber);
                    response.put("serviceKeyConfigured", serviceKey != null && !serviceKey.isEmpty());
                    response.put("resultCount", stations.size());
                    response.put("stations", stations);
                    
                    logger.info("MOLIT API 노선 응답: {} 개 역 정보 수신", stations.size());
                    
                    return ResponseEntity.ok(response);
                })
                .onErrorReturn(ResponseEntity.internalServerError().body(
                    Map.of("error", "MOLIT API 호출 실패", 
                           "requestedLine", lineNumber,
                           "serviceKeyConfigured", serviceKey != null && !serviceKey.isEmpty())
                ));
    }

    @GetMapping("/config")
    @Operation(
        summary = "MOLIT API 설정 확인",
        description = "현재 MOLIT API 설정 상태를 확인합니다."
    )
    public ResponseEntity<Map<String, Object>> checkConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("serviceKeyConfigured", serviceKey != null && !serviceKey.isEmpty());
        config.put("serviceKeyLength", serviceKey != null ? serviceKey.length() : 0);
        config.put("baseUrl", "https://apis.data.go.kr/1613000");
        config.put("endpoints", Map.of(
            "stationSearch", "/SubwayInfoService/getKwrdFndSubwaySttnList",
            "lineSearch", "/SubwayInfoService/getSubwaySttnList"
        ));
        
        if (serviceKey != null && !serviceKey.isEmpty()) {
            String maskedKey = serviceKey.length() > 20 ? 
                serviceKey.substring(0, 10) + "..." + serviceKey.substring(serviceKey.length() - 10) : 
                serviceKey.substring(0, Math.min(10, serviceKey.length())) + "...";
            config.put("serviceKeyPreview", maskedKey);
        }
        
        logger.info("MOLIT API 설정 확인 - Key 설정됨: {}", serviceKey != null && !serviceKey.isEmpty());
        
        return ResponseEntity.ok(config);
    }
}