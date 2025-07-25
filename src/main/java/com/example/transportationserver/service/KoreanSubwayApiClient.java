package com.example.transportationserver.service;

import com.example.transportationserver.dto.*;
import com.example.transportationserver.dto.SeoulApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class KoreanSubwayApiClient {
    
    private static final Logger logger = LoggerFactory.getLogger(KoreanSubwayApiClient.class);
    private final WebClient webClient;
    
    @Value("${api.korea.subway.base.url}")
    private String baseUrl;
    
    @Value("${api.korea.subway.key}")
    private String apiKey;
    
    public KoreanSubwayApiClient() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }
    
    /**
     * 지하철역 검색 API
     */
    public Mono<List<SubwayStationApiDto>> searchStations(String stationName, int startIndex, int endIndex) {
        String url = String.format("%s/%s/json/SearchInfoBySubwayNameService/%d/%d/%s",
                baseUrl, apiKey, startIndex, endIndex, stationName);
        
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(SeoulApiResponse.class)
                .<List<SubwayStationApiDto>>map(response -> {
                    if (response.getSearchInfoBySubwayNameService() != null && 
                        response.getSearchInfoBySubwayNameService().getRow() != null) {
                        return response.getSearchInfoBySubwayNameService().getRow();
                    }
                    return List.of();
                })
                .onErrorReturn(List.of());
    }
    
    /**
     * 지하철역 전체 목록 조회
     */
    public Mono<List<SubwayStationApiDto>> getAllStations(int startIndex, int endIndex) {
        String url = String.format("%s/%s/json/SearchInfoBySubwayNameService/%d/%d/",
                baseUrl, apiKey, startIndex, endIndex);
        
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(SeoulApiResponse.class)
                .<List<SubwayStationApiDto>>map(response -> {
                    if (response.getSearchInfoBySubwayNameService() != null && 
                        response.getSearchInfoBySubwayNameService().getRow() != null) {
                        return response.getSearchInfoBySubwayNameService().getRow();
                    }
                    return List.of();
                })
                .onErrorReturn(List.of());
    }
    
    /**
     * 지하철 시간표 조회 API
     */
    public Mono<List<SubwayScheduleApiDto>> getStationSchedule(String stationName, String weekTag, String inOutTag) {
        String url = String.format("%s/%s/json/SearchSTNTimeTableByIDService/1/100/%s/%s/%s",
                baseUrl, apiKey, stationName, weekTag, inOutTag);
        
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<SubwayScheduleApiDto>>() {})
                .map(response -> {
                    if (response.getResponse() != null && 
                        response.getResponse().getBody() != null &&
                        response.getResponse().getBody().getItems() != null) {
                        return response.getResponse().getBody().getItems().getItem();
                    }
                    return List.of();
                });
    }
    
    /**
     * 실시간 지하철 도착정보 API
     */
    public Mono<List<NextTrainDto>> getRealTimeArrival(String stationName) {
        String url = String.format("%s/%s/json/realtimeStationArrival/1/10/%s",
                baseUrl, apiKey, stationName);
        
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<NextTrainDto>>() {})
                .map(response -> {
                    if (response.getResponse() != null && 
                        response.getResponse().getBody() != null &&
                        response.getResponse().getBody().getItems() != null) {
                        return response.getResponse().getBody().getItems().getItem();
                    }
                    return List.of();
                });
    }
    
    /**
     * 지하철 노선별 역 정보 조회
     */
    public Mono<List<SubwayStationApiDto>> getStationsByLine(String lineNumber, int startIndex, int endIndex) {
        String url = String.format("%s/%s/json/SearchSubwayStationByLineService/%d/%d/%s",
                baseUrl, apiKey, startIndex, endIndex, lineNumber);
        
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<ApiResponse<SubwayStationApiDto>>() {})
                .map(response -> {
                    if (response.getResponse() != null && 
                        response.getResponse().getBody() != null &&
                        response.getResponse().getBody().getItems() != null) {
                        return response.getResponse().getBody().getItems().getItem();
                    }
                    return List.of();
                });
    }
}