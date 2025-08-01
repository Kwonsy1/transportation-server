package com.example.transportationserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * 그룹화된 근처 지하철역 조회 API 응답 DTO
 * 같은 역명이고 가까운 거리의 역들을 그룹화하여 반환
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class GroupedNearbyStationResponse {
    
    private List<GroupedNearbyStation> stations;
    
    private int totalCount;
    
    private double searchRadiusKm;
    
    private SearchCenter centerCoordinates;
    
    public GroupedNearbyStationResponse() {}
    
    public GroupedNearbyStationResponse(List<GroupedNearbyStation> stations, int totalCount, 
                                      double searchRadiusKm, double centerLat, double centerLon) {
        this.stations = stations;
        this.totalCount = totalCount;
        this.searchRadiusKm = searchRadiusKm;
        this.centerCoordinates = new SearchCenter(centerLat, centerLon);
    }
    
    // Getters and Setters
    public List<GroupedNearbyStation> getStations() { return stations; }
    public void setStations(List<GroupedNearbyStation> stations) { this.stations = stations; }
    
    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    
    public double getSearchRadiusKm() { return searchRadiusKm; }
    public void setSearchRadiusKM(double searchRadiusKm) { this.searchRadiusKm = searchRadiusKm; }
    
    public SearchCenter getCenterCoordinates() { return centerCoordinates; }
    public void setCenterCoordinates(SearchCenter centerCoordinates) { this.centerCoordinates = centerCoordinates; }
    
    /**
     * 그룹화된 근처 지하철역 정보
     */
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public static class GroupedNearbyStation {
        
        private String stationName;
        
        private List<String> lines;
        
        private Coordinates coordinates; // 그룹의 대표 좌표
        
        private Double distanceKm; // 그룹의 대표 거리
        
        private String address; // 그룹의 대표 주소
        
        private String region;
        
        private int stationCount; // 그룹에 속한 역 개수
        
        private List<StationDetail> details; // 개별 역 상세 정보
        
        public GroupedNearbyStation() {}
        
        public GroupedNearbyStation(String stationName, List<String> lines, Double latitude, Double longitude,
                                  Double distanceKm, String address, String region, int stationCount,
                                  List<StationDetail> details) {
            this.stationName = stationName;
            this.lines = lines;
            this.coordinates = new Coordinates(latitude, longitude);
            this.distanceKm = distanceKm;
            this.address = address;
            this.region = region;
            this.stationCount = stationCount;
            this.details = details;
        }
        
        // Getters and Setters
        public String getStationName() { return stationName; }
        public void setStationName(String stationName) { this.stationName = stationName; }
        
        public List<String> getLines() { return lines; }
        public void setLines(List<String> lines) { this.lines = lines; }
        
        public Coordinates getCoordinates() { return coordinates; }
        public void setCoordinates(Coordinates coordinates) { this.coordinates = coordinates; }
        
        public Double getDistanceKm() { return distanceKm; }
        public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
        
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        
        public int getStationCount() { return stationCount; }
        public void setStationCount(int stationCount) { this.stationCount = stationCount; }
        
        public List<StationDetail> getDetails() { return details; }
        public void setDetails(List<StationDetail> details) { this.details = details; }
    }
    
    /**
     * 개별 역 상세 정보
     */
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public static class StationDetail {
        
        private Long id;
        
        private String lineNumber;
        
        private String stationCode;
        
        private Coordinates coordinates;
        
        private Double distanceKm;
        
        private String subwayStationId;
        
        public StationDetail() {}
        
        public StationDetail(Long id, String lineNumber, String stationCode, Double latitude, Double longitude,
                           Double distanceKm, String subwayStationId) {
            this.id = id;
            this.lineNumber = lineNumber;
            this.stationCode = stationCode;
            this.coordinates = new Coordinates(latitude, longitude);
            this.distanceKm = distanceKm;
            this.subwayStationId = subwayStationId;
        }
        
        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        
        public String getLineNumber() { return lineNumber; }
        public void setLineNumber(String lineNumber) { this.lineNumber = lineNumber; }
        
        public String getStationCode() { return stationCode; }
        public void setStationCode(String stationCode) { this.stationCode = stationCode; }
        
        public Coordinates getCoordinates() { return coordinates; }
        public void setCoordinates(Coordinates coordinates) { this.coordinates = coordinates; }
        
        public Double getDistanceKm() { return distanceKm; }
        public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
        
        public String getSubwayStationId() { return subwayStationId; }
        public void setSubwayStationId(String subwayStationId) { this.subwayStationId = subwayStationId; }
    }
    
    /**
     * 좌표 정보
     */
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public static class Coordinates {
        
        private Double latitude;
        
        private Double longitude;
        
        public Coordinates() {}
        
        public Coordinates(Double latitude, Double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
        
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
    }
    
    /**
     * 검색 중심점 정보
     */
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public static class SearchCenter {
        
        private Double latitude;
        
        private Double longitude;
        
        public SearchCenter() {}
        
        public SearchCenter(Double latitude, Double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }
        
        public Double getLatitude() { return latitude; }
        public void setLatitude(Double latitude) { this.latitude = latitude; }
        
        public Double getLongitude() { return longitude; }
        public void setLongitude(Double longitude) { this.longitude = longitude; }
    }
}