package com.example.transportationserver.controller;

import com.example.transportationserver.service.SubwayStationService;
import com.example.transportationserver.util.ErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
public class CoordinateUpdateController {
    
    private static final Logger logger = LoggerFactory.getLogger(CoordinateUpdateController.class);
    
    @Autowired
    private SubwayStationService stationService;
    
    @PostMapping("/update-dongdaemun-coordinates")
    public ResponseEntity<Map<String, Object>> updateDongdaemunCoordinates() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 동대문역사문화공원역 3개 노선 좌표 업데이트
            // OpenStreetMap 좌표: 37.5652615, 127.0081233
            
            boolean success1 = stationService.updateStationCoordinates(340L, 37.5652615, 127.0081233); // 2호선
            boolean success2 = stationService.updateStationCoordinates(224L, 37.5652615, 127.0081233); // 4호선  
            boolean success3 = stationService.updateStationCoordinates(573L, 37.5652615, 127.0081233); // 5호선
            
            result.put("success", success1 && success2 && success3);
            result.put("line2_id340", success1);
            result.put("line4_id224", success2);
            result.put("line5_id573", success3);
            result.put("coordinates", Map.of("latitude", 37.5652615, "longitude", 127.0081233));
            result.put("message", "동대문역사문화공원역 좌표 업데이트 완료");
            
            logger.info("동대문역사문화공원역 좌표 업데이트 결과: 2호선={}, 4호선={}, 5호선={}", 
                       success1, success2, success3);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            ErrorHandler.logAndHandle(logger, "좌표 업데이트", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
}