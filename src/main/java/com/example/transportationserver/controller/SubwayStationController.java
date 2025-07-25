package com.example.transportationserver.controller;

import com.example.transportationserver.dto.StandardApiResponse;
import com.example.transportationserver.model.SubwayStation;
import com.example.transportationserver.service.SubwayStationService;
import com.example.transportationserver.service.MolitApiClient;
import com.example.transportationserver.service.MolitApiClient.MolitStationInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subway/stations")
@Tag(name = "지하철역 관리", description = "지하철역 정보 CRUD 및 검색 기능을 제공합니다")
public class SubwayStationController {
    
    private static final Logger logger = LoggerFactory.getLogger(SubwayStationController.class);
    
    @Autowired
    private SubwayStationService subwayStationService;
    
    @Autowired
    private MolitApiClient molitApiClient;
    
    @Value("${api.molit.service.key:}")
    private String serviceKey;
    
    @Operation(summary = "모든 지하철역 조회", description = "등록된 모든 지하철역 목록을 반환합니다")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "성공적으로 조회됨"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping
    public ResponseEntity<StandardApiResponse<List<SubwayStation>>> getAllStations() {
        List<SubwayStation> stations = subwayStationService.getAllStations();
        return ResponseEntity.ok(StandardApiResponse.successWithCount(stations, "지하철역 목록 조회 성공", stations.size()));
    }
    
    @Operation(summary = "지하철역 상세 조회", description = "ID로 특정 지하철역의 상세 정보를 조회합니다")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "성공적으로 조회됨"),
        @ApiResponse(responseCode = "404", description = "해당 ID의 역을 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{id}")
    public ResponseEntity<StandardApiResponse<SubwayStation>> getStationById(
            @Parameter(description = "지하철역 ID", required = true, example = "1")
            @PathVariable Long id) {
        SubwayStation station = subwayStationService.getStationById(id);
        return ResponseEntity.ok(StandardApiResponse.success(station, "지하철역 상세 조회 성공"));
    }
    
    @GetMapping("/search")
    public ResponseEntity<StandardApiResponse<List<SubwayStation>>> searchStations(@RequestParam String name) {
        List<SubwayStation> stations = subwayStationService.searchStationsByName(name);
        return ResponseEntity.ok(StandardApiResponse.successWithCount(stations, "지하철역 검색 결과", stations.size()));
    }
    
    @GetMapping("/line/{lineNumber}")
    public ResponseEntity<StandardApiResponse<List<SubwayStation>>> getStationsByLine(@PathVariable String lineNumber) {
        List<SubwayStation> stations = subwayStationService.getStationsByLine(lineNumber);
        return ResponseEntity.ok(StandardApiResponse.successWithCount(stations, lineNumber + "호선 지하철역 목록 조회 성공", stations.size()));
    }
    
    @GetMapping("/nearby")
    public ResponseEntity<StandardApiResponse<List<SubwayStation>>> getNearbyStations(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(defaultValue = "2.0") Double radius) {
        List<SubwayStation> stations = subwayStationService.getNearbyStations(lat, lng, radius);
        return ResponseEntity.ok(StandardApiResponse.successWithCount(stations, "주변 지하철역 조회 성공", stations.size()));
    }
    
    @PostMapping
    public ResponseEntity<StandardApiResponse<SubwayStation>> createStation(@Valid @RequestBody SubwayStation station) {
        SubwayStation createdStation = subwayStationService.createStation(station);
        return new ResponseEntity<>(StandardApiResponse.success(createdStation, "지하철역 생성 성공", HttpStatus.CREATED.value()), HttpStatus.CREATED);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<StandardApiResponse<SubwayStation>> updateStation(@PathVariable Long id, 
                                                      @Valid @RequestBody SubwayStation station) {
        SubwayStation updatedStation = subwayStationService.updateStation(id, station);
        return ResponseEntity.ok(StandardApiResponse.success(updatedStation, "지하철역 정보 수정 성공"));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<StandardApiResponse<Void>> deleteStation(@PathVariable Long id) {
        subwayStationService.deleteStation(id);
        return ResponseEntity.ok(StandardApiResponse.success(null, "지하철역 삭제 성공"));
    }
}