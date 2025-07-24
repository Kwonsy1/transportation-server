package com.example.transportationserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NextTrainDto {
    
    @JsonProperty("STATION_NM")
    private String stationName;
    
    @JsonProperty("TRAIN_LINE_NM")
    private String trainLineName;
    
    @JsonProperty("SUBWAY_ID")
    private String subwayId;
    
    @JsonProperty("UPDNLINE")
    private String direction;
    
    @JsonProperty("ARVLMSG2")
    private String arrivalMessage2; // 도착정보
    
    @JsonProperty("ARVLMSG3")
    private String arrivalMessage3; // 첫번째/두번째
    
    @JsonProperty("ARVLCD")
    private String arrivalCode; // 0:진입, 1:도착, 2:출발, 3:전역출발, 4:전역진입, 5:전역도착
    
    @JsonProperty("BTRAINNO")
    private String trainNumber;
    
    @JsonProperty("BSTATN_NM")
    private String endStationName;
    
    @JsonProperty("RECPTN_DT")
    private String receptionTime;
    
    @JsonProperty("TRAIN_CO")
    private String trainCount; // 남은 열차 수
    
    public NextTrainDto() {}
    
    // Getters and Setters
    public String getStationName() {
        return stationName;
    }
    
    public void setStationName(String stationName) {
        this.stationName = stationName;
    }
    
    public String getTrainLineName() {
        return trainLineName;
    }
    
    public void setTrainLineName(String trainLineName) {
        this.trainLineName = trainLineName;
    }
    
    public String getSubwayId() {
        return subwayId;
    }
    
    public void setSubwayId(String subwayId) {
        this.subwayId = subwayId;
    }
    
    public String getDirection() {
        return direction;
    }
    
    public void setDirection(String direction) {
        this.direction = direction;
    }
    
    public String getArrivalMessage2() {
        return arrivalMessage2;
    }
    
    public void setArrivalMessage2(String arrivalMessage2) {
        this.arrivalMessage2 = arrivalMessage2;
    }
    
    public String getArrivalMessage3() {
        return arrivalMessage3;
    }
    
    public void setArrivalMessage3(String arrivalMessage3) {
        this.arrivalMessage3 = arrivalMessage3;
    }
    
    public String getArrivalCode() {
        return arrivalCode;
    }
    
    public void setArrivalCode(String arrivalCode) {
        this.arrivalCode = arrivalCode;
    }
    
    public String getTrainNumber() {
        return trainNumber;
    }
    
    public void setTrainNumber(String trainNumber) {
        this.trainNumber = trainNumber;
    }
    
    public String getEndStationName() {
        return endStationName;
    }
    
    public void setEndStationName(String endStationName) {
        this.endStationName = endStationName;
    }
    
    public String getReceptionTime() {
        return receptionTime;
    }
    
    public void setReceptionTime(String receptionTime) {
        this.receptionTime = receptionTime;
    }
    
    public String getTrainCount() {
        return trainCount;
    }
    
    public void setTrainCount(String trainCount) {
        this.trainCount = trainCount;
    }
}