package com.example.transportationserver.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.transportationserver.util.DataMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class MolitApiClient {
    
    private static final Logger logger = LoggerFactory.getLogger(MolitApiClient.class);
    private final WebClient webClient;
    
    @Value("${api.molit.service.key}")
    private String serviceKey;
    
    @Autowired
    public MolitApiClient(@Qualifier("molitApiWebClient") WebClient webClient) {
        this.webClient = webClient;
    }
    
    /**
     * 지하철역 상세정보 조회
     */
    public Mono<List<MolitStationInfo>> getStationDetails(String stationName) {
        if (serviceKey == null || serviceKey.isEmpty()) {
            logger.warn("MOLIT service key not configured");
            return Mono.just(new ArrayList<MolitStationInfo>());
        }
        
        logger.info("Calling MOLIT API for station: {}", stationName);
        logger.debug("Using service key: {}...{}", 
            serviceKey.substring(0, Math.min(10, serviceKey.length())), 
            serviceKey.length() > 10 ? serviceKey.substring(serviceKey.length() - 10) : "");
        
        return webClient.get()
                .uri(uriBuilder -> {
                    var finalUri = uriBuilder
                            .path("/SubwayInfoService/getKwrdFndSubwaySttnList")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("pageNo", 1)
                            .queryParam("numOfRows", 100)
                            .queryParam("_type", "json")
                            .queryParam("subwayStationName", stationName)
                            .build();
                    logger.info("Final MOLIT API URL: {}", finalUri.toString());
                    return finalUri;
                })
                    .retrieve()
                    .bodyToMono(String.class)
                    .doOnNext(rawResponse -> {
                        logger.info("Raw MOLIT API response: {}", rawResponse);
                    })
                    .flatMap(rawResponse -> {
                        try {
                            // Parse manually to see what's happening
                            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            MolitApiResponse response = mapper.readValue(rawResponse, MolitApiResponse.class);
                            
                            logger.info("Parsed response - header: {}, body: {}", 
                                response != null && response.response != null ? response.response.header : "null",
                                response != null && response.response != null && response.response.body != null ? "exists" : "null");
                            
                            if (response != null && response.response != null && response.response.body != null) {
                                List<MolitStationInfo> items = response.response.body.getItemsList();
                                logger.info("Extracted {} items from response", items.size());
                                return Mono.just(items);
                            }
                            return Mono.just(new ArrayList<MolitStationInfo>());
                        } catch (Exception e) {
                            logger.error("Error parsing MOLIT response: {}", e.getMessage());
                            return Mono.just(new ArrayList<MolitStationInfo>());
                        }
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
                    if (response != null && response.response != null && response.response.body != null) {
                        return response.response.body.getItemsList();
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
        return DataMapper.convertMolitLineNumber(lineNumber);
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
                @JsonProperty("items")
                public Object items;  // Can be List<MolitStationInfo> or empty string
                public int numOfRows;
                public int pageNo;
                public int totalCount;
                
                @SuppressWarnings("unchecked")
                public List<MolitStationInfo> getItemsList() {
                    if (items instanceof List) {
                        return (List<MolitStationInfo>) items;
                    } else if (items instanceof java.util.Map) {
                        // Sometimes items comes as {"item": [...]}
                        java.util.Map<String, Object> itemsMap = (java.util.Map<String, Object>) items;
                        Object itemList = itemsMap.get("item");
                        if (itemList instanceof List) {
                            return (List<MolitStationInfo>) itemList;
                        }
                    }
                    return new ArrayList<>();
                }
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