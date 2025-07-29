package com.example.transportationserver.util;

import com.example.transportationserver.model.SubwayStation;
import com.example.transportationserver.service.MolitApiClient.MolitStationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 데이터 매핑 로직을 통합한 유틸리티 클래스
 * 중복된 데이터 변환 로직을 하나로 통합
 */
public class DataMapper {
    
    private static final Logger logger = LoggerFactory.getLogger(DataMapper.class);
    
    // 호선 번호 정규화를 위한 패턴
    private static final Pattern LINE_NUMBER_PATTERN = Pattern.compile("^0?(\\d+).*");
    private static final Pattern KOREAN_LINE_PATTERN = Pattern.compile(".*?(\\d+)호선.*");
    
    /**
     * 문자열을 Double로 안전하게 파싱
     */
    public static Double parseDouble(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        try {
            double parsed = Double.parseDouble(value.trim());
            return parsed == 0.0 ? null : parsed;
        } catch (NumberFormatException e) {
            logger.debug("숫자 파싱 실패: {}", value);
            return null;
        }
    }
    
    /**
     * 문자열을 Integer로 안전하게 파싱
     */
    public static Integer parseInt(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            logger.debug("정수 파싱 실패: {}", value);
            return null;
        }
    }
    
    /**
     * 호선 번호 정규화
     * 다양한 형태의 호선 표기를 일관된 형태로 변환
     */
    public static String normalizeLineNumber(String lineNumber) {
        if (lineNumber == null || lineNumber.trim().isEmpty()) {
            return "1";
        }
        
        String line = lineNumber.trim();
        
        // 특수 노선 처리
        if (line.contains("경의") || line.contains("중앙")) return "경의중앙";
        if (line.contains("분당")) return "분당";
        if (line.contains("수인")) return "수인";
        if (line.contains("신분당")) return "신분당";
        if (line.contains("우이")) return "우이신설";
        if (line.contains("신설")) return "우이신설";
        if (line.contains("서해")) return "서해";
        if (line.contains("김포")) return "김포골드";
        if (line.contains("의정부")) return "의정부";
        if (line.contains("에버")) return "에버라인";
        if (line.contains("GTX")) return "GTX-A";
        if (line.contains("신림")) return "신림";
        
        // 숫자로 시작하는 일반 호선 처리
        Matcher matcher = LINE_NUMBER_PATTERN.matcher(line);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        
        // "X호선" 형태 처리
        Matcher koreanMatcher = KOREAN_LINE_PATTERN.matcher(line);
        if (koreanMatcher.matches()) {
            return koreanMatcher.group(1);
        }
        
        // 숫자만 추출
        String numbersOnly = line.replaceAll("[^0-9]", "");
        if (!numbersOnly.isEmpty()) {
            return numbersOnly;
        }
        
        return "1"; // 기본값
    }
    
    /**
     * MOLIT 호선 번호를 서울교통공사 형태로 변환
     */
    public static String convertMolitLineNumber(String molitLineNumber) {
        if (molitLineNumber == null) return "1";
        
        String cleanNumber = molitLineNumber.replaceAll("[^0-9]", "");
        
        switch (cleanNumber) {
            case "1": return "1001";
            case "2": return "1002";
            case "3": return "1003";
            case "4": return "1004";
            case "5": return "1005";
            case "6": return "1006";
            case "7": return "1007";
            case "8": return "1008";
            case "9": return "1009";
            default: return cleanNumber.isEmpty() ? "1" : cleanNumber;
        }
    }
    
    /**
     * 기본 지하철역 객체 생성
     */
    public static SubwayStation createBasicStation(String stationName, String lineNumber) {
        SubwayStation station = new SubwayStation();
        station.setName(stationName);
        station.setLineNumber(normalizeLineNumber(lineNumber));
        station.setFullName(stationName);
        station.setRegion("서울특별시");
        station.setDataSource("SEOUL_API");
        station.setHasCoordinates(false);
        
        LocalDateTime now = LocalDateTime.now();
        station.setCreatedAt(now);
        station.setUpdatedAt(now);
        
        return station;
    }
    
    /**
     * MOLIT API 정보로부터 지하철역 객체 생성
     */
    public static SubwayStation createStationFromMolit(MolitStationInfo molitInfo, String region) {
        SubwayStation station = new SubwayStation();
        station.setName(molitInfo.getStationName());
        station.setLineNumber(normalizeLineNumber(molitInfo.getRouteName()));
        station.setRegion(region != null ? region : "정보없음");
        station.setCity(molitInfo.getSggName());
        station.setDataSource("MOLIT_API");
        
        // 좌표 설정
        Double latitude = molitInfo.getLatitudeAsDouble();
        Double longitude = molitInfo.getLongitudeAsDouble();
        
        if (CoordinateValidator.isValidKoreanCoordinate(latitude, longitude)) {
            station.setLatitude(latitude);
            station.setLongitude(longitude);
            station.setHasCoordinates(true);
        } else {
            station.setHasCoordinates(false);
        }
        
        LocalDateTime now = LocalDateTime.now();
        station.setCreatedAt(now);
        station.setUpdatedAt(now);
        
        return station;
    }
    
    /**
     * 역명에서 호선 정보 추출
     */
    public static String extractLineFromRouteName(String routeName) {
        if (routeName == null) return "1";
        
        if (routeName.contains("호선")) {
            Matcher matcher = KOREAN_LINE_PATTERN.matcher(routeName);
            if (matcher.matches()) {
                return matcher.group(1);
            }
        }
        
        return "1";
    }
    
    /**
     * 역명 정규화 (공백 제거, 특수문자 처리)
     */
    public static String normalizeStationName(String stationName) {
        if (stationName == null) return null;
        
        return stationName.trim()
                .replace("역", "")
                .replace(" ", "")
                .replace("(", "")
                .replace(")", "");
    }
    
    /**
     * 좌표 정보로 역 객체 업데이트
     */
    public static void updateStationCoordinates(SubwayStation station, Double latitude, Double longitude, String source) {
        if (CoordinateValidator.isValidKoreanCoordinate(latitude, longitude)) {
            station.setLatitude(latitude);
            station.setLongitude(longitude);
            station.setHasCoordinates(true);
            if (source != null) {
                station.setDataSource(source);
            }
            station.setUpdatedAt(LocalDateTime.now());
        }
    }
}