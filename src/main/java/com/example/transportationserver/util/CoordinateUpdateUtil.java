package com.example.transportationserver.util;

import com.example.transportationserver.service.SubwayStationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Scanner;

/**
 * 좌표 업데이트를 위한 유틸리티 클래스
 * 커맨드라인에서 직접 실행하거나 프로그래밍적으로 사용할 수 있습니다.
 */
@Component
public class CoordinateUpdateUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(CoordinateUpdateUtil.class);
    
    @Autowired
    private SubwayStationService stationService;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    /**
     * 동대문역사문화공원역 좌표 업데이트 (프로그래밍 방식)
     */
    public void updateDongdaemunHistoryCultureParkCoordinates() {
        Double latitude = 37.5652615;  // OpenStreetMap 기준
        Double longitude = 127.0081233; // OpenStreetMap 기준
        
        logger.info("=== 동대문역사문화공원역 좌표 업데이트 시작 ===");
        logger.info("OpenStreetMap 좌표: lat={}, lon={}", latitude, longitude);
        
        try {
            Map<String, Object> result = stationService.updateDongdaemunHistoryCultureParkCoordinates(latitude, longitude);
            
            logger.info("업데이트 결과:");
            logger.info("- 대상 역: {}", result.get("targetStation"));
            logger.info("- 대상 ID들: {}", java.util.Arrays.toString((Long[]) result.get("targetIds")));
            logger.info("- 총 대상: {}개", result.get("totalTargets"));
            logger.info("- 성공: {}개", result.get("successCount"));
            logger.info("- 실패: {}개", result.get("failCount"));
            
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> details = (java.util.List<Map<String, Object>>) result.get("details");
            
            logger.info("상세 결과:");
            for (Map<String, Object> detail : details) {
                logger.info("  - ID {}: {} ({}호선) -> {}", 
                    detail.get("id"), 
                    detail.get("name"), 
                    detail.get("lineNumber"),
                    detail.get("status"));
            }
            
        } catch (Exception e) {
            logger.error("동대문역사문화공원역 좌표 업데이트 실패: {}", e.getMessage(), e);
        }
        
        logger.info("=== 동대문역사문화공원역 좌표 업데이트 완료 ===");
    }
    
    /**
     * 개별 역 좌표 업데이트
     */
    public boolean updateSingleStationCoordinates(Long id, Double latitude, Double longitude) {
        logger.info("개별 역 좌표 업데이트: ID={}, lat={}, lon={}", id, latitude, longitude);
        
        try {
            boolean success = stationService.updateStationCoordinates(id, latitude, longitude);
            
            if (success) {
                logger.info("역 ID {} 좌표 업데이트 성공", id);
            } else {
                logger.warn("역 ID {} 좌표 업데이트 실패", id);
            }
            
            return success;
            
        } catch (Exception e) {
            logger.error("역 ID {} 좌표 업데이트 중 오류: {}", id, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 대화형 좌표 업데이트 (콘솔에서 실행)
     */
    public void interactiveCoordinateUpdate() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("=== 지하철 역 좌표 업데이트 유틸리티 ===");
        System.out.println("1. 동대문역사문화공원역 일괄 업데이트");
        System.out.println("2. 개별 역 업데이트");
        System.out.println("3. 종료");
        System.out.print("선택하세요 (1-3): ");
        
        int choice = scanner.nextInt();
        
        switch (choice) {
            case 1:
                updateDongdaemunHistoryCultureParkCoordinates();
                break;
                
            case 2:
                System.out.print("역 ID를 입력하세요: ");
                Long id = scanner.nextLong();
                System.out.print("위도를 입력하세요: ");
                Double lat = scanner.nextDouble();
                System.out.print("경도를 입력하세요: ");
                Double lon = scanner.nextDouble();
                
                updateSingleStationCoordinates(id, lat, lon);
                break;
                
            case 3:
                System.out.println("프로그램을 종료합니다.");
                break;
                
            default:
                System.out.println("잘못된 선택입니다.");
        }
        
        scanner.close();
    }
    
    /**
     * 좌표 업데이트 상태 체크
     */
    public void checkCoordinateStatus() {
        logger.info("=== 좌표 상태 체크 ===");
        
        try {
            SubwayStationService.CoordinateStatistics stats = stationService.getCoordinateStatistics();
            
            logger.info("전체 통계:");
            logger.info("- 전체 역 수: {}개", stats.getTotal());
            logger.info("- 좌표 보유: {}개", stats.getHasCoordinates());
            logger.info("- 좌표 누락: {}개", stats.getMissingCoordinates());
            logger.info("- 완성률: {:.1f}%", stats.getCompletionRate());
            
        } catch (Exception e) {
            logger.error("좌표 상태 체크 실패: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 커맨드라인 실행을 위한 메인 메서드
     */
    public static void main(String[] args) {
        SpringApplication.run(CoordinateUpdateUtil.class, args);
    }
    
    /**
     * Spring Boot 애플리케이션이 시작된 후 실행되는 메서드
     */
    // @Override  // CommandLineRunner 구현 시 주석 해제
    public void run(String... args) throws Exception {
        if (args.length > 0 && "update-dongdaemun".equals(args[0])) {
            updateDongdaemunHistoryCultureParkCoordinates();
            // 애플리케이션 종료
            SpringApplication.exit(applicationContext, () -> 0);
        }
    }
}