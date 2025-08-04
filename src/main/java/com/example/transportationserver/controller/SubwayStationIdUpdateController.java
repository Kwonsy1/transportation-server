package com.example.transportationserver.controller;

import com.example.transportationserver.dto.StandardApiResponse;
import com.example.transportationserver.service.SubwayStationIdUpdateService;
import com.example.transportationserver.util.ErrorHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SubwayStationId 업데이트 API 컨트롤러
 */
@RestController
@RequestMapping("/api/admin")
public class SubwayStationIdUpdateController {
    
    private static final Logger logger = LoggerFactory.getLogger(SubwayStationIdUpdateController.class);
    
    @Autowired
    private SubwayStationIdUpdateService updateService;
    
    /**
     * MOLIT API에서 subwayStationId 대량 업데이트
     */
    @Operation(
        summary = "지하철역 ID 대량 업데이트",
        description = "MOLIT API에서 전체 지하철역의 subwayStationId를 가져와서 데이터베이스를 업데이트합니다. " +
                      "총 1092개의 역 데이터를 처리하며, 완료까지 1-2분 소요됩니다."
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "업데이트 완료"),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @PostMapping("/update-subway-station-ids")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> updateSubwayStationIds() {
        
        return ErrorHandler.handleWithTryCatch(() -> {
            logger.info("===== 지하철역 ID 대량 업데이트 시작 =====");
            
            Map<String, Object> result = updateService.updateAllSubwayStationIds();
            
            logger.info("===== 지하철역 ID 대량 업데이트 완료 =====");
            logger.info("결과: {}", result);
            
            return result;
            
        }, "지하철역 ID 대량 업데이트",
           "MOLIT API에서 지하철역 ID 업데이트 완료",
           logger);
    }
    
    /**
     * 업데이트 상태 확인
     */
    @Operation(
        summary = "업데이트 상태 확인",
        description = "현재 데이터베이스에서 subwayStationId가 설정된 역의 개수를 확인합니다."
    )
    @GetMapping("/subway-station-id-status")
    public ResponseEntity<StandardApiResponse<Map<String, Object>>> getUpdateStatus() {
        
        return ErrorHandler.handleWithTryCatch(() -> {
            // 간단한 상태 확인을 위한 더미 데이터
            // 실제로는 데이터베이스 쿼리로 구현해야 함
            Map<String, Object> status = Map.of(
                "message", "업데이트 상태 확인 API",
                "note", "실제 구현은 데이터베이스 쿼리로 대체 필요"
            );
            
            return status;
            
        }, "업데이트 상태 확인",
           "업데이트 상태 확인 완료",
           logger);
    }
}