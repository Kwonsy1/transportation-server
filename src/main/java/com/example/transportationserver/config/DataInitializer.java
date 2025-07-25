package com.example.transportationserver.config;

import com.example.transportationserver.service.SubwayDataSyncService;
import com.example.transportationserver.service.SubwayStationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
    
    @Autowired
    private SubwayDataSyncService syncService;
    
    @Autowired
    private SubwayStationService stationService;
    
    @Autowired
    private Environment environment;
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.info("데이터 초기화 시작...");
        
        try {
            // 현재 데이터베이스에 저장된 지하철역 개수 확인
            int stationCount = stationService.getAllStations().size();
            logger.info("현재 데이터베이스에 저장된 지하철역 개수: {}", stationCount);
            
            // 데이터가 없거나 적을 경우에만 동기화 실행
            if (stationCount < 100) {
                logger.info("데이터가 부족합니다. 서울시 API에서 데이터 동기화를 시작합니다...");
                
                // 동기화 실행
                syncService.triggerFullSync();
                
                // 동기화 완료까지 대기 (최대 60초)
                int maxWaitTime = 60;
                int waitedTime = 0;
                
                while (waitedTime < maxWaitTime) {
                    Thread.sleep(2000); // 2초 대기
                    waitedTime += 2;
                    
                    int currentCount = stationService.getAllStations().size();
                    logger.info("동기화 진행 중... 현재 {} 개 역 저장됨", currentCount);
                    
                    if (currentCount > 700) {
                        logger.info("데이터 동기화 완료! 총 {} 개 지하철역이 저장되었습니다.", currentCount);
                        break;
                    }
                }
                
                if (waitedTime >= maxWaitTime) {
                    logger.warn("데이터 동기화가 예상보다 오래 걸리고 있습니다. 백그라운드에서 계속 진행됩니다.");
                }
                
            } else {
                logger.info("데이터베이스에 충분한 데이터가 있습니다. 동기화를 건너뜁니다.");
            }
            
            logger.info("데이터 초기화 완료!");
            
        } catch (Exception e) {
            logger.error("데이터 초기화 중 오류 발생", e);
        }
    }
}