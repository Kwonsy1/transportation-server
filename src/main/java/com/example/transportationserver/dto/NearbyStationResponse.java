package com.example.transportationserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 근처 지하철역 조회 API 응답 DTO
 */
public class NearbyStationResponse {
    
    @JsonProperty("stations")
    private List<NearbyStation> stations;
    
    @JsonProperty("total_count")
    private int totalCount;
    
    @JsonProperty("search_radius_km")
    private double searchRadiusKm;
    
    @JsonProperty("center_coordinates")
    private SearchCenter centerCoordinates;
    
    public NearbyStationResponse() {}
    
    public NearbyStationResponse(List<NearbyStation> stations, int totalCount, double searchRadiusKm, double centerLat, double centerLon) {
        this.stations = stations;
        this.totalCount = totalCount;
        this.searchRadiusKm = searchRadiusKm;
        this.centerCoordinates = new SearchCenter(centerLat, centerLon);
    }
    
    // Getters and Setters
    public List<NearbyStation> getStations() { return stations; }
    public void setStations(List<NearbyStation> stations) { this.stations = stations; }
    
    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    
    public double getSearchRadiusKm() { return searchRadiusKm; }
    public void setSearchRadiusKm(double searchRadiusKm) { this.searchRadiusKm = searchRadiusKm; }
    
    public SearchCenter getCenterCoordinates() { return centerCoordinates; }
    public void setCenterCoordinates(SearchCenter centerCoordinates) { this.centerCoordinates = centerCoordinates; }
    
    /**
     * 근처 지하철역 정보
     */
    public static class NearbyStation {
        
        @JsonProperty("id")
        private Long id;
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("line_number")
        private String lineNumber;
        
        @JsonProperty("coordinates")
        private Coordinates coordinates;
        
        @JsonProperty("distance_km")
        private Double distanceKm;
        
        @JsonProperty("address")
        private String address;
        
        @JsonProperty("station_code")
        private String stationCode;
        
        @JsonProperty("region")
        private String region;
        
        public NearbyStation() {}
        
        public NearbyStation(Long id, String name, String lineNumber, Double latitude, Double longitude, 
                           Double distanceKm, String address, String stationCode, String region) {
            this.id = id;
            this.name = name;
            this.lineNumber = lineNumber;
            this.coordinates = new Coordinates(latitude, longitude);
            this.distanceKm = distanceKm;
            this.address = address;
            this.stationCode = stationCode;
            this.region = region;
        }
        
        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getLineNumber() { return lineNumber; }
        public void setLineNumber(String lineNumber) { this.lineNumber = lineNumber; }
        
        public Coordinates getCoordinates() { return coordinates; }
        public void setCoordinates(Coordinates coordinates) { this.coordinates = coordinates; }
        
        public Double getDistanceKm() { return distanceKm; }
        public void setDistanceKm(Double distanceKm) { this.distanceKm = distanceKm; }
        
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        
        public String getStationCode() { return stationCode; }
        public void setStationCode(String stationCode) { this.stationCode = stationCode; }
        
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
    }
    
    /**
     * 좌표 정보
     */
    public static class Coordinates {
        
        @JsonProperty("latitude")
        private Double latitude;
        
        @JsonProperty("longitude")
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
    public static class SearchCenter {
        
        @JsonProperty("latitude")
        private Double latitude;
        
        @JsonProperty("longitude")
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