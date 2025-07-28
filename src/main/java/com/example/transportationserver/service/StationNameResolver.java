package com.example.transportationserver.service;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 동명역 구분 및 역명 표준화 서비스
 */
@Service
public class StationNameResolver {
    
    // 지역별 키워드 매핑
    private static final Map<String, String> REGION_KEYWORDS = Map.of(
        "서울", "서울특별시",
        "경기", "경기도", 
        "인천", "인천광역시",
        "대전", "대전광역시",
        "대구", "대구광역시",
        "부산", "부산광역시",
        "광주", "광주광역시",
        "울산", "울산광역시"
    );
    
    // 동명역 후보 리스트 (수동 관리)
    private static final Set<String> DUPLICATE_STATION_NAMES = Set.of(
        "시청", "운동장앞", "공덕", "신설동", "왕십리", "신도림", "사당",
        "교대", "강남", "잠실", "건대입구", "홍대입구", "신촌", "이대",
        "용산", "서울역", "영등포", "구로", "금천구청", "석계", "태릉입구"
    );
    
    /**
     * 역명을 지역정보와 함께 표준화
     */
    public StandardizedStation standardizeStationName(String rawStationName, String region, String city) {
        String cleanName = cleanStationName(rawStationName);
        String canonicalName = generateCanonicalName(cleanName, region, city);
        String displayName = generateDisplayName(cleanName, region);
        
        return new StandardizedStation(
            cleanName,
            canonicalName, 
            displayName,
            determineRegion(region, city),
            city,
            isDuplicateCandidate(cleanName)
        );
    }
    
    /**
     * 역명 정제 (참고 코드 로직 적용)
     */
    public String cleanStationName(String stationName) {
        if (stationName == null) return "";
        
        return stationName
            .replaceAll("역$", "")              // 마지막 "역"만 제거 (참고 코드와 동일)
            .replaceAll("\\(\\w+\\)", "")        // 괄호 안 내용 제거 (참고 코드와 동일)
            .replaceAll("\\d+호선", "")           // 호선 번호 제거 (참고 코드 로직)
            .trim();
    }
    
    /**
     * 깨끗한 역명 생성 (StationGroup 참고 코드 로직)
     */
    public String getCleanStationName(String stationName) {
        if (stationName == null) return "";
        
        // 마지막이 "역"으로 끝나는 경우에만 제거 (참고 코드와 동일)
        if (stationName.endsWith("역")) {
            return stationName.substring(0, stationName.length() - 1);
        }
        
        return stationName
            .replaceAll("\\d+호선", "")
            .trim();
    }
    
    /**
     * 표준 역명 생성 (DB 저장용)
     */
    private String generateCanonicalName(String cleanName, String region, String city) {
        if (!isDuplicateCandidate(cleanName)) {
            return cleanName;
        }
        
        String regionSuffix = determineRegionSuffix(region, city);
        return cleanName + "(" + regionSuffix + ")";
    }
    
    /**
     * 표시용 역명 생성 (API 응답용)
     */
    private String generateDisplayName(String cleanName, String region) {
        if (!isDuplicateCandidate(cleanName)) {
            return cleanName + "역";
        }
        
        String regionSuffix = determineRegionSuffix(region, null);
        return cleanName + "역(" + regionSuffix + ")";
    }
    
    /**
     * 지역 정보 결정
     */
    private String determineRegion(String region, String city) {
        if (region != null && !region.isEmpty()) {
            return normalizeRegionName(region);
        }
        
        if (city != null && !city.isEmpty()) {
            // 시/구 이름으로 지역 추정
            return inferRegionFromCity(city);
        }
        
        return "미분류";
    }
    
