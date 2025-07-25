package com.example.transportationserver.service;

import com.example.transportationserver.dto.SubwayStationApiDto;
import com.example.transportationserver.dto.SubwayScheduleApiDto;
import com.example.transportationserver.model.SubwayStation;
import com.example.transportationserver.model.SubwaySchedule;
import com.example.transportationserver.repository.SubwayStationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class SubwayDataSyncService {
    
    private static final Logger logger = LoggerFactory.getLogger(SubwayDataSyncService.class);
    
    @Autowired
    private KoreanSubwayApiClient apiClient;
    
    @Autowired
    private SubwayStationMapper stationMapper;
    
    /**
     * 지하철역 데이터 동기화
     */
    @Async
    public CompletableFuture<Void> syncStationData() {
        logger.info("Starting subway station data synchronization...");
        
        try {
            // API에서 전체 역 데이터 가져오기 (페이징 처리)
            int pageSize = 100;
            int currentPage = 1;
            boolean hasMore = true;
            int totalSynced = 0;
            
            while (hasMore) {
                int startIndex = (currentPage - 1) * pageSize + 1;
                int endIndex = currentPage * pageSize;
                
                List<SubwayStationApiDto> apiStations = apiClient.getAllStations(startIndex, endIndex)
                        .block(); // Reactive -> Blocking for simplicity
                
                if (apiStations == null || apiStations.isEmpty()) {
                    hasMore = false;
                    break;
                }
                
                // API 데이터를 DB 모델로 변환 및 저장
                for (SubwayStationApiDto apiStation : apiStations) {
                    try {
                        syncStation(apiStation);
                        totalSynced++;
                    } catch (Exception e) {
                        logger.error("Failed to sync station: {}, error: {}", apiStation.getStationName(), e.getMessage());
                    }
                }
                
                logger.info("Synced page {}: {} stations", currentPage, apiStations.size());
                currentPage++;
                
                // API 호출 제한을 고려한 딜레이
                Thread.sleep(100);
            }
            
            logger.info("Subway station data synchronization completed. Total synced: {}", totalSynced);
            
        } catch (Exception e) {
            logger.error("Failed to synchronize subway station data", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 개별 지하철역 동기화
     */
    private void syncStation(SubwayStationApiDto apiStation) {
        // 기존 데이터 확인
        SubwayStation existingStation = null;
        if (apiStation.getStationCode() != null) {
            existingStation = stationMapper.findByExternalId(apiStation.getStationCode());
        }
        
        if (existingStation == null) {
            // 새로운 역 생성
            SubwayStation newStation = convertApiToModel(apiStation);
            stationMapper.insert(newStation);
            logger.debug("Created new station: {}", newStation.getName());
        } else {
            // 기존 역 업데이트
            updateStationFromApi(existingStation, apiStation);
            stationMapper.update(existingStation);
            logger.debug("Updated existing station: {}", existingStation.getName());
        }
    }
    
    /**
     * API DTO를 DB 모델로 변환
     */
    private SubwayStation convertApiToModel(SubwayStationApiDto apiStation) {
        SubwayStation station = new SubwayStation();
        station.setName(apiStation.getStationName());
        station.setLineNumber(apiStation.getLineNumber());
        station.setStationCode(apiStation.getFrCode());
        station.setExternalId(apiStation.getStationCode());
        station.setAddress(apiStation.getAddress());
        
        // 좌표 변환 (String -> Double)
        try {
            if (apiStation.getLatitude() != null && !apiStation.getLatitude().isEmpty()) {
                station.setLatitude(Double.parseDouble(apiStation.getLatitude()));
            }
            if (apiStation.getLongitude() != null && !apiStation.getLongitude().isEmpty()) {
                station.setLongitude(Double.parseDouble(apiStation.getLongitude()));
            }
        } catch (NumberFormatException e) {
            logger.warn("Invalid coordinate format for station: {}", apiStation.getStationName());
        }
        
        LocalDateTime now = LocalDateTime.now();
        station.setCreatedAt(now);
        station.setUpdatedAt(now);
        
        return station;
    }
    
    /**
     * API 데이터로 기존 역 정보 업데이트
     */
    private void updateStationFromApi(SubwayStation existingStation, SubwayStationApiDto apiStation) {
        existingStation.setName(apiStation.getStationName());
        existingStation.setLineNumber(apiStation.getLineNumber());
        existingStation.setStationCode(apiStation.getFrCode());
        existingStation.setAddress(apiStation.getAddress());
        
        // 좌표 업데이트
        try {
            if (apiStation.getLatitude() != null && !apiStation.getLatitude().isEmpty()) {
                existingStation.setLatitude(Double.parseDouble(apiStation.getLatitude()));
            }
            if (apiStation.getLongitude() != null && !apiStation.getLongitude().isEmpty()) {
                existingStation.setLongitude(Double.parseDouble(apiStation.getLongitude()));
            }
        } catch (NumberFormatException e) {
            logger.warn("Invalid coordinate format for station update: {}", apiStation.getStationName());
        }
        
        existingStation.setUpdatedAt(LocalDateTime.now());
    }
    
    /**
     * 특정 역의 시간표 데이터 동기화
     */
    @Async
    public CompletableFuture<Void> syncStationSchedule(String stationName) {
        logger.info("Syncing schedule data for station: {}", stationName);
        
        try {
            // 평일, 토요일, 일요일 각각 조회
            String[] dayTypes = {"1", "2", "3"}; // 1:평일, 2:토요일, 3:일요일/공휴일
            String[] inOutTags = {"1", "2"}; // 1:진입, 2:진출
            
            for (String dayType : dayTypes) {
                for (String inOutTag : inOutTags) {
                    List<SubwayScheduleApiDto> schedules = apiClient.getStationSchedule(stationName, dayType, inOutTag)
                            .block();
                    
                    if (schedules != null) {
                        for (SubwayScheduleApiDto scheduleDto : schedules) {
                            // 시간표 데이터 처리 로직 (추후 구현)
                            logger.debug("Schedule data: {}", scheduleDto.getArriveTime());
                        }
                    }
                    
                    Thread.sleep(50); // API 호출 제한 고려
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to sync schedule for station: {}", stationName, e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 수동 데이터 동기화 트리거
     */
    public void triggerFullSync() {
        logger.info("Manual full data synchronization triggered");
        syncStationDataSync();
    }
    
    /**
     * 동기 버전의 지하철역 데이터 동기화 (테스트용)
     */
    public void syncStationDataSync() {
        logger.info("Starting subway station data synchronization (sync)...");
        
        try {
            // 전체 데이터 동기화
            int pageSize = 100;
            int currentPage = 1;
            boolean hasMore = true;
            int totalSynced = 0;
            
            while (hasMore) {
                int startIndex = (currentPage - 1) * pageSize + 1;
                int endIndex = currentPage * pageSize;
                
                logger.info("Fetching stations from API: {} to {}", startIndex, endIndex);
                List<SubwayStationApiDto> apiStations = apiClient.getAllStations(startIndex, endIndex)
                        .block();
                
                if (apiStations == null || apiStations.isEmpty()) {
                    logger.info("No more station data at page {}", currentPage);
                    hasMore = false;
                    break;
                }
                
                logger.info("Received {} stations from API (page {})", apiStations.size(), currentPage);
                
                // API 데이터를 DB 모델로 변환 및 저장
                for (SubwayStationApiDto apiStation : apiStations) {
                    try {
                        syncStation(apiStation);
                        totalSynced++;
                        if (totalSynced % 50 == 0) {
                            logger.info("Progress: {} stations synced", totalSynced);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to sync station: {}, error: {}", apiStation.getStationName(), e.getMessage(), e);
                    }
                }
                
                currentPage++;
                
                // API 호출 제한을 고려한 딜레이
                Thread.sleep(100);
            }
            
            logger.info("Subway station data synchronization completed. Total synced: {}", totalSynced);
            
        } catch (Exception e) {
            logger.error("Failed to synchronize subway station data", e);
        }
    }
}