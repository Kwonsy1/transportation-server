package com.example.transportationserver.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 좌표 유효성 검사를 위한 통합 유틸리티 클래스
 * 중복된 좌표 검증 로직을 하나로 통합
 */
public class CoordinateValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(CoordinateValidator.class);
    
    // 한국 지역 좌표 범위
    public static final double KOREA_MIN_LATITUDE = 33.0;
    public static final double KOREA_MAX_LATITUDE = 43.0;
    public static final double KOREA_MIN_LONGITUDE = 124.0;
    public static final double KOREA_MAX_LONGITUDE = 132.0;
    
    // 서울 지역 좌표 범위 (더 엄격한 검증용)
    public static final double SEOUL_MIN_LATITUDE = 37.4;
    public static final double SEOUL_MAX_LATITUDE = 37.7;
    public static final double SEOUL_MIN_LONGITUDE = 126.8;
    public static final double SEOUL_MAX_LONGITUDE = 127.2;
    
    /**
     * 좌표가 null이거나 0인지 확인
     */
    public static boolean isCoordinateEmpty(Double latitude, Double longitude) {
        return latitude == null || longitude == null || 
               latitude == 0.0 || longitude == 0.0;
    }
    
    /**
     * 좌표가 유효한 숫자 값인지 확인 (NaN, Infinite 체크)
     */
    public static boolean isCoordinateNumber(double latitude, double longitude) {
        return !Double.isNaN(latitude) && !Double.isNaN(longitude) &&
               !Double.isInfinite(latitude) && !Double.isInfinite(longitude);
    }
    
    /**
     * 좌표가 전 세계 범위 내에 있는지 확인
     */
    public static boolean isCoordinateInWorldRange(double latitude, double longitude) {
        return latitude >= -90.0 && latitude <= 90.0 &&
               longitude >= -180.0 && longitude <= 180.0;
    }
    
    /**
     * 좌표가 한국 지역 범위 내에 있는지 확인
     */
    public static boolean isCoordinateInKoreaRange(double latitude, double longitude) {
        return latitude >= KOREA_MIN_LATITUDE && latitude <= KOREA_MAX_LATITUDE &&
               longitude >= KOREA_MIN_LONGITUDE && longitude <= KOREA_MAX_LONGITUDE;
    }
    
    /**
     * 좌표가 서울 지역 범위 내에 있는지 확인
     */
    public static boolean isCoordinateInSeoulRange(double latitude, double longitude) {
        return latitude >= SEOUL_MIN_LATITUDE && latitude <= SEOUL_MAX_LATITUDE &&
               longitude >= SEOUL_MIN_LONGITUDE && longitude <= SEOUL_MAX_LONGITUDE;
    }
    
    /**
     * 종합적인 좌표 유효성 검사 (한국 지역 기준)
     */
    public static boolean isValidKoreanCoordinate(Double latitude, Double longitude) {
        if (isCoordinateEmpty(latitude, longitude)) {
            return false;
        }
        
        double lat = latitude;
        double lng = longitude;
        
        return isCoordinateNumber(lat, lng) && 
               isCoordinateInWorldRange(lat, lng) &&
               isCoordinateInKoreaRange(lat, lng);
    }
    
    /**
     * 문자열을 좌표로 안전하게 파싱
     */
    public static Double parseCoordinate(String coordinate) {
        if (coordinate == null || coordinate.trim().isEmpty()) {
            return null;
        }
        
        try {
            double parsed = Double.parseDouble(coordinate.trim());
            return parsed == 0.0 ? null : parsed;
        } catch (NumberFormatException e) {
            logger.debug("좌표 파싱 실패: {}", coordinate);
            return null;
        }
    }
    
    /**
     * 두 좌표 간의 거리 계산 (하버사인 공식)
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371; // 지구 반지름 (km)
        
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return R * c; // 거리 (km)
    }
    
    /**
     * 좌표가 지정된 반경 내에 있는지 확인
     */
    public static boolean isWithinRadius(double centerLat, double centerLon, 
                                       double targetLat, double targetLon, 
                                       double radiusKm) {
        double distance = calculateDistance(centerLat, centerLon, targetLat, targetLon);
        return distance <= radiusKm;
    }
}