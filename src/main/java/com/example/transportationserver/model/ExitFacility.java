package com.example.transportationserver.model;

import java.time.LocalDateTime;

public class ExitFacility {
    
    private Long id;
    private Long exitId;
    private String facilityName;
    private String facilityType; // 편의점, 카페, 병원, 은행 등
    private Integer distanceMeters;
    private Integer walkingMinutes;
    private String address;
    private String phoneNumber;
    private String operatingHours;
    private Double latitude;
    private Double longitude;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Related entity
    private SubwayExit exit;
    
    public ExitFacility() {}
    
    public ExitFacility(Long exitId, String facilityName, String facilityType, Integer distanceMeters) {
        this.exitId = exitId;
        this.facilityName = facilityName;
        this.facilityType = facilityType;
        this.distanceMeters = distanceMeters;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getExitId() { return exitId; }
    public void setExitId(Long exitId) { this.exitId = exitId; }
    
    public String getFacilityName() { return facilityName; }
    public void setFacilityName(String facilityName) { this.facilityName = facilityName; }
    
    public String getFacilityType() { return facilityType; }
    public void setFacilityType(String facilityType) { this.facilityType = facilityType; }
    
    public Integer getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Integer distanceMeters) { this.distanceMeters = distanceMeters; }
    
    public Integer getWalkingMinutes() { return walkingMinutes; }
    public void setWalkingMinutes(Integer walkingMinutes) { this.walkingMinutes = walkingMinutes; }
    
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    
    public String getOperatingHours() { return operatingHours; }
    public void setOperatingHours(String operatingHours) { this.operatingHours = operatingHours; }
    
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public SubwayExit getExit() { return exit; }
    public void setExit(SubwayExit exit) { this.exit = exit; }
}