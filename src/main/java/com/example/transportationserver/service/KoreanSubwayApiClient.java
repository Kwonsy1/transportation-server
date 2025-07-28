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

import java.util.ArrayList;
import java.util.List;

@Service
public class KoreanSubwayApiClient {
    
    private static final Logger logger = LoggerFactory.getLogger(KoreanSubwayApiClient.class);
    private final WebClient webClient;
    
    @Value("${api.korea.subway.base.url:http://openAPI.seoul.go.kr:8088}")
    private String baseUrl;
    
    @Value("${api.korea.subway.key}")
    private String apiKey;
    
    public KoreanSubwayApiClient() {
        this.webClient = WebClient.builder()
                .defaultHeader("User-Agent", "Transportation-Server/1.0")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Accept-Charset", "UTF-8")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
        
        logger.info("한국 지하철 API 클라이언트 초기화 완료");
    }
    
    /**
     * 지하철역 검색 API (참고 코드 에러 핸들링 패턴 적용)
     */
    public Mono<List<SubwayStationApiDto>> searchStations(String stationName, int startIndex, int endIndex) {
        String url = String.format("%s/%s/json/SearchInfoBySubwayNameService/%d/%d/%s",
                baseUrl, apiKey, startIndex, endIndex, stationName);
        
        logger.info("서울 지하철 API 요청 URL: {}", url);
        
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(SeoulApiResponse.class)
                .<List<SubwayStationApiDto>>map(response -> {
                    logger.debug("서울 지하철 API 응답: {}", response);
                    
                    if (response.getSearchInfoBySubwayNameService() != null && 
                        response.getSearchInfoBySubwayNameService().getRow() != null) {
                        
                        List<SubwayStationApiDto> stations = response.getSearchInfoBySubwayNameService().getRow();
                        logger.info("서울 지하철 API에서 {}({}~{}) {}(개 역 조회 성공", 
                                   stationName, startIndex, endIndex, stations.size());
                        return stations;
                    }
                    
                    logger.warn("서울 지하철 API 응답에 데이터가 없음: {}", stationName);
                    return List.of();
                })
                .onErrorResume(error -> {
                    logger.error("서울 지하철 API 오류: {} - {}", stationName, error.getMessage());
                    return Mono.just(new ArrayList<SubwayStationApiDto>());
                });
    }
    
    /**
     * 지하철역 전체 목록 조회 (참고 코드 로깅 패턴)
     */
    public Mono<List<SubwayStationApiDto>> getAllStations(int startIndex, int endIndex) {
        String url = String.format("%s/%s/json/SearchInfoBySubwayNameService/%d/%d/",
                baseUrl, apiKey, startIndex, endIndex);
        
        logger.info("서울 지하철 API 전체 목록 요청 ({}~{}): {}", startIndex, endIndex, url);
        
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(SeoulApiResponse.class)
                .<List<SubwayStationApiDto>>map(response -> {
                    if (response.getSearchInfoBySubwayNameService() != null && 
                        response.getSearchInfoBySubwayNameService().getRow() != null) {
                        
                        List<SubwayStationApiDto> stations = response.getSearchInfoBySubwayNameService().getRow();
                        logger.info("서울 지하철 API에서 {}~{} 범위 {}(개 역 조회 성공", 
                                   startIndex, endIndex, stations.size());
                        return stations;
                    }
                    
                    logger.warn("서울 지하철 API 응답에 데이터가 없음 ({}~{})", startIndex, endIndex);
                    return List.of();
                })
                .onErrorResume(error -> {
                    logger.error("서울 지하철 API 전체 목록 조회 오류 ({}~{}): {}", 
                               startIndex, endIndex, error.getMessage(), error);
                    return Mono.just(new ArrayList<SubwayStationApiDto>());
                });
    }
    
    /**
     * 지하철 시간표 조회 API (임시 비활성화)
     */
    public Mono<List<SubwayScheduleApiDto>> getStationSchedule(String stationName, String weekTag, String inOutTag) {
        logger.info("시간표 조회 API는 현재 비활성화됨: {}", stationName);
        return Mono.just(new ArrayList<SubwayScheduleApiDto>());
    }
    
    /**
     * 실시간 지하철 도착정보 API (임시 비활성화)
     */
    public Mono<List<NextTrainDto>> getRealTimeArrival(String stationName) {
        logger.info("실시간 도착정보 API는 현재 비활성화됨: {}", stationName);
        return Mono.just(new ArrayList<NextTrainDto>());
    }
    
    /**
     * 지하철 노선별 역 정보 조회 (임시 비활성화)
     */
    public Mono<List<SubwayStationApiDto>> getStationsByLine(String lineNumber, int startIndex, int endIndex) {
        logger.info("노선별 역 조회 API는 현재 비활성화됨: {}호선", lineNumber);
        return Mono.just(new ArrayList<SubwayStationApiDto>());
    }
}