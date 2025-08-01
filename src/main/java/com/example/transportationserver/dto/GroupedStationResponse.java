package com.example.transportationserver.dto;

import com.example.transportationserver.model.SubwayStation;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 그룹화된 지하철역 정보를 담는 DTO
 * 같은 이름이고 5km 이내에 있는 역들을 하나로 그룹화
 */
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class GroupedStationResponse {
    
    private String stationName;
    private List<String> lines;           // 이 역이 속한 모든 노선
    private Double representativeLatitude;  // 대표 좌표 (평균 또는 첫 번째)
    private Double representativeLongitude;
    private String representativeAddress;   // 대표 주소
    private List<StationDetail> details;   // 개별 역 상세 정보
    private int stationCount;              // 그룹에 속한 역 개수
    private String region;                 // 대표 지역
    
    public GroupedStationResponse() {}
    
    public GroupedStationResponse(String stationName, List<SubwayStation> stations) {
        this.stationName = stationName;
        this.stationCount = stations.size();
        
        // 노선 정보 수집 (중복 제거)
        this.lines = stations.stream()
            .map(SubwayStation::getLineNumber)
            .filter(line -> line != null && !line.isEmpty())
            .distinct()
            .sorted()
            .collect(Collectors.toList());
        
        // 좌표가 있는 역들 중에서 대표 좌표 계산 (평균)
        List<SubwayStation> stationsWithCoords = stations.stream()
            .filter(s -> s.getLatitude() != null && s.getLongitude() != null)
            .collect(Collectors.toList());
            
        if (!stationsWithCoords.isEmpty()) {
            this.representativeLatitude = stationsWithCoords.stream()
                .mapToDouble(SubwayStation::getLatitude)
                .average()
                .orElse(0.0);
            this.representativeLongitude = stationsWithCoords.stream()
                .mapToDouble(SubwayStation::getLongitude)
                .average()
                .orElse(0.0);
        }
        
        // 대표 주소 및 지역 (첫 번째 유효한 값 사용)
        this.representativeAddress = stations.stream()
            .map(SubwayStation::getAddress)
            .filter(addr -> addr != null && !addr.isEmpty())
            .findFirst()
            .orElse(null);
            
        this.region = stations.stream()
            .map(SubwayStation::getRegion)
            .filter(region -> region != null && !region.isEmpty())
            .findFirst()
            .orElse(null);
        
        // 개별 역 상세 정보
        this.details = stations.stream()
            .map(StationDetail::new)
            .collect(Collectors.toList());
    }
    
    // Getters and Setters
    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }
    
    public List<String> getLines() { return lines; }
    public void setLines(List<String> lines) { this.lines = lines; }
    
    public Double getRepresentativeLatitude() { return representativeLatitude; }
    public void setRepresentativeLatitude(Double representativeLatitude) { this.representativeLatitude = representativeLatitude; }
    
    public Double getRepresentativeLongitude() { return representativeLongitude; }
    public void setRepresentativeLongitude(Double representativeLongitude) { this.representativeLongitude = representativeLongitude; }
    
    public String getRepresentativeAddress() { return representativeAddress; }
    public void setRepresentativeAddress(String representativeAddress) { this.representativeAddress = representativeAddress; }
    
    public List<StationDetail> getDetails() { return details; }
    public void setDetails(List<StationDetail> details) { this.details = details; }
    
    public int getStationCount() { return stationCount; }
    public void setStationCount(int stationCount) { this.stationCount = stationCount; }
    
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    
    /**
     * 개별 역 상세 정보를 담는 내부 클래스
     */
    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public static class StationDetail {
        private Long id;
        private String lineNumber;
        private String stationCode;
        private Double latitude;
        private Double longitude;
        private String address;
        private String subwayStationId;
        private String dataSource;
        
        public StationDetail() {}
        
        public StationDetail(SubwayStation station) {
            this.id = station.getId();
            this.lineNumber = station.getLineNumber();
            this.stationCode = station.getStationCode();
            this.latitude = station.getLatitude();
            this.longitude = station.getLongitude();
            this.address = station.getAddress();
            this.subwayStationId = station.getSubwayStationId();
            this.dataSource = station.getDataSource();
        }
        
        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        
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
        
        public String getSubwayStationId() { return subwayStationId; }
        public void setSubwayStationId(String subwayStationId) { this.subwayStationId = subwayStationId; }
        
        public String getDataSource() { return dataSource; }
        public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    }
}