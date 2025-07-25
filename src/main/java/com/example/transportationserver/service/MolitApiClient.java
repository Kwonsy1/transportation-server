package com.example.transportationserver.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
public class MolitApiClient {
    
    private static final Logger logger = LoggerFactory.getLogger(MolitApiClient.class);
    private static final String MOLIT_BASE_URL = "https://apis.data.go.kr/1613000";
    
    private final WebClient webClient;
    
    @Value("${api.molit.service-key:}")
    private String serviceKey;
    
    public MolitApiClient() {
        this.webClient = WebClient.builder()
                .baseUrl(MOLIT_BASE_URL)
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }
    
    /**
     * 지하철역 상세정보 조회
     */
    public Mono<List<MolitStationInfo>> getStationDetails(String stationName) {
        if (serviceKey == null || serviceKey.isEmpty()) {
            logger.warn("MOLIT service key not configured");
            return Mono.just(new ArrayList<MolitStationInfo>());
        }
        
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/SubwayInfoService/getKwrdFndSubwaySttnList")
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("pageNo", 1)
                        .queryParam("numOfRows", 100)
                        .queryParam("_type", "json")
                        .queryParam("subwayStationName", stationName)
                        .build())
                .retrieve()
                .bodyToMono(MolitApiResponse.class)
                .map(response -> {
                    if (response != null && response.response != null && 
                        response.response.body != null && response.response.body.items != null) {
                        return response.response.body.items;
                    }
                    return new ArrayList<MolitStationInfo>();
                })
                .doOnSuccess(result -> {
                    if (result.isEmpty()) {
                        logger.debug("No MOLIT data found for station: {}", stationName);
                    } else {
                        logger.info("Found {} MOLIT records for station: {}", result.size(), stationName);
                    }
                })
                .doOnError(error -> logger.error("Error fetching MOLIT data for {}: {}", stationName, error.getMessage()))
                .onErrorReturn(new ArrayList<MolitStationInfo>());
    }
    
    /**
     * 노선별 지하철역 조회
     */
    public Mono<List<MolitStationInfo>> getStationsByLine(String lineNumber) {
        if (serviceKey == null || serviceKey.isEmpty()) {
            return Mono.just(new ArrayList<MolitStationInfo>());
        }
        
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/SubwayInfoService/getSubwaySttnList")
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("pageNo", 1)
                        .queryParam("numOfRows", 300)
                        .queryParam("_type", "json")
                        .queryParam("subwayRouteId", convertLineNumber(lineNumber))
                        .build())
                .retrieve()
                .bodyToMono(MolitApiResponse.class)
                .map(response -> {
                    if (response != null && response.response != null && 
                        response.response.body != null && response.response.body.items != null) {
                        return response.response.body.items;
                    }
                    return new ArrayList<MolitStationInfo>();
                })
                .doOnError(error -> logger.error("Error fetching line {} data: {}", lineNumber, error.getMessage()))
                .onErrorReturn(new ArrayList<MolitStationInfo>());
    }
    
    /**
     * 노선번호를 MOLIT API 형식으로 변환
     */
    private String convertLineNumber(String lineNumber) {
        if (lineNumber == null) return "";
        
        // 기본적인 매핑 (실제 MOLIT API 문서 참조하여 정확한 값으로 수정 필요)
        switch (lineNumber.replaceAll("[^0-9]", "")) {
            case "1": return "1001";  // 1호선
            case "2": return "1002";  // 2호선
            case "3": return "1003";  // 3호선
            case "4": return "1004";  // 4호선
            case "5": return "1005";  // 5호선
            case "6": return "1006";  // 6호선
            case "7": return "1007";  // 7호선
            case "8": return "1008";  // 8호선
            case "9": return "1009";  // 9호선
            default: return lineNumber;
        }
    }
    
    /**
     * MOLIT API 응답 구조
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MolitApiResponse {
        public Response response;
        
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Response {
            public Header header;
            public Body body;
            
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class Header {
                public String resultCode;
                public String resultMsg;
            }
            
            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class Body {
                public List<MolitStationInfo> items;
                public int numOfRows;
                public int pageNo;
                public int totalCount;
            }
        }
    }
    
    /**
     * MOLIT 지하철역 정보
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MolitStationInfo {
        
        @JsonProperty("subwayStationId")
        public String stationId;
        
        @JsonProperty("subwayStationName")
        public String stationName;
        
        @JsonProperty("subwayRouteName")
        public String routeName;
        
        @JsonProperty("subwaySttnConsttDt")
        public String constructionDate;
        
        @JsonProperty("sggName")
        public String sggName;  // 시군구명
        
        @JsonProperty("sidoName")
        public String sidoName; // 시도명
        
        @JsonProperty("roadAddress")
        public String roadAddress;
        
        @JsonProperty("lotAddress")
        public String lotAddress;
        
        @JsonProperty("lon")
        public String longitude;
        
        @JsonProperty("lat")
        public String latitude;
        
        // Getters
        public String getStationId() { return stationId; }
        public String getStationName() { return stationName; }
        public String getRouteName() { return routeName; }
        public String getConstructionDate() { return constructionDate; }
        public String getSggName() { return sggName; }
        public String getSidoName() { return sidoName; }
        public String getRoadAddress() { return roadAddress; }
        public String getLotAddress() { return lotAddress; }
        public String getLongitude() { return longitude; }
        public String getLatitude() { return latitude; }
        
        /**
         * 좌표 유효성 검사
         */
        public boolean hasValidCoordinates() {
            try {
                return longitude != null && latitude != null &&
                       !longitude.trim().isEmpty() && !latitude.trim().isEmpty() &&
                       Double.parseDouble(longitude) != 0.0 && Double.parseDouble(latitude) != 0.0;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        
        /**
         * Double 타입 위도 반환
         */
        public Double getLatitudeAsDouble() {
            try {
                return latitude != null && !latitude.trim().isEmpty() ? Double.parseDouble(latitude) : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        /**
         * Double 타입 경도 반환
         */
        public Double getLongitudeAsDouble() {
            try {
                return longitude != null && !longitude.trim().isEmpty() ? Double.parseDouble(longitude) : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}