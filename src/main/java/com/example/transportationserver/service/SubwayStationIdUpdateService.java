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
                            
                            // 역명 정규화
                            String normalizedStationName = normalizeStationName(stationName);
                            
                            // 호선명을 표준 형식으로 변환
                            String normalizedLineNumber = normalizeLineNumber(routeName);
                            
                            // 다양한 매칭 시도
                            boolean updated = tryUpdateWithMultipleStrategies(
                                stationName, normalizedStationName, normalizedLineNumber, subwayStationId);
                                
                            if (updated) {
                                successfulUpdates.incrementAndGet();
                                logger.debug("업데이트 성공: {} -> {}", stationName, subwayStationId);
                            } else {
                                failedUpdates.incrementAndGet();
                                logger.debug("업데이트 실패: {} ({}) - 매칭되는 역이 없음", stationName, normalizedLineNumber);
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
     * 역명을 정규화하여 매칭률을 높임
     */
    private String normalizeStationName(String stationName) {
        if (stationName == null) return null;
        
        String normalized = stationName.trim();
        
        // 괄호 안의 부가 정보 처리 (선택적으로 제거할 수 있도록 원본도 반환)
        // 예: "잠실(송파구청)" -> "잠실"
        if (normalized.contains("(") && normalized.contains(")")) {
            // 괄호 안 내용이 있는 경우 원본 그대로 먼저 시도
            return normalized;
        }
        
        // 특수 문자 정리
        normalized = normalized.replaceAll("\\s+", ""); // 공백 제거
        
        // 역명 별칭 매핑
        Map<String, String> stationMapping = Map.of(
            "서울역", "서울",
            "김포공항", "김포공항",
            "인천공항1터미널", "인천국제공항",
            "인천공항2터미널", "인천국제공항화물터미널",
            "디지털미디어시티", "디지털미디어시티"
        );
        
        return stationMapping.getOrDefault(normalized, normalized);
    }
    
    /**
     * 호선명을 표준 형식으로 변환 (개선된 버전)
     */
    private String normalizeLineNumber(String routeName) {
        if (routeName == null) return null;
        
        // 기본적인 호선명 정규화
        routeName = routeName.trim();
        
        // 숫자 호선 (1호선, 2호선 등)
        if (routeName.matches("\\d+호선")) {
            return routeName;
        }
        
        // 특수 노선명 매핑 (확장된 버전)
        Map<String, String> lineMapping = Map.of(
            "공항", "공항철도",
            "신분당", "신분당선",
            "분당선", "수인분당선",
            "수인선", "수인분당선", 
            "수인분당", "수인분당선",
            "경춘선", "경춘선",
            "경의선", "경의중앙선",
            "중앙선", "경의중앙선",
            "에버라인", "에버라인"
        );
        
        return lineMapping.getOrDefault(routeName, routeName);
    }
    
    /**
     * 여러 전략으로 매칭을 시도하여 성공률을 높임
     */
    private boolean tryUpdateWithMultipleStrategies(String originalStationName, String normalizedStationName, 
                                                   String normalizedLineNumber, String subwayStationId) {
        
        // 전략 1: 원본 역명 + 정규화된 호선명
        int updateCount = subwayStationMapper.updateSubwayStationId(
            originalStationName, normalizedLineNumber, subwayStationId);
        if (updateCount > 0) {
            logger.debug("전략1 성공: {} + {}", originalStationName, normalizedLineNumber);
            return true;
        }
        
        // 전략 2: 정규화된 역명 + 정규화된 호선명
        if (!originalStationName.equals(normalizedStationName)) {
            updateCount = subwayStationMapper.updateSubwayStationId(
                normalizedStationName, normalizedLineNumber, subwayStationId);
            if (updateCount > 0) {
                logger.debug("전략2 성공: {} + {}", normalizedStationName, normalizedLineNumber);
                return true;
            }
        }
        
        // 전략 3: 괄호 제거한 역명 시도
        String stationNameWithoutParentheses = removeParentheses(originalStationName);
        if (!stationNameWithoutParentheses.equals(originalStationName)) {
            updateCount = subwayStationMapper.updateSubwayStationId(
                stationNameWithoutParentheses, normalizedLineNumber, subwayStationId);
            if (updateCount > 0) {
                logger.debug("전략3 성공: {} + {}", stationNameWithoutParentheses, normalizedLineNumber);
                return true;
            }
        }
        
        // 전략 4: 원본 역명 + 호선명 없이
        updateCount = subwayStationMapper.updateSubwayStationId(
            originalStationName, null, subwayStationId);
        if (updateCount > 0) {
            logger.debug("전략4 성공: {} (호선명 없이)", originalStationName);
            return true;
        }
        
        // 전략 5: 정규화된 역명 + 호선명 없이
        if (!originalStationName.equals(normalizedStationName)) {
            updateCount = subwayStationMapper.updateSubwayStationId(
                normalizedStationName, null, subwayStationId);
            if (updateCount > 0) {
                logger.debug("전략5 성공: {} (호선명 없이)", normalizedStationName);
                return true;
            }
        }
        
        // 전략 6: 괄호 제거한 역명 + 호선명 없이
        if (!stationNameWithoutParentheses.equals(originalStationName)) {
            updateCount = subwayStationMapper.updateSubwayStationId(
                stationNameWithoutParentheses, null, subwayStationId);
            if (updateCount > 0) {
                logger.debug("전략6 성공: {} (호선명 없이)", stationNameWithoutParentheses);
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 괄호와 그 안의 내용을 제거
     */
    private String removeParentheses(String stationName) {
        if (stationName == null) return null;
        return stationName.replaceAll("\\([^)]*\\)", "").trim();
    }
    
}