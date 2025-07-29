package com.example.transportationserver.service;

import com.example.transportationserver.dto.NearbyStationResponse;
import com.example.transportationserver.model.SubwayStation;
import com.example.transportationserver.repository.SubwayStationMapper;
import com.example.transportationserver.util.CoordinateValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class SubwayStationService {
    
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
                    station.getRegion()
                ))
                .collect(Collectors.toList());
        
        return new NearbyStationResponse(stationDtos, stationDtos.size(), searchRadius, latitude, longitude);
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
}