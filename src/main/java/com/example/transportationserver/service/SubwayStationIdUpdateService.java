package com.example.transportationserver.service;

import com.example.transportationserver.repository.SubwayStationMapper;
import com.example.transportationserver.service.MolitApiClient.MolitStationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MOLIT API에서 subwayStationId를 가져와서 데이터베이스를 업데이트하는 서비스
 */
@Service
public class SubwayStationIdUpdateService {
    
    private static final Logger logger = LoggerFactory.getLogger(SubwayStationIdUpdateService.class);
    
    @Autowired
    private MolitApiClient molitApiClient;
    
    @Autowired
    private SubwayStationMapper subwayStationMapper;
    
    @Autowired
    private RateLimitService rateLimitService;
    
    /**
     * MOLIT API에서 전체 지하철역 데이터를 가져와서 subwayStationId 업데이트
     */
    public Map<String, Object> updateAllSubwayStationIds() {
        logger.info("===== MOLIT API subwayStationId 대량 업데이트 시작 =====");
        long startTime = System.currentTimeMillis();
        
        AtomicInteger totalApiRecords = new AtomicInteger(0);
        AtomicInteger successfulUpdates = new AtomicInteger(0);
        AtomicInteger failedUpdates = new AtomicInteger(0);
        
        try {
            // 페이징으로 전체 데이터 조회 (MOLIT API는 총 1092개 레코드)
            int pageSize = 500;  // 한 번에 500개씩 조회
            int totalPages = 3;  // 총 3페이지면 충분
            
            for (int page = 1; page <= totalPages; page++) {
                logger.info("===== MOLIT API 페이지 {} 조회 시작 =====", page);
                
                try {
                    List<MolitStationInfo> molitStations = molitApiClient.getAllStations(pageSize, page).block();
                    
                    if (molitStations == null || molitStations.isEmpty()) {
                        logger.info("페이지 {}에서 데이터가 없습니다. 종료합니다.", page);
                        break;
                    }
                    
                    totalApiRecords.addAndGet(molitStations.size());
                    logger.info("페이지 {}에서 {}개 역 데이터 수신", page, molitStations.size());
                    
                    // 각 역에 대해 업데이트 시도
                    for (MolitStationInfo molitStation : molitStations) {
                        try {
                            String stationName = molitStation.getStationName();
                            String routeName = molitStation.getRouteName();
                            String subwayStationId = molitStation.getStationId();
                            
                            if (stationName == null || subwayStationId == null) {
                                logger.debug("역명 또는 stationId가 null입니다: {}", molitStation.getStationName());
                                continue;
                            }
                            
                            // 역명 정규화 (끝의 공백 제거)
                            stationName = stationName.trim();
                            
                            // 호선명을 표준 형식으로 변환
                            String normalizedLineNumber = normalizeLineNumber(routeName);
                            
                            // 데이터베이스 업데이트
                            int updateCount = subwayStationMapper.updateSubwayStationId(
                                stationName, normalizedLineNumber, subwayStationId);
                                
                            if (updateCount > 0) {
                                successfulUpdates.incrementAndGet();
                                logger.debug("업데이트 성공: {} ({}) -> {}", stationName, normalizedLineNumber, subwayStationId);
                            } else {
                                // 호선명 없이도 시도
                                int updateCountWithoutLine = tryUpdateWithoutLineNumber(stationName, subwayStationId);
                                if (updateCountWithoutLine > 0) {
                                    successfulUpdates.incrementAndGet();
                                    logger.debug("호선명 없이 업데이트 성공: {} -> {}", stationName, subwayStationId);
                                } else {
                                    failedUpdates.incrementAndGet();
                                    logger.debug("업데이트 실패: {} ({}) - 매칭되는 역이 없음", stationName, normalizedLineNumber);
                                }
                            }
                            
                        } catch (Exception e) {
                            failedUpdates.incrementAndGet();
                            logger.error("역 업데이트 중 오류: {}", molitStation.getStationName(), e);
                        }
                    }
                    
                    logger.info("페이지 {} 처리 완료: 성공 {}, 실패 {}", page, successfulUpdates.get(), failedUpdates.get());
                    
                    // API 호출 제한 고려
                    if (page < totalPages) {
                        rateLimitService.waitForMolit();
                    }
                    
                } catch (Exception e) {
                    logger.error("페이지 {} 처리 중 오류 발생", page, e);
                }
            }
            
        } catch (Exception e) {
            logger.error("subwayStationId 업데이트 중 전체적인 오류 발생", e);
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalApiRecords", totalApiRecords.get());
        result.put("successfulUpdates", successfulUpdates.get());
        result.put("failedUpdates", failedUpdates.get());
        result.put("durationMs", duration);
        result.put("durationSeconds", duration / 1000.0);
        
        logger.info("===== MOLIT API subwayStationId 대량 업데이트 완료 =====");
        logger.info("총 API 레코드: {}, 성공: {}, 실패: {}, 소요시간: {}초", 
                   totalApiRecords.get(), successfulUpdates.get(), failedUpdates.get(), duration / 1000.0);
        
        return result;
    }
    
    /**
     * 호선명을 표준 형식으로 변환
     */
    private String normalizeLineNumber(String routeName) {
        if (routeName == null) return null;
        
        // 기본적인 호선명 정규화
        routeName = routeName.trim();
        
        // 숫자 호선 (1호선, 2호선 등)
        if (routeName.matches("\\d+호선")) {
            return routeName;
        }
        
        // 특수 노선명 매핑
        Map<String, String> lineMapping = Map.of(
            "공항", "공항철도",
            "분당선", "수인분당선",
            "수인선", "수인분당선",
            "경춘선", "경춘선",
            "경의선", "경의중앙선",
            "중앙선", "경의중앙선"
        );
        
        return lineMapping.getOrDefault(routeName, routeName);
    }
    
    /**
     * 호선명 없이 역명만으로 업데이트 시도
     */
    private int tryUpdateWithoutLineNumber(String stationName, String subwayStationId) {
        try {
            // 같은 이름의 역 중 subway_station_id가 null인 첫 번째 역만 업데이트
            return subwayStationMapper.updateSubwayStationId(stationName, null, subwayStationId);
        } catch (Exception e) {
            logger.debug("호선명 없이 업데이트 시도 실패: {}", stationName, e);
            return 0;
        }
    }
}