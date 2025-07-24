package com.example.transportationserver.controller;

import com.example.transportationserver.model.SubwayStation;
import com.example.transportationserver.service.SubwayStationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subway/stations")
@Tag(name = "지하철역 관리", description = "지하철역 정보 CRUD 및 검색 기능을 제공합니다")
public class SubwayStationController {
    
    @Autowired
    private SubwayStationService subwayStationService;
    
    @Operation(summary = "모든 지하철역 조회", description = "등록된 모든 지하철역 목록을 반환합니다")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "성공적으로 조회됨"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping
    public ResponseEntity<List<SubwayStation>> getAllStations() {
        List<SubwayStation> stations = subwayStationService.getAllStations();
        return ResponseEntity.ok(stations);
    }
    
    @Operation(summary = "지하철역 상세 조회", description = "ID로 특정 지하철역의 상세 정보를 조회합니다")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "성공적으로 조회됨"),
        @ApiResponse(responseCode = "404", description = "해당 ID의 역을 찾을 수 없음"),
        @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{id}")
    public ResponseEntity<SubwayStation> getStationById(
            @Parameter(description = "지하철역 ID", required = true, example = "1")
            @PathVariable Long id) {
        SubwayStation station = subwayStationService.getStationById(id);
        return ResponseEntity.ok(station);
    }
    
    @GetMapping("/search")
    public ResponseEntity<List<SubwayStation>> searchStations(@RequestParam String name) {
        List<SubwayStation> stations = subwayStationService.searchStationsByName(name);
        return ResponseEntity.ok(stations);
    }
    
    @GetMapping("/line/{lineNumber}")
    public ResponseEntity<List<SubwayStation>> getStationsByLine(@PathVariable String lineNumber) {
        List<SubwayStation> stations = subwayStationService.getStationsByLine(lineNumber);
        return ResponseEntity.ok(stations);
    }
    
    @GetMapping("/nearby")
    public ResponseEntity<List<SubwayStation>> getNearbyStations(
            @RequestParam Double lat,
            @RequestParam Double lng,
            @RequestParam(defaultValue = "2.0") Double radius) {
        List<SubwayStation> stations = subwayStationService.getNearbyStations(lat, lng, radius);
        return ResponseEntity.ok(stations);
    }
    
    @PostMapping
    public ResponseEntity<SubwayStation> createStation(@Valid @RequestBody SubwayStation station) {
        SubwayStation createdStation = subwayStationService.createStation(station);
        return new ResponseEntity<>(createdStation, HttpStatus.CREATED);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<SubwayStation> updateStation(@PathVariable Long id, 
                                                      @Valid @RequestBody SubwayStation station) {
        SubwayStation updatedStation = subwayStationService.updateStation(id, station);
        return ResponseEntity.ok(updatedStation);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStation(@PathVariable Long id) {
        subwayStationService.deleteStation(id);
        return ResponseEntity.noContent().build();
    }
}