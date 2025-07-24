package com.example.transportationserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SubwayScheduleApiDto {
    
    @JsonProperty("STATION_NM")
    private String stationName;
    
    @JsonProperty("LINE_NUM")
    private String lineNumber;
    
    @JsonProperty("UPDN_LINE")
    private String direction; // 상행/하행
    
    @JsonProperty("WEEK_TAG")
    private String dayType; // 1:평일, 2:토요일, 3:일요일/공휴일
    
    @JsonProperty("INOUT_TAG")
    private String inOutTag; // 1:진입, 2:진출
    
    @JsonProperty("ARRIVETIME")
    private String arriveTime;
    
    @JsonProperty("LEFTTIME")
    private String leftTime;
    
    @JsonProperty("STATION_CD")
    private String stationCode;
    
    @JsonProperty("TRAIN_NO")
    private String trainNumber;
    
    @JsonProperty("EXPRESS_YN")
    private String expressYn; // 급행여부
    
    @JsonProperty("LAST_YN")
    private String lastYn; // 막차여부
    
    public SubwayScheduleApiDto() {}
    
    // Getters and Setters
    public String getStationName() {
        return stationName;
    }
    
    public void setStationName(String stationName) {
        this.stationName = stationName;
    }
    
    public String getLineNumber() {
        return lineNumber;
    }
    
    public void setLineNumber(String lineNumber) {
        this.lineNumber = lineNumber;
    }
    
    public String getDirection() {
        return direction;
    }
    
    public void setDirection(String direction) {
        this.direction = direction;
    }
    
    public String getDayType() {
        return dayType;
    }
    
    public void setDayType(String dayType) {
        this.dayType = dayType;
    }
    
    public String getInOutTag() {
        return inOutTag;
    }
    
    public void setInOutTag(String inOutTag) {
        this.inOutTag = inOutTag;
    }
    
    public String getArriveTime() {
        return arriveTime;
    }
    
    public void setArriveTime(String arriveTime) {
        this.arriveTime = arriveTime;
    }
    
    public String getLeftTime() {
        return leftTime;
    }
    
    public void setLeftTime(String leftTime) {
        this.leftTime = leftTime;
    }
    
    public String getStationCode() {
        return stationCode;
    }
    
    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }
    
    public String getTrainNumber() {
        return trainNumber;
    }
    
    public void setTrainNumber(String trainNumber) {
        this.trainNumber = trainNumber;
    }
    
    public String getExpressYn() {
        return expressYn;
    }
    
    public void setExpressYn(String expressYn) {
        this.expressYn = expressYn;
    }
    
    public String getLastYn() {
        return lastYn;
    }
    
    public void setLastYn(String lastYn) {
        this.lastYn = lastYn;
    }
}