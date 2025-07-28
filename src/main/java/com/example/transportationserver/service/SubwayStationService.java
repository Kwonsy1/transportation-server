package com.example.transportationserver.service;

import com.example.transportationserver.model.SubwayStation;
import com.example.transportationserver.repository.SubwayStationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

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
    public boolean updateStationCoordinates(Long id, Double latitude, Double longitude) {
        int updated = subwayStationMapper.updateCoordinates(id, latitude, longitude);
        return updated > 0;
    }
    
    /**
     * 역명으로 찾아서 좌표 업데이트 (같은 이름의 여러 역이 있을 수 있음)
     */
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
}