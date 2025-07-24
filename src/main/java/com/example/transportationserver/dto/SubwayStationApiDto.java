package com.example.transportationserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SubwayStationApiDto {
    
    @JsonProperty("STATION_NM")
    private String stationName;
    
    @JsonProperty("LINE_NUM")
    private String lineNumber;
    
    @JsonProperty("STATION_CD")
    private String stationCode;
    
    @JsonProperty("FR_CODE")
    private String frCode;
    
    @JsonProperty("XPOINT_WGS")
    private String longitude;
    
    @JsonProperty("YPOINT_WGS")
    private String latitude;
    
    @JsonProperty("ADRES")
    private String address;
    
    public SubwayStationApiDto() {}
    
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
    
    public String getStationCode() {
        return stationCode;
    }
    
    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }
    
    public String getFrCode() {
        return frCode;
    }
    
    public void setFrCode(String frCode) {
        this.frCode = frCode;
    }
    
    public String getLongitude() {
        return longitude;
    }
    
    public void setLongitude(String longitude) {
        this.longitude = longitude;
    }
    
    public String getLatitude() {
        return latitude;
    }
    
    public void setLatitude(String latitude) {
        this.latitude = latitude;
    }
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
}