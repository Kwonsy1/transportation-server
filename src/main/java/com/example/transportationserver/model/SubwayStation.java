package com.example.transportationserver.model;

import java.time.LocalDateTime;
import java.util.List;

public class SubwayStation {
    
    private Long id;
    private String name;
    private String lineNumber;
    private String stationCode;
    private Double latitude;
    private Double longitude;
    private String address;
    private String externalId;
    private String region;        // 지역 (서울특별시, 경기도, 대전광역시 등)
    private String city;          // 시/구 (중구, 강남구 등)
    private String fullName;      // 전체 이름 (시청역(서울), 시청역(대전))
    private String aliases;       // 별칭들 (쉼표로 구분)
    private String dataSource;    // 데이터 출처 (SEOUL_API, MOLIT_API 등)
    private Boolean hasCoordinates; // 좌표 유무
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Related entities
    private List<SubwaySchedule> schedules;
    private List<SubwayExit> exits;
    
    public SubwayStation() {}
    
    public SubwayStation(String name, String lineNumber, String stationCode, 
                        Double latitude, Double longitude, String address) {
        this.name = name;
        this.lineNumber = lineNumber;
        this.stationCode = stationCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        this.hasCoordinates = (latitude != null && longitude != null);
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getLineNumber() { return lineNumber; }
    public void setLineNumber(String lineNumber) { this.lineNumber = lineNumber; }
    
    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }
    
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public List<SubwaySchedule> getSchedules() { return schedules; }
    public void setSchedules(List<SubwaySchedule> schedules) { this.schedules = schedules; }
    
    public List<SubwayExit> getExits() { return exits; }
    public void setExits(List<SubwayExit> exits) { this.exits = exits; }
    
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    
    public String getAliases() { return aliases; }
    public void setAliases(String aliases) { this.aliases = aliases; }
    
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    
    public Boolean getHasCoordinates() { return hasCoordinates; }
    public void setHasCoordinates(Boolean hasCoordinates) { this.hasCoordinates = hasCoordinates; }
}