    /**
     * 지역명 표준화
     */
    private String normalizeRegionName(String region) {
        for (Map.Entry<String, String> entry : REGION_KEYWORDS.entrySet()) {
            if (region.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return region;
    }
    
    /**
     * 도시명으로 지역 추정
     */
    private String inferRegionFromCity(String city) {
        if (city.contains("구") && !city.contains("시")) {
            return "서울특별시";  // 대부분의 구는 서울
        }
        if (city.contains("수원") || city.contains("성남") || city.contains("안양")) {
            return "경기도";
        }
        if (city.contains("부천") || city.contains("고양") || city.contains("용인")) {
            return "경기도";
        }
        return "미분류";
    }
    
    /**
     * 지역 접미어 결정
     */
    private String determineRegionSuffix(String region, String city) {
        String normalizedRegion = normalizeRegionName(region);
        
        switch (normalizedRegion) {
            case "서울특별시":
                return "서울";
            case "경기도":
                return city != null && !city.isEmpty() ? city : "경기";
            case "인천광역시":
                return "인천";
            case "대전광역시":
                return "대전";
            case "대구광역시":
                return "대구";
            case "부산광역시":
                return "부산";
            case "광주광역시":
                return "광주";
            case "울산광역시":
                return "울산";
            default:
                return region;
        }
    }
    
    /**
     * 동명역 후보 확인
     */
    public boolean isDuplicateCandidate(String stationName) {
        return DUPLICATE_STATION_NAMES.contains(stationName);
    }
    
    /**
     * 역 목록을 그룹화 (참고 코드 StationGrouper 로직 적용)
     */
    public Map<String, List<StationInfo>> groupStationsByName(List<StationInfo> stations) {
        Map<String, List<StationInfo>> groupedMap = new HashMap<>();
        
        for (StationInfo station : stations) {
            String cleanName = getCleanStationName(station.getStationName());
            
            groupedMap.computeIfAbsent(cleanName, k -> new ArrayList<>()).add(station);
        }
        
        return groupedMap;
    }
    
    /**
     * 스테이션 그룹 생성 (참고 코드 로직)
     */
    public List<StationGroup> createStationGroups(List<StationInfo> stations) {
        Map<String, List<StationInfo>> groupedMap = groupStationsByName(stations);
        List<StationGroup> stationGroups = new ArrayList<>();
        
        for (Map.Entry<String, List<StationInfo>> entry : groupedMap.entrySet()) {
            String stationName = entry.getKey();
            List<StationInfo> stationList = entry.getValue();
            
            // 좌표는 첫 번째 역의 좌표 사용 (참고 코드와 동일)
            StationInfo firstStation = stationList.get(0);
            
            StationGroup group = new StationGroup(
                stationName,
                stationList,
                firstStation.getLatitude(),
                firstStation.getLongitude()
            );
            
            stationGroups.add(group);
        }
        
        return stationGroups;
    }
    
    /**
     * 좌표 기반 역 그룹 판정
     */
    public boolean isSameStationGroup(double lat1, double lon1, double lat2, double lon2) {
        // 환승역으로 간주할 거리 임계값 (미터)
        final double TRANSFER_DISTANCE_THRESHOLD = 200.0;
        
        double distance = calculateDistance(lat1, lon1, lat2, lon2);
        return distance <= TRANSFER_DISTANCE_THRESHOLD;
    }
    
    /**
     * 두 좌표 간 거리 계산 (하버사인 공식)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double EARTH_RADIUS = 6371000; // 지구 반지름 (미터)
        
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                  Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                  Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS * c;
    }
    
    /**
     * 표준화된 역 정보 클래스
     */
    public static class StandardizedStation {
        private final String originalName;
        private final String canonicalName;
        private final String displayName;
        private final String region;
        private final String city;
        private final boolean isDuplicateCandidate;
        
        public StandardizedStation(String originalName, String canonicalName, String displayName, 
                                 String region, String city, boolean isDuplicateCandidate) {
            this.originalName = originalName;
            this.canonicalName = canonicalName;
            this.displayName = displayName;
            this.region = region;
            this.city = city;
            this.isDuplicateCandidate = isDuplicateCandidate;
        }
        
        // Getters
        public String getOriginalName() { return originalName; }
        public String getCanonicalName() { return canonicalName; }
        public String getDisplayName() { return displayName; }
        public String getRegion() { return region; }
        public String getCity() { return city; }
        public boolean isDuplicateCandidate() { return isDuplicateCandidate; }
    }
    
    /**
     * 스테이션 정보 인터페이스
     */
    public interface StationInfo {
        String getStationName();
        String getLineNumber();
        Double getLatitude();
        Double getLongitude();
        String getStationId();
    }
    
    /**
     * 스테이션 그룹 클래스 (참고 코드 로직)
     */
    public static class StationGroup {
        private final String stationName;
        private final List<StationInfo> stations;
        private final Double latitude;
        private final Double longitude;
        
        public StationGroup(String stationName, List<StationInfo> stations, 
                          Double latitude, Double longitude) {
            this.stationName = stationName;
            this.stations = new ArrayList<>(stations);
            this.latitude = latitude;
            this.longitude = longitude;
        }
        
        /**
         * 포함된 호선 목록 (참고 코드 로직)
         */
        public List<String> getAvailableLines() {
            return stations.stream()
                .map(StationInfo::getLineNumber)
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.toList());
        }
        
        /**
         * 대표 역 (첫 번째 역)
         */
        public StationInfo getRepresentativeStation() {
            return stations.isEmpty() ? null : stations.get(0);
        }
        
        /**
         * 특정 호선의 역 찾기
         */
        public StationInfo getStationByLine(String lineName) {
            return stations.stream()
                .filter(station -> lineName.equals(station.getLineNumber()))
                .findFirst()
                .orElse(null);
        }
        
        /**
         * 호선 개수
         */
        public int getLineCount() {
            return stations.size();
        }
        
        /**
         * 호선명 텍스트 (UI 표시용, 참고 코드 로직)
         */
        public String getLineNamesText() {
            List<String> lines = getAvailableLines();
            
            // 숫자 호선 우선 정렬
            lines.sort((a, b) -> {
                try {
                    int numA = Integer.parseInt(a.replaceAll("\\D", ""));
                    int numB = Integer.parseInt(b.replaceAll("\\D", ""));
                    return Integer.compare(numA, numB);
                } catch (NumberFormatException e) {
                    return a.compareTo(b);
                }
            });
            
            return lines.stream()
                .map(line -> {
                    // 숫자만 있는 경우 "호선" 추가, 특수 호선은 "선" 추가
                    if (line.matches("^\\d+$")) {
                        return line + "호선";
                    } else {
                        return line.endsWith("선") ? line : line + "선";
                    }
                })
                .collect(java.util.stream.Collectors.joining(", "));
        }
        
        // Getters
        public String getStationName() { return stationName; }
        public List<StationInfo> getStations() { return new ArrayList<>(stations); }
        public Double getLatitude() { return latitude; }
        public Double getLongitude() { return longitude; }
    }
}