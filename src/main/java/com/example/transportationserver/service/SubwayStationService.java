package com.example.transportationserver.service;

import com.example.transportationserver.model.SubwayStation;
import com.example.transportationserver.repository.SubwayStationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

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