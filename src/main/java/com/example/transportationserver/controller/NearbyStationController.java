package com.example.transportationserver.controller;

import com.example.transportationserver.dto.NearbyStationResponse;
import com.example.transportationserver.dto.GroupedNearbyStationResponse;
import com.example.transportationserver.dto.StandardApiResponse;
import com.example.transportationserver.service.SubwayStationService;
import com.example.transportationserver.util.ErrorHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 근처 지하철역 조회 API 컨트롤러
 * 클라이언트가 좌표를 제공하면 근처 지하철역을 반환
 */
@RestController
@RequestMapping("/api/stations")
public class NearbyStationController {
    
    private static final Logger logger = LoggerFactory.getLogger(NearbyStationController.class);
    
    @Autowired
    private SubwayStationService stationService;
    
    /**
     * 근처 지하철역 조회
     * 
     * @param latitude 위도 (필수, 33.0 ~ 43.0)
     * @param longitude 경도 (필수, 124.0 ~ 132.0)
     * @param radius 검색 반경 (km, 선택, 기본값: 2.0, 최대: 50.0)
     * @param limit 최대 결과 개수 (선택, 기본값: 80, 최대: 200)
     * @return 거리순으로 정렬된 근처 지하철역 목록
     */
    @Operation(
        summary = "근처 지하철역 조회 (그룹화된 결과)",
        description = "주어진 좌표를 중심으로 지정된 반경 내의 지하철역을 그룹화하여 거리순으로 조회합니다. " +
                      "같은 역명이고 5km 이내에 있는 역들을 하나로 그룹화하여 반환합니다. " +
                      "최대 200개까지 조회 가능하며, 기본적으로 80개를 반환합니다.",
        tags = {"2. 클라이언트 API (DB → 클라이언트)"}
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200", 
            description = "조회 성공",
            content = @Content(schema = @Schema(implementation = GroupedNearbyStationResponse.class))
        ),
        @ApiResponse(responseCode = "400", description = "잘못된 좌표 또는 파라미터"),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @GetMapping("/nearby")
    public ResponseEntity<StandardApiResponse<GroupedNearbyStationResponse>> getNearbyStations(
            @Parameter(description = "위도 (33.0 ~ 43.0)", required = true, example = "37.5665")
            @RequestParam("latitude") Double latitude,
            
            @Parameter(description = "경도 (124.0 ~ 132.0)", required = true, example = "126.9780") 
            @RequestParam("longitude") Double longitude,
            
            @Parameter(description = "검색 반경 (km, 기본값: 2.0, 최대: 50.0)", example = "2.0")
            @RequestParam(value = "radius", required = false) Double radius,
            
            @Parameter(description = "최대 결과 개수 (기본값: 80, 최대: 200)", example = "80")
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        
        return ErrorHandler.handleWithTryCatch(() -> {
            logger.info("근처 지하철역 조회 요청: lat={}, lon={}, radius={}km, limit={}", 
                       latitude, longitude, radius, limit);
            
            // 파라미터 검증
            if (latitude == null || longitude == null) {
                throw new IllegalArgumentException("위도와 경도는 필수 파라미터입니다");
            }
            
            if (radius != null && radius <= 0) {
                throw new IllegalArgumentException("검색 반경은 0보다 커야 합니다");
            }
            
            if (limit != null && limit <= 0) {
                throw new IllegalArgumentException("결과 개수는 0보다 커야 합니다");
            }
            
            // 서비스 호출 (그룹화된 버전)
            GroupedNearbyStationResponse response = stationService.findNearbyStationsGrouped(latitude, longitude, radius, limit);
            
            logger.info("근처 지하철역 조회 완료: {}개 그룹 반환 (반경: {}km)", 
                       response.getTotalCount(), response.getSearchRadiusKm());
            
            return response;
            
        }, "근처 지하철역 조회", 
           String.format("반경 %.1fkm 내 지하철역 조회 완료", radius != null ? radius : 2.0), 
           logger);
    }
    
    /**
     * 특정 지점 기준 가장 가까운 지하철역 조회 (편의 API)
     */
    @Operation(
        summary = "가장 가까운 지하철역 조회 (그룹화된 결과)", 
        description = "주어진 좌표에서 가장 가까운 지하철역 그룹 1개를 조회합니다. 같은 역명의 여러 노선이 있을 경우 하나로 그룹화됩니다.",
        tags = {"2. 클라이언트 API (DB → 클라이언트)"}
    )
    @GetMapping("/nearest")
    public ResponseEntity<StandardApiResponse<GroupedNearbyStationResponse>> getNearestStation(
            @Parameter(description = "위도", required = true, example = "37.5665")
            @RequestParam("latitude") Double latitude,
            
            @Parameter(description = "경도", required = true, example = "126.9780")
            @RequestParam("longitude") Double longitude
    ) {
        
        return ErrorHandler.handleWithTryCatch(() -> {
            logger.info("가장 가까운 지하철역 조회 요청: lat={}, lon={}", latitude, longitude);
            
            // 반경 10km, 결과 1개로 제한 (그룹화된 버전)
            GroupedNearbyStationResponse response = stationService.findNearbyStationsGrouped(latitude, longitude, 10.0, 1);
            
            if (response.getTotalCount() == 0) {
                logger.info("반경 10km 내에 지하철역이 없습니다");
                return response;
            }
            
            GroupedNearbyStationResponse.GroupedNearbyStation nearest = response.getStations().get(0);
            logger.info("가장 가까운 지하철역: {} (노선: {}, 거리: {:.2f}km)", 
                       nearest.getStationName(), nearest.getLines(), nearest.getDistanceKm());
            
            return response;
            
        }, "가장 가까운 지하철역 조회",
           "가장 가까운 지하철역 조회 완료",
           logger);
    }
}