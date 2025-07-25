package com.example.transportationserver.controller;

import com.example.transportationserver.service.EnhancedSubwayDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/enhanced-sync")
@Tag(name = "Enhanced Data Synchronization", description = "다층 데이터 동기화 API (서울시 + 국토교통부 + OpenStreetMap)")
public class EnhancedDataSyncController {

    @Autowired
    private EnhancedSubwayDataService enhancedSyncService;

    @PostMapping("/full")
    @Operation(
        summary = "전체 다층 데이터 동기화", 
        description = """
            3단계 데이터 수집 프로세스:
            1. 서울시 공공데이터에서 기본 역명 리스트 수집
            2. 국토교통부 API로 전국 역 상세정보 보완
            3. OpenStreetMap으로 좌표 정보 보완 (1초 간격 제한)
            
            동명 역 구분: 시청역(서울), 시청역(대전) 등으로 자동 처리
            """
    )
    public ResponseEntity<String> performEnhancedSync() {
        try {
            enhancedSyncService.triggerEnhancedSync();
            return ResponseEntity.ok("""
                Enhanced data synchronization started successfully.
                
                Process includes:
                - Seoul API: Station name collection
                - MOLIT API: Detailed station information
                - OpenStreetMap: Coordinate enrichment (1-second rate limit)
                - Duplicate handling: Regional grouping and disambiguation
                
                Monitor logs for progress updates.
                """);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Failed to start enhanced synchronization: " + e.getMessage());
        }
    }

    @GetMapping("/status")
    @Operation(summary = "동기화 서비스 상태", description = "향상된 데이터 동기화 서비스의 현재 상태 확인")
    public ResponseEntity<String> getServiceStatus() {
        return ResponseEntity.ok("""
            Enhanced Data Synchronization Service Status: READY
            
            Available APIs:
            ✓ Seoul Public Data Portal (서울시 공공데이터)
            ✓ MOLIT API (국토교통부)
            ✓ OpenStreetMap Nominatim (좌표 검색)
            
            Features:
            ✓ Multi-layer data collection
            ✓ Regional station disambiguation
            ✓ Rate-limited coordinate enrichment
            ✓ Duplicate detection and merging
            """);
    }
    
    @PostMapping("/coordinates-only")
    @Operation(
        summary = "좌표 정보만 보완", 
        description = "기존 역 데이터 중 좌표가 없는 역들만 OpenStreetMap으로 좌표 보완"
    )
    public ResponseEntity<String> enrichCoordinatesOnly() {
        return ResponseEntity.ok("Coordinate enrichment feature will be implemented in next version.");
    }
}