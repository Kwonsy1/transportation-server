package com.example.transportationserver.service;

import com.example.transportationserver.dto.SubwayStationApiDto;
import com.example.transportationserver.model.SubwayStation;
import com.example.transportationserver.repository.SubwayStationMapper;
import com.example.transportationserver.service.MolitApiClient.MolitStationInfo;
import com.example.transportationserver.service.OpenStreetMapClient.CoordinateResult;
import com.example.transportationserver.util.DataMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class EnhancedSubwayDataService {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedSubwayDataService.class);
    
    @Autowired
    private KoreanSubwayApiClient seoulApiClient;
    
    @Autowired
    private MolitApiClient molitApiClient;
    
    @Autowired
    private OpenStreetMapClient osmClient;
    
    @Autowired
    private SubwayStationMapper stationMapper;
    
    @Autowired
    private RateLimitService rateLimitService;
    
    /**
     * 다층 데이터 수집 메인 프로세스
     */
    @Async
    public CompletableFuture<Void> performEnhancedDataSync() {
        logger.info("=== 향상된 다층 데이터 동기화 시작 ===");
        
        try {
            // 1단계: 서울시 API에서 기본 역명 리스트 수집
            logger.info("1단계: 서울시 API에서 기본 역명 수집");
            Set<String> stationNames = collectStationNamesFromSeoul();
            logger.info("서울시 API에서 {} 개 역명 수집 완료", stationNames.size());
            
            // 2단계: 각 역에 대해 국토교통부 API로 상세정보 보완
            logger.info("2단계: 국토교통부 API로 상세정보 보완");
            Map<String, List<MolitStationInfo>> molitDataMap = collectMolitData(stationNames);
            logger.info("국토교통부 API에서 {} 개 역의 상세정보 수집", molitDataMap.size());
            
            // 3단계: 역별 데이터 통합 및 저장
            logger.info("3단계: 데이터 통합 및 중복 처리");
            List<SubwayStation> consolidatedStations = consolidateStationData(stationNames, molitDataMap);
            logger.info("{} 개 통합 역 데이터 생성", consolidatedStations.size());
            
            // 4단계: 좌표가 없는 역들에 대해 OpenStreetMap 검색 (배치 처리)
            logger.info("4단계: OpenStreetMap으로 좌표 보완");
            enrichWithCoordinates(consolidatedStations);
            
            // 5단계: 최종 데이터베이스 저장
            logger.info("5단계: 데이터베이스 저장");
            saveConsolidatedData(consolidatedStations);
            
            logger.info("=== 향상된 다층 데이터 동기화 완료 ===");
            
        } catch (Exception e) {
            logger.error("Enhanced data sync failed", e);
            throw new RuntimeException("Enhanced data sync failed", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }
    
    /**
     * 서울시 API에서 역명 리스트 수집
     */
    private Set<String> collectStationNamesFromSeoul() {
        Set<String> stationNames = new HashSet<>();
        
        try {
            // 페이징으로 전체 역 데이터 수집
            int pageSize = 1000;
            int currentPage = 1;
            boolean hasMore = true;
            
            while (hasMore) {
                int startIndex = (currentPage - 1) * pageSize + 1;
                int endIndex = currentPage * pageSize;
                
                List<SubwayStationApiDto> stations = seoulApiClient.getAllStations(startIndex, endIndex)
                        .block();
                
                if (stations == null || stations.isEmpty()) {
                    hasMore = false;
                } else {
                    stations.forEach(station -> {
                        if (station.getStationName() != null && !station.getStationName().trim().isEmpty()) {
                            stationNames.add(station.getStationName().trim());
                        }
                    });
                    currentPage++;
                    
                    // API 호출 제한 고려
                    rateLimitService.waitForSeoulApi();
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to collect station names from Seoul API", e);
        }
        
        return stationNames;
    }
    
    /**
     * 국토교통부 API로 각 역의 상세정보 수집
     */
    private Map<String, List<MolitStationInfo>> collectMolitData(Set<String> stationNames) {
        Map<String, List<MolitStationInfo>> molitDataMap = new HashMap<>();
        
        for (String stationName : stationNames) {
            try {
                List<MolitStationInfo> molitData = molitApiClient.getStationDetails(stationName).block();
                if (molitData != null && !molitData.isEmpty()) {
                    molitDataMap.put(stationName, molitData);
                }
                
                // API 호출 제한 고려
                rateLimitService.waitForMolit();
                
            } catch (Exception e) {
                logger.warn("Failed to get MOLIT data for station: {}", stationName);
            }
        }
        
        return molitDataMap;
    }
    
    /**
     * 데이터 통합 및 중복 처리
     */
    private List<SubwayStation> consolidateStationData(Set<String> stationNames, 
                                                      Map<String, List<MolitStationInfo>> molitDataMap) {
        List<SubwayStation> consolidatedStations = new ArrayList<>();
        
        for (String stationName : stationNames) {
            List<MolitStationInfo> molitDataList = molitDataMap.getOrDefault(stationName, List.of());
            
            if (molitDataList.isEmpty()) {
                // MOLIT 데이터가 없는 경우 기본 역 생성
                SubwayStation station = createBasicStation(stationName);
                consolidatedStations.add(station);
            } else {
                // MOLIT 데이터가 있는 경우 지역별로 그룹화
                Map<String, List<MolitStationInfo>> regionGroups = molitDataList.stream()
                        .collect(Collectors.groupingBy(info -> 
                                Optional.ofNullable(info.getSidoName()).orElse("알 수 없음")));
                
                for (Map.Entry<String, List<MolitStationInfo>> regionEntry : regionGroups.entrySet()) {
                    String region = regionEntry.getKey();
                    List<MolitStationInfo> regionStations = regionEntry.getValue();
                    
                    // 같은 지역 내에서도 노선별로 구분
                    for (MolitStationInfo molitInfo : regionStations) {
                        SubwayStation station = createStationFromMolit(molitInfo, region);
                        consolidatedStations.add(station);
                    }
                }
            }
        }
        
        return consolidatedStations;
    }
    
    /**
     * 기본 역 생성 (MOLIT 데이터 없는 경우)
     */
    private SubwayStation createBasicStation(String stationName) {
        return DataMapper.createBasicStation(stationName, "1");
    }
    
    /**
     * MOLIT 데이터로부터 역 생성
     */
    private SubwayStation createStationFromMolit(MolitStationInfo molitInfo, String region) {
        SubwayStation station = new SubwayStation();
        
        station.setName(molitInfo.getStationName());
        station.setRegion(region);
        station.setCity(molitInfo.getSggName());
        station.setExternalId(molitInfo.getStationId());
        station.setSubwayStationId(molitInfo.getStationId()); // MOLIT API subwayStationId 저장
        station.setAddress(Optional.ofNullable(molitInfo.getRoadAddress())
                .orElse(molitInfo.getLotAddress()));
        station.setLineNumber(molitInfo.getRouteName());
        station.setDataSource("MOLIT_API");
        
        // 좌표 설정
        if (molitInfo.hasValidCoordinates()) {
            station.setLatitude(molitInfo.getLatitudeAsDouble());
            station.setLongitude(molitInfo.getLongitudeAsDouble());
            station.setHasCoordinates(true);
        } else {
            station.setHasCoordinates(false);
        }
        
        // 전체 이름 생성 (동명 역 구분용)
        if (region != null && !region.equals("서울특별시")) {
            station.setFullName(String.format("%s(%s)", 
                    molitInfo.getStationName(), 
                    region.replace("특별시", "").replace("광역시", "")));
        } else {
            station.setFullName(molitInfo.getStationName());
        }
        
        station.setCreatedAt(LocalDateTime.now());
        station.setUpdatedAt(LocalDateTime.now());
        
        return station;
    }
    
    /**
     * OpenStreetMap으로 좌표 보완 (1초 제한 적용)
     */
    private void enrichWithCoordinates(List<SubwayStation> stations) {
        List<SubwayStation> stationsNeedingCoordinates = stations.stream()
                .filter(station -> !Boolean.TRUE.equals(station.getHasCoordinates()))
                .collect(Collectors.toList());
        
        logger.info("좌표가 필요한 역: {} 개", stationsNeedingCoordinates.size());
        
        int processed = 0;
        for (SubwayStation station : stationsNeedingCoordinates) {
            try {
                CoordinateResult result = osmClient.searchStationCoordinates(
                        station.getName(), station.getRegion()).block();
                
                if (result != null && result.isValid()) {
                    station.setLatitude(result.getLatitude());
                    station.setLongitude(result.getLongitude());
                    station.setHasCoordinates(true);
                    
                    if (station.getAddress() == null && result.getAddress() != null) {
                        station.setAddress(result.getAddress());
                    }
                    
                    logger.debug("좌표 보완 완료: {} -> lat={}, lon={}", 
                            station.getName(), result.getLatitude(), result.getLongitude());
                }
                
                processed++;
                if (processed % 10 == 0) {
                    logger.info("좌표 보완 진행: {}/{}", processed, stationsNeedingCoordinates.size());
                }
                
            } catch (Exception e) {
                logger.warn("Failed to get coordinates for station: {}", station.getName());
            }
        }
        
        logger.info("좌표 보완 완료: {}/{} 역 처리", processed, stationsNeedingCoordinates.size());
    }
    
    /**
     * 통합된 데이터를 데이터베이스에 저장
     */
    private void saveConsolidatedData(List<SubwayStation> stations) {
        int saved = 0;
        int updated = 0;
        
        for (SubwayStation station : stations) {
            try {
                // 기존 데이터 확인 (이름 + 지역으로)
                SubwayStation existing = findExistingStation(station);
                
                if (existing == null) {
                    stationMapper.insert(station);
                    saved++;
                } else {
                    // 기존 데이터 업데이트
                    updateExistingStation(existing, station);
                    stationMapper.update(existing);
                    updated++;
                }
                
            } catch (Exception e) {
                logger.error("Failed to save station: {}", station.getName(), e);
            }
        }
        
        logger.info("데이터 저장 완료 - 신규: {} 개, 업데이트: {} 개", saved, updated);
    }
    
    /**
     * 기존 역 찾기 (이름 + 지역 기준)
     */
    private SubwayStation findExistingStation(SubwayStation station) {
        try {
            List<SubwayStation> candidates = stationMapper.findByName(station.getName());
            
            for (SubwayStation candidate : candidates) {
                if (Objects.equals(candidate.getRegion(), station.getRegion()) &&
                    Objects.equals(candidate.getLineNumber(), station.getLineNumber())) {
                    return candidate;
                }
            }
        } catch (Exception e) {
            logger.debug("Error finding existing station: {}", station.getName());
        }
        
        return null;
    }
    
    /**
     * 기존 역 정보 업데이트
     */
    private void updateExistingStation(SubwayStation existing, SubwayStation newData) {
        // 좌표가 없었다면 새 좌표로 업데이트
        if (!Boolean.TRUE.equals(existing.getHasCoordinates()) && 
            Boolean.TRUE.equals(newData.getHasCoordinates())) {
            existing.setLatitude(newData.getLatitude());
            existing.setLongitude(newData.getLongitude());
            existing.setHasCoordinates(true);
        }
        
        // 주소 정보 보완
        if (existing.getAddress() == null && newData.getAddress() != null) {
            existing.setAddress(newData.getAddress());
        }
        
        // 지역/도시 정보 보완
        if (existing.getRegion() == null && newData.getRegion() != null) {
            existing.setRegion(newData.getRegion());
        }
        if (existing.getCity() == null && newData.getCity() != null) {
            existing.setCity(newData.getCity());
        }
        
        // 전체 이름 업데이트
        if (existing.getFullName() == null && newData.getFullName() != null) {
            existing.setFullName(newData.getFullName());
        }
        
        existing.setUpdatedAt(LocalDateTime.now());
    }
    
    /**
     * 수동 트리거
     */
    public void triggerEnhancedSync() {
        logger.info("Manual enhanced data synchronization triggered");
        performEnhancedDataSync();
    }
}