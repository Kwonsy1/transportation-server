package com.example.transportationserver.model;

import java.time.LocalDateTime;
import java.util.List;

public class SubwayExit {
    
    private Long id;
    private Long stationId;
    private String exitNumber;
    private Double latitude;
    private Double longitude;
    private String address;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Related entities
    private SubwayStation station;
    private List<BusRoute> busRoutes;
    private List<ExitFacility> facilities;
    
    public SubwayExit() {}
    
    public SubwayExit(Long stationId, String exitNumber, Double latitude, Double longitude) {
        this.stationId = stationId;
        this.exitNumber = exitNumber;
        this.latitude = latitude;
        this.longitude = longitude;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getStationId() { return stationId; }
    public void setStationId(Long stationId) { this.stationId = stationId; }
    
    public String getExitNumber() { return exitNumber; }
    public void setExitNumber(String exitNumber) { this.exitNumber = exitNumber; }
    
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public SubwayStation getStation() { return station; }
    public void setStation(SubwayStation station) { this.station = station; }
    
    public List<BusRoute> getBusRoutes() { return busRoutes; }
    public void setBusRoutes(List<BusRoute> busRoutes) { this.busRoutes = busRoutes; }
    
    public List<ExitFacility> getFacilities() { return facilities; }
    public void setFacilities(List<ExitFacility> facilities) { this.facilities = facilities; }
}