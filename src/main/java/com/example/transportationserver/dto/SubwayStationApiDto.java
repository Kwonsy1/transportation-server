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
    
    @JsonProperty("XCOORD")
    private String xCoordinate;
    
    @JsonProperty("YCOORD")
    private String yCoordinate;
    
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
    
    public String getXCoordinate() {
        return xCoordinate;
    }
    
    public void setXCoordinate(String xCoordinate) {
        this.xCoordinate = xCoordinate;
    }
    
    public String getYCoordinate() {
        return yCoordinate;
    }
    
    public void setYCoordinate(String yCoordinate) {
        this.yCoordinate = yCoordinate;
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
    
    /**
     * 안전한 위도 파싱 (YCOORD 우선, YPOINT_WGS 대체)
     */
    public double parseLatitude() {
        // YCOORD가 위도 (참고 코드 기준)
        String primaryCoord = yCoordinate;
        String fallbackCoord = latitude;
        
        double result = parseCoordinate(primaryCoord);
        if (result == 0.0) {
            result = parseCoordinate(fallbackCoord);
        }
        return result;
    }
    
    /**
     * 안전한 경도 파싱 (XCOORD 우선, XPOINT_WGS 대체)
     */
    public double parseLongitude() {
        // XCOORD가 경도 (참고 코드 기준)
        String primaryCoord = xCoordinate;
        String fallbackCoord = longitude;
        
        double result = parseCoordinate(primaryCoord);
        if (result == 0.0) {
            result = parseCoordinate(fallbackCoord);
        }
        return result;
    }
    
    /**
     * 문자열 좌표를 double로 안전하게 파싱
     */
    private double parseCoordinate(String coordinate) {
        if (coordinate == null || coordinate.trim().isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(coordinate.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
    
    /**
     * 호선 번호 정규화 (참고 코드 로직 적용)
     */
    public String getNormalizedLineNumber() {
        if (lineNumber == null || lineNumber.trim().isEmpty()) {
            return "1";
        }
        
        String line = lineNumber.trim();
        
        // 숫자로 시작하는 호선 처리 (01호선 → 1, 02호선 → 2)
        if (line.matches("^0?(\\d+).*")) {
            String number = line.replaceAll("^0?(\\d+).*", "$1");
            return number;
        }
        
        // 특수 호선 처리
        if (line.contains("경의중앙")) return "경의중앙";
        if (line.contains("분당") && !line.contains("신분당") && !line.contains("수인")) return "분당";
        if (line.contains("신분당")) return "신분당";
        if (line.contains("경춘")) return "경춘";
        if (line.contains("수인분당") || line.contains("수인")) return "수인분당";
        if (line.contains("우이신설")) return "우이신설";
        if (line.contains("서해")) return "서해";
        if (line.contains("김포")) return "김포";
        if (line.contains("신림")) return "신림";
        
        // 기타 경우 원본에서 "호선" 제거
        return line.replaceAll("호선", "").trim();
    }
    
    /**
     * 역명과 호선을 기반으로 고유한 ID 생성 (참고 코드 로직)
     */
    public String generateStationId() {
        String normalizedLine = getNormalizedLineNumber();
        String safeName = stationName != null ? stationName : "UNKNOWN";
        return String.format("SEOUL_%d_%d", 
            Math.abs(safeName.hashCode()), 
            Math.abs(normalizedLine.hashCode()));
    }
    
    /**
     * 좌표 유효성 검증
     */
    public boolean hasValidCoordinates() {
        double lat = parseLatitude();
        double lng = parseLongitude();
        return lat != 0.0 && lng != 0.0 && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
}