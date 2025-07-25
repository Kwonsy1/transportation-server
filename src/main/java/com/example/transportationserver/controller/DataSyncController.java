package com.example.transportationserver.controller;

import com.example.transportationserver.dto.StandardApiResponse;
import com.example.transportationserver.service.SubwayDataSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sync")
@Tag(name = "Data Synchronization", description = "데이터 동기화 관련 API")
public class DataSyncController {

    @Autowired
    private SubwayDataSyncService syncService;

    @PostMapping("/stations")
    @Operation(summary = "지하철역 데이터 동기화", description = "서울 공공데이터 포털에서 지하철역 정보를 가져와 DB에 저장")
    public ResponseEntity<StandardApiResponse<String>> syncStations() {
        try {
            syncService.triggerFullSync();
            return ResponseEntity.ok(StandardApiResponse.success("동기화 작업이 시작되었습니다", "지하철역 데이터 동기화 시작 성공"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(StandardApiResponse.error("동기화 시작 실패: " + e.getMessage(), 500));
        }
    }

    @PostMapping("/station/{stationName}/schedule")
    @Operation(summary = "특정 역 시간표 동기화", description = "특정 지하철역의 시간표 정보를 동기화")
    public ResponseEntity<StandardApiResponse<String>> syncStationSchedule(@PathVariable String stationName) {
        try {
            syncService.syncStationSchedule(stationName);
            return ResponseEntity.ok(StandardApiResponse.success(
                stationName + " 역 시간표 동기화가 시작되었습니다", 
                "시간표 동기화 시작 성공"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(StandardApiResponse.error("시간표 동기화 실패: " + e.getMessage(), 500));
        }
    }

    @GetMapping("/status")
    @Operation(summary = "동기화 상태 확인", description = "현재 데이터 동기화 상태 확인")
    public ResponseEntity<StandardApiResponse<String>> getSyncStatus() {
        return ResponseEntity.ok(StandardApiResponse.success(
            "데이터 동기화 서비스가 정상 작동 중입니다", 
            "동기화 상태 확인 성공"));
    }
}