package com.example.transportationserver.service;

import com.example.transportationserver.dto.NearbyStationResponse;
import com.example.transportationserver.dto.GroupedStationResponse;
import com.example.transportationserver.dto.GroupedNearbyStationResponse;
import com.example.transportationserver.model.SubwayStation;
import com.example.transportationserver.repository.SubwayStationMapper;
import com.example.transportationserver.util.CoordinateValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class SubwayStationService {
    
    private static final Logger logger = LoggerFactory.getLogger(SubwayStationService.class);
    
    @Autowired
    private SubwayStationMapper subwayStationMapper;
    
    @Cacheable(value = "stations", key = "'all'")
    public List<SubwayStation> getAllStations() {
        return subwayStationMapper.findAll();
    }
    
    @Cacheable(value = "stations", key = "#id")
    public SubwayStation getStationById(Long id) {
        SubwayStation station = subwayStationMapper.findById(id);
        if (station == null) {
            throw new RuntimeException("Station not found with id: " + id);
        }
        return station;
    }
    
    @Cacheable(value = "stationSearch", key = "#name")
    public List<SubwayStation> searchStationsByName(String name) {
        return subwayStationMapper.findByName(name);
    }
    
    public List<SubwayStation> getStationsByLine(String lineNumber) {
        return subwayStationMapper.findByLineNumber(lineNumber);
    }
    
    public List<SubwayStation> getNearbyStations(Double latitude, Double longitude, Double radiusKm) {
        return subwayStationMapper.findNearbyStations(latitude, longitude, radiusKm);
    }
    
    @CacheEvict(value = {"stations", "stationSearch"}, allEntries = true)
    public SubwayStation createStation(SubwayStation station) {
        if (station.getStationCode() != null && 
            subwayStationMapper.existsByStationCode(station.getStationCode())) {
            throw new IllegalArgumentException("Station with code already exists: " + station.getStationCode());
        }
        
        LocalDateTime now = LocalDateTime.now();
        station.setCreatedAt(now);
        station.setUpdatedAt(now);
        
        subwayStationMapper.insert(station);
        return station;
    }
    
    @CacheEvict(value = {"stations", "stationSearch"}, allEntries = true)
    public SubwayStation updateStation(Long id, SubwayStation stationData) {
        SubwayStation existingStation = getStationById(id);
        
        existingStation.setName(stationData.getName());
        existingStation.setLineNumber(stationData.getLineNumber());
        existingStation.setStationCode(stationData.getStationCode());
        existingStation.setLatitude(stationData.getLatitude());
        existingStation.setLongitude(stationData.getLongitude());
        existingStation.setAddress(stationData.getAddress());
        existingStation.setExternalId(stationData.getExternalId());
        existingStation.setUpdatedAt(LocalDateTime.now());
        
        subwayStationMapper.update(existingStation);
        return existingStation;
    }
    
    @CacheEvict(value = {"stations", "stationSearch"}, allEntries = true)
    public void deleteStation(Long id) {
        if (subwayStationMapper.findById(id) == null) {
            throw new RuntimeException("Station not found with id: " + id);
        }
        subwayStationMapper.deleteById(id);
    }
    
    public SubwayStation findByExternalId(String externalId) {
        return subwayStationMapper.findByExternalId(externalId);
    }
    
    /**
     * 좌표가 없는 역들 조회
     */
    public List<SubwayStation> getStationsWithoutCoordinates() {
        return subwayStationMapper.findStationsWithoutCoordinates();
    }
    
    /**
     * 특정 역의 좌표 업데이트
     */
    @CacheEvict(value = {"stations", "stationSearch"}, allEntries = true)
    public boolean updateStationCoordinates(Long id, Double latitude, Double longitude) {
        int updated = subwayStationMapper.updateCoordinates(id, latitude, longitude);
        return updated > 0;
    }
    
    /**
     * 역명으로 찾아서 좌표 업데이트 (같은 이름의 여러 역이 있을 수 있음)
     */
    @CacheEvict(value = {"stations", "stationSearch"}, allEntries = true)
    public int updateStationCoordinatesByName(String name, String lineNumber, Double latitude, Double longitude) {
        List<SubwayStation> stations = searchStationsByName(name);
        int updatedCount = 0;
        
        for (SubwayStation station : stations) {
            // 노선 번호가 지정되었으면 해당 노선만 업데이트
            if (lineNumber != null && !lineNumber.equals(station.getLineNumber())) {
                continue;
            }
            
            boolean updated = updateStationCoordinates(station.getId(), latitude, longitude);
            if (updated) {
                updatedCount++;
            }
        }
        
        return updatedCount;
    }
    
    /**
     * 특정 역들의 좌표를 일괄 업데이트
     */
    @CacheEvict(value = {"stations", "stationSearch"}, allEntries = true)
    public Map<Long, Boolean> updateMultipleStationCoordinates(Map<Long, CoordinateUpdate> updates) {
        Map<Long, Boolean> results = new HashMap<>();
        
        for (Map.Entry<Long, CoordinateUpdate> entry : updates.entrySet()) {
            Long id = entry.getKey();
            CoordinateUpdate update = entry.getValue();
            
            boolean success = updateStationCoordinates(id, update.getLatitude(), update.getLongitude());
            results.put(id, success);
        }
        
        return results;
    }
    
    /**
     * 동대문역사문화공원역 전용 업데이트 메서드
     * (ID: 340, 224, 573에 해당하는 역들을 일괄 업데이트)
     */
    public Map<String, Object> updateDongdaemunHistoryCultureParkCoordinates(Double latitude, Double longitude) {
        Long[] targetIds = {340L, 224L, 573L};
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> updateResults = new ArrayList<>();
        int successCount = 0;
        
        for (Long id : targetIds) {
            try {
                SubwayStation station = getStationById(id);
                boolean updated = updateStationCoordinates(id, latitude, longitude);
                
                Map<String, Object> updateResult = new HashMap<>();
                updateResult.put("id", id);
                updateResult.put("name", station.getName());
                updateResult.put("lineNumber", station.getLineNumber());
                updateResult.put("updated", updated);
                updateResult.put("latitude", latitude);
                updateResult.put("longitude", longitude);
                
                if (updated) {
                    successCount++;
                    updateResult.put("status", "SUCCESS");
                } else {
                    updateResult.put("status", "FAILED");
                }
                
                updateResults.add(updateResult);
                
            } catch (Exception e) {
                Map<String, Object> updateResult = new HashMap<>();
                updateResult.put("id", id);
                updateResult.put("status", "ERROR");
                updateResult.put("error", e.getMessage());
                updateResults.add(updateResult);
            }
        }
        
        result.put("targetStation", "동대문역사문화공원");
        result.put("targetIds", targetIds);
        result.put("coordinates", Map.of("latitude", latitude, "longitude", longitude));
        result.put("totalTargets", targetIds.length);
        result.put("successCount", successCount);
        result.put("failCount", targetIds.length - successCount);
        result.put("details", updateResults);
        
        return result;
    }
    
    /**
     * 좌표 업데이트를 위한 내부 클래스
     */
    public static class CoordinateUpdate {
        private final Double latitude;
        private final Double longitude;
        
        public CoordinateUpdate(Double latitude, Double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
        
        public Double getLatitude() { return latitude; }
        public Double getLongitude() { return longitude; }
    }
    
    /**
     * 좌표 통계 정보 반환
     */
    public CoordinateStatistics getCoordinateStatistics() {
        List<SubwayStation> allStations = getAllStations();
        List<SubwayStation> stationsWithoutCoordinates = getStationsWithoutCoordinates();
        
        int total = allStations.size();
        int missing = stationsWithoutCoordinates.size();
        int hasCoordinates = total - missing;
        
        return new CoordinateStatistics(total, hasCoordinates, missing);
    }
    
    /**
     * 좌표 통계 클래스
     */
    public static class CoordinateStatistics {
        private final int total;
        private final int hasCoordinates;
        private final int missingCoordinates;
        
        public CoordinateStatistics(int total, int hasCoordinates, int missingCoordinates) {
            this.total = total;
            this.hasCoordinates = hasCoordinates;
            this.missingCoordinates = missingCoordinates;
        }
        
        public int getTotal() { return total; }
        public int getHasCoordinates() { return hasCoordinates; }
        public int getMissingCoordinates() { return missingCoordinates; }
        
        public double getCompletionRate() {
            return total > 0 ? (double) hasCoordinates / total * 100 : 0;
        }
    }
    
    /**
     * 근처 지하철역 조회 API용 메서드
     * 클라이언트에서 좌표를 주면 근처 지하철역을 거리순으로 반환
     */
    @Cacheable(value = "nearbyStations", key = "#latitude + '_' + #longitude + '_' + #radiusKm + '_' + #limit")
    public NearbyStationResponse findNearbyStations(Double latitude, Double longitude, Double radiusKm, Integer limit) {
        // 입력 값 검증
        if (!CoordinateValidator.isValidKoreanCoordinate(latitude, longitude)) {
            throw new IllegalArgumentException("유효하지 않은 좌표입니다: lat=" + latitude + ", lon=" + longitude);
        }
        
        // 기본값 설정
        Double searchRadius = radiusKm != null ? Math.min(radiusKm, 50.0) : 2.0; // 최대 50km 제한
        Integer searchLimit = limit != null ? Math.min(limit, 200) : 80; // 최대 200개 제한
        
        // 데이터베이스에서 근처 역 조회 (거리 계산 포함)
        List<SubwayStation> nearbyStations = subwayStationMapper.findNearbyStations(latitude, longitude, searchRadius);
        
        // 제한된 개수만큼 자르기
        List<SubwayStation> limitedStations = nearbyStations.stream()
                .limit(searchLimit)
                .collect(Collectors.toList());
        
        // DTO로 변환
        List<NearbyStationResponse.NearbyStation> stationDtos = limitedStations.stream()
                .map(station -> new NearbyStationResponse.NearbyStation(
                    station.getId(),
                    station.getName(),
                    station.getLineNumber(),
                    station.getLatitude(),
                    station.getLongitude(),
                    calculateDistance(latitude, longitude, station.getLatitude(), station.getLongitude()),
                    station.getAddress(),
                    station.getStationCode(),
                    station.getSubwayStationId(),
                    station.getRegion()
                ))
                .collect(Collectors.toList());
        
        return new NearbyStationResponse(stationDtos, stationDtos.size(), searchRadius, latitude, longitude);
    }
    
    /**
     * 근처 지하철역 조회 API용 메서드 (그룹화된 버전)
     * 클라이언트에서 좌표를 주면 근처 지하철역을 그룹화하여 거리순으로 반환
     */
    @Cacheable(value = "nearbyStations", key = "'grouped_' + #latitude + '_' + #longitude + '_' + #radiusKm + '_' + #limit")
    public GroupedNearbyStationResponse findNearbyStationsGrouped(Double latitude, Double longitude, Double radiusKm, Integer limit) {
        // 입력 값 검증
        if (!CoordinateValidator.isValidKoreanCoordinate(latitude, longitude)) {
            throw new IllegalArgumentException("유효하지 않은 좌표입니다: lat=" + latitude + ", lon=" + longitude);
        }
        
        // 기본값 설정
        Double searchRadius = radiusKm != null ? Math.min(radiusKm, 50.0) : 2.0; // 최대 50km 제한
        Integer searchLimit = limit != null ? Math.min(limit, 200) : 80; // 최대 200개 제한
        
        // 1. 데이터베이스에서 근처 역 조회 (개별 역)
        List<SubwayStation> nearbyStations = subwayStationMapper.findNearbyStations(latitude, longitude, searchRadius);
        
        if (nearbyStations.isEmpty()) {
            return new GroupedNearbyStationResponse(new ArrayList<>(), 0, searchRadius, latitude, longitude);
        }
        
        // 2. 거리 계산 및 역명별 그룹화
        Map<String, List<SubwayStation>> stationsByName = new HashMap<>();
        
        for (SubwayStation station : nearbyStations) {
            String stationName = station.getName() != null ? station.getName().trim() : "";
            if (stationName.isEmpty()) continue;
            
            // 거리 계산
            Double distance = calculateDistance(latitude, longitude, station.getLatitude(), station.getLongitude());
            // 임시로 distance를 저장하기 위해 address 필드 사용 (나중에 파싱)
            station.setAddress(String.valueOf(distance));
            
            stationsByName.computeIfAbsent(stationName, k -> new ArrayList<>()).add(station);
        }
        
        // 3. 각 역명 그룹을 GroupedNearbyStation으로 변환
        List<GroupedNearbyStationResponse.GroupedNearbyStation> groupedStations = new ArrayList<>();
        
        for (Map.Entry<String, List<SubwayStation>> entry : stationsByName.entrySet()) {
            String stationName = entry.getKey();
            List<SubwayStation> stations = entry.getValue();
            
            // 3-1. 좌표 기반 클러스터링 (5km 이내)
            List<List<SubwayStation>> clusters = clusterStationsByDistance(stations, 5.0);
            
            // 3-2. 각 클러스터를 GroupedNearbyStation으로 변환
            for (List<SubwayStation> cluster : clusters) {
                if (cluster.isEmpty()) continue;
                
                // 노선 정보 수집
                List<String> lines = cluster.stream()
                    .map(SubwayStation::getLineNumber)
                    .filter(line -> line != null && !line.isEmpty())
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
                
                // 대표 좌표 계산 (평균)
                List<SubwayStation> stationsWithCoords = cluster.stream()
                    .filter(s -> s.getLatitude() != null && s.getLongitude() != null)
                    .collect(Collectors.toList());
                    
                Double repLat = null, repLon = null, repDistance = null;
                if (!stationsWithCoords.isEmpty()) {
                    repLat = stationsWithCoords.stream()
                        .mapToDouble(SubwayStation::getLatitude)
                        .average()
                        .orElse(0.0);
                    repLon = stationsWithCoords.stream()
                        .mapToDouble(SubwayStation::getLongitude)
                        .average()
                        .orElse(0.0);
                    repDistance = calculateDistance(latitude, longitude, repLat, repLon);
                }
                
                // 대표 주소 및 지역
                String repAddress = cluster.stream()
                    .map(SubwayStation::getAddress)
                    .filter(addr -> addr != null && !addr.isEmpty() && !addr.matches("\\d+\\.\\d+"))
                    .findFirst()
                    .orElse(null);
                    
                String repRegion = cluster.stream()
                    .map(SubwayStation::getRegion)
                    .filter(region -> region != null && !region.isEmpty())
                    .findFirst()
                    .orElse(null);
                
                // 개별 역 상세 정보
                List<GroupedNearbyStationResponse.StationDetail> details = cluster.stream()
                    .map(station -> {
                        Double stationDistance = null;
                        try {
                            String distanceStr = station.getAddress();
                            if (distanceStr != null && distanceStr.matches("\\d+\\.\\d+")) {
                                stationDistance = Double.parseDouble(distanceStr);
                            }
                        } catch (NumberFormatException e) {
                            // 거리 파싱 실패 시 직접 계산
                            stationDistance = calculateDistance(latitude, longitude, 
                                station.getLatitude(), station.getLongitude());
                        }
                        
                        return new GroupedNearbyStationResponse.StationDetail(
                            station.getId(),
                            station.getLineNumber(),
                            station.getStationCode(),
                            station.getLatitude(),
                            station.getLongitude(),
                            stationDistance,
                            station.getSubwayStationId()
                        );
                    })
                    .collect(Collectors.toList());
                
                // GroupedNearbyStation 생성
                GroupedNearbyStationResponse.GroupedNearbyStation groupedStation = 
                    new GroupedNearbyStationResponse.GroupedNearbyStation(
                        stationName,
                        lines,
                        repLat,
                        repLon,
                        repDistance,
                        repAddress,
                        repRegion,
                        cluster.size(),
                        details
                    );
                
                groupedStations.add(groupedStation);
            }
        }
        
        // 4. 거리순 정렬 및 제한
        groupedStations.sort((a, b) -> {
            if (a.getDistanceKm() == null) return 1;
            if (b.getDistanceKm() == null) return -1;
            return Double.compare(a.getDistanceKm(), b.getDistanceKm());
        });
        
        List<GroupedNearbyStationResponse.GroupedNearbyStation> limitedStations = groupedStations.stream()
            .limit(searchLimit)
            .collect(Collectors.toList());
        
        logger.info("그룹화된 근처 역 조회 완료: {}개 그룹, 반경 {}km", limitedStations.size(), searchRadius);
        
        return new GroupedNearbyStationResponse(limitedStations, limitedStations.size(), searchRadius, latitude, longitude);
    }
    
    /**
     * 두 좌표 간의 거리 계산 (하버사인 공식)
     */
    private Double calculateDistance(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat2 == null || lon2 == null) {
            return null;
        }
        
        final int R = 6371; // 지구 반지름 (km)
        
        Double latDistance = Math.toRadians(lat2 - lat1);
        Double lonDistance = Math.toRadians(lon2 - lon1);
        
        Double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        Double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c; // 킬로미터 단위
    }
    
    /**
     * 스마트 검색: 정확한 매칭을 우선시하는 검색
     * "강남" 검색 시 "강남역"만 반환하고 "강남구청역"은 제외
     */
    @Cacheable(value = "stationSearch", key = "'smart_' + #name")
    public List<SubwayStation> searchStationsSmart(String name) {
        String searchTerm = name != null ? name.trim() : "";
        
        if (searchTerm.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 1순위: 높은 우선순위 검색 시도 (정확 매칭 + "역" 추가)
        List<SubwayStation> highPriorityResults = subwayStationMapper.findByHighPrioritySearch(searchTerm);
        
        if (!highPriorityResults.isEmpty()) {
            logger.info("스마트 검색 '{}': 높은 우선순위로 {}개 역 발견", searchTerm, highPriorityResults.size());
            return highPriorityResults;
        }
        
        // 2순위: 전체 스마트 검색 (시작 매칭 포함)
        List<SubwayStation> smartResults = subwayStationMapper.findBySmartSearch(searchTerm);
        
        // 상위 점수 결과만 반환 (80점 이상)
        List<SubwayStation> filteredResults = smartResults.stream()
            .limit(10) // 최대 10개로 제한
            .collect(Collectors.toList());
        
        logger.info("스마트 검색 '{}': 전체 검색으로 {}개 역 발견", searchTerm, filteredResults.size());
        return filteredResults;
    }
    
    /**
     * 역명으로 검색한 결과를 그룹화하여 반환 (개선된 스마트 검색 사용)
     * 같은 이름이고 5km 이내에 있는 역들을 하나로 그룹화
     */
    @Cacheable(value = "stationSearch", key = "'smart_grouped_' + #name")
    public List<GroupedStationResponse> searchStationsGroupedSmart(String name) {
        // 1. 스마트 검색으로 관련도 높은 역들만 조회
        List<SubwayStation> allStations = searchStationsSmart(name);
        
        if (allStations.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 2. 역명별로 그룹화 (정확한 역명 매칭)
        Map<String, List<SubwayStation>> stationsByName = allStations.stream()
            .collect(Collectors.groupingBy(station -> 
                station.getName() != null ? station.getName().trim() : ""));
        
        List<GroupedStationResponse> groupedResults = new ArrayList<>();
        
        // 3. 각 역명 그룹에 대해 좌표 기반 클러스터링 수행
        for (Map.Entry<String, List<SubwayStation>> entry : stationsByName.entrySet()) {
            String stationName = entry.getKey();
            List<SubwayStation> stations = entry.getValue();
            
            if (stationName.isEmpty()) {
                continue; // 역명이 없는 경우 건너뛰기
            }
            
            // 좌표 기반으로 5km 이내 그룹 생성
            List<List<SubwayStation>> clusters = clusterStationsByDistance(stations, 5.0);
            
            // 각 클러스터를 GroupedStationResponse로 변환
            for (List<SubwayStation> cluster : clusters) {
                if (!cluster.isEmpty()) {
                    groupedResults.add(new GroupedStationResponse(stationName, cluster));
                }
            }
        }
        
        // 4. 결과를 역명 기준으로 정렬
        groupedResults.sort((a, b) -> a.getStationName().compareTo(b.getStationName()));
        
        return groupedResults;
    }
    
    /**
     * 역명으로 검색한 결과를 그룹화하여 반환 (기존 로직 - 호환성 유지)
     * 같은 이름이고 5km 이내에 있는 역들을 하나로 그룹화
     */
    @Cacheable(value = "stationSearch", key = "'grouped_' + #name")
    public List<GroupedStationResponse> searchStationsGrouped(String name) {
        // 1. 기본 검색으로 모든 매칭 역 조회 (기존 로직)
        List<SubwayStation> allStations = searchStationsByName(name);
        
        if (allStations.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 2. 역명별로 그룹화 (정확한 역명 매칭)
        Map<String, List<SubwayStation>> stationsByName = allStations.stream()
            .collect(Collectors.groupingBy(station -> 
                station.getName() != null ? station.getName().trim() : ""));
        
        List<GroupedStationResponse> groupedResults = new ArrayList<>();
        
        // 3. 각 역명 그룹에 대해 좌표 기반 클러스터링 수행
        for (Map.Entry<String, List<SubwayStation>> entry : stationsByName.entrySet()) {
            String stationName = entry.getKey();
            List<SubwayStation> stations = entry.getValue();
            
            if (stationName.isEmpty()) {
                continue; // 역명이 없는 경우 건너뛰기
            }
            
            // 좌표 기반으로 5km 이내 그룹 생성
            List<List<SubwayStation>> clusters = clusterStationsByDistance(stations, 5.0);
            
            // 각 클러스터를 GroupedStationResponse로 변환
            for (List<SubwayStation> cluster : clusters) {
                if (!cluster.isEmpty()) {
                    groupedResults.add(new GroupedStationResponse(stationName, cluster));
                }
            }
        }
        
        // 4. 결과를 역명 기준으로 정렬
        groupedResults.sort((a, b) -> a.getStationName().compareTo(b.getStationName()));
        
        return groupedResults;
    }
    
    /**
     * 같은 이름의 역들을 거리 기준으로 클러스터링
     * @param stations 같은 이름의 역들
     * @param maxDistanceKm 최대 거리 (km)
     * @return 클러스터링된 역 그룹들
     */
    private List<List<SubwayStation>> clusterStationsByDistance(List<SubwayStation> stations, double maxDistanceKm) {
        List<List<SubwayStation>> clusters = new ArrayList<>();
        List<SubwayStation> remaining = new ArrayList<>(stations);
        
        while (!remaining.isEmpty()) {
            // 첫 번째 역을 기준으로 새 클러스터 시작
            SubwayStation baseStation = remaining.remove(0);
            List<SubwayStation> currentCluster = new ArrayList<>();
            currentCluster.add(baseStation);
            
            // 좌표가 없는 역은 단독 클러스터로 처리
            if (baseStation.getLatitude() == null || baseStation.getLongitude() == null) {
                clusters.add(currentCluster);
                continue;
            }
            
            // 나머지 역들 중에서 maxDistanceKm 이내에 있는 역들을 같은 클러스터에 추가
            List<SubwayStation> toRemove = new ArrayList<>();
            for (SubwayStation station : remaining) {
                if (station.getLatitude() != null && station.getLongitude() != null) {
                    double distance = calculateDistance(
                        baseStation.getLatitude(), baseStation.getLongitude(),
                        station.getLatitude(), station.getLongitude()
                    );
                    
                    if (distance <= maxDistanceKm) {
                        currentCluster.add(station);
                        toRemove.add(station);
                    }
                }
            }
            
            // 클러스터에 추가된 역들을 remaining에서 제거
            remaining.removeAll(toRemove);
            clusters.add(currentCluster);
        }
        
        return clusters;
    }
    
    /**
     * 단일 역명에 대한 상세 그룹 정보 조회
     * 특정 역명의 모든 변형과 인근 역들을 포함하여 그룹화
     */
    @Cacheable(value = "stationSearch", key = "'detailed_grouped_' + #exactName")
    public GroupedStationResponse getDetailedGroupedStation(String exactName) {
        // 정확한 역명으로 검색
        List<SubwayStation> stations = subwayStationMapper.findByName(exactName);
        
        if (stations.isEmpty()) {
            return null;
        }
        
        // 같은 이름의 모든 역들을 하나의 그룹으로 처리 (거리 관계없이)
        // 하지만 실제로는 좌표 기반 클러스터링 적용
        List<List<SubwayStation>> clusters = clusterStationsByDistance(stations, 5.0);
        
        // 가장 큰 클러스터를 메인 그룹으로 반환 (역 개수가 가장 많은 것)
        return clusters.stream()
            .max((cluster1, cluster2) -> Integer.compare(cluster1.size(), cluster2.size()))
            .map(mainCluster -> new GroupedStationResponse(exactName, mainCluster))
            .orElse(null);
    }
}