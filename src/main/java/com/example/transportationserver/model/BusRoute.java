package com.example.transportationserver.model;

import java.time.LocalDateTime;

public class BusRoute {
    
    private Long id;
    private Long exitId;
    private String routeNumber;
    private String routeName;
    private String busType; // 일반, 급행, 마을버스 등
    private String startStation;
    private String endStation;
    private String firstBusTime;
    private String lastBusTime;
    private Integer intervalMinutes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Related entity
    private SubwayExit exit;
    
    public BusRoute() {}
    
    public BusRoute(Long exitId, String routeNumber, String routeName, String busType) {
        this.exitId = exitId;
        this.routeNumber = routeNumber;
        this.routeName = routeName;
        this.busType = busType;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Long getExitId() { return exitId; }
    public void setExitId(Long exitId) { this.exitId = exitId; }
    
    public String getRouteNumber() { return routeNumber; }
    public void setRouteNumber(String routeNumber) { this.routeNumber = routeNumber; }
    
    public String getRouteName() { return routeName; }
    public void setRouteName(String routeName) { this.routeName = routeName; }
    
    public String getBusType() { return busType; }
    public void setBusType(String busType) { this.busType = busType; }
    
    public String getStartStation() { return startStation; }
    public void setStartStation(String startStation) { this.startStation = startStation; }
    
    public String getEndStation() { return endStation; }
    public void setEndStation(String endStation) { this.endStation = endStation; }
    
    public String getFirstBusTime() { return firstBusTime; }
    public void setFirstBusTime(String firstBusTime) { this.firstBusTime = firstBusTime; }
    
    public String getLastBusTime() { return lastBusTime; }
    public void setLastBusTime(String lastBusTime) { this.lastBusTime = lastBusTime; }
    
    public Integer getIntervalMinutes() { return intervalMinutes; }
    public void setIntervalMinutes(Integer intervalMinutes) { this.intervalMinutes = intervalMinutes; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public SubwayExit getExit() { return exit; }
    public void setExit(SubwayExit exit) { this.exit = exit; }
}