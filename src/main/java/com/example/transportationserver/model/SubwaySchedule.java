package com.example.transportationserver.model;

import java.time.LocalDateTime;
import java.time.LocalTime;

public class SubwaySchedule {
    
    private Long id;
    private Long stationId;
    private String direction;
    private String dayType; // weekday, saturday, sunday
    private LocalTime departureTime;
    private LocalTime arrivalTime;
    private String endStation;
    private String trainType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Related entity
    private SubwayStation station;
    
    public SubwaySchedule() {}
    
    public SubwaySchedule(Long stationId, String direction, String dayType, 
                         LocalTime departureTime, String endStation) {
        this.stationId = stationId;
        this.direction = direction;
        this.dayType = dayType;
        this.departureTime = departureTime;
        this.endStation = endStation;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getStationId() { return stationId; }
    public void setStationId(Long stationId) { this.stationId = stationId; }
    
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    
    public String getDayType() { return dayType; }
    public void setDayType(String dayType) { this.dayType = dayType; }
    
    public LocalTime getDepartureTime() { return departureTime; }
    public void setDepartureTime(LocalTime departureTime) { this.departureTime = departureTime; }
    
    public LocalTime getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(LocalTime arrivalTime) { this.arrivalTime = arrivalTime; }
    
    public String getEndStation() { return endStation; }
    public void setEndStation(String endStation) { this.endStation = endStation; }
    
    public String getTrainType() { return trainType; }
    public void setTrainType(String trainType) { this.trainType = trainType; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public SubwayStation getStation() { return station; }
    public void setStation(SubwayStation station) { this.station = station; }
}