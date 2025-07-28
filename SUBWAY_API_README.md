# 지하철 API 시스템 문서

## 개요

이 시스템은 서울시 공공데이터 API, 국토교통부 MOLIT API, OpenStreetMap 데이터를 통합하여 정규화된 지하철 정보를 제공하는 고도화된 API입니다.

## 주요 기능

### 1. 다중 데이터 소스 통합
- **서울시 API**: 기본 지하철역 이름 목록
- **국토교통부 API**: 지하철역 상세정보 (좌표, 주소, 노선)
- **OpenStreetMap**: 좌표 정보 보완

### 2. 동명역 구분 처리
- 시청역(서울) vs 시청역(대전) 자동 구분
- 지역 정보 기반 표준명 생성
- 별칭 관리 시스템

### 3. 환승역 그룹화
- 같은 역의 여러 노선을 하나의 그룹으로 관리
- 대표 좌표 산정 (좌표 우선순위 알고리즘 적용)
- 환승 정보 제공

### 4. 좌표 정보 통합
- 다중 소스 좌표 검증 및 통합
- 좌표 클러스터링 (200m 이내 동일 역 판정)
- 신뢰도 기반 대표 좌표 선정

## 시스템 아키텍처

### 데이터베이스 구조

#### Enhanced Schema (권장)
```sql
-- 역 그룹 테이블 (환승역 관리)
station_groups
├── id (그룹 ID)
├── canonical_name (표준명: 강남역(서울))
├── latitude/longitude (대표 좌표)
└── region (지역: 서울특별시)

-- 노선 테이블
subway_lines
├── line_code (노선코드: 2호선, 신분당선)
├── line_name (노선명)
└── operator (운영사)

-- 개별 역 테이블
subway_stations_enhanced
├── station_group_id (그룹 참조)
├── line_id (노선 참조)
├── station_code (역코드: 222, D7)
└── latitude/longitude (개별 좌표)

-- 별칭 테이블
station_aliases
├── station_group_id
├── alias_name (별칭)
└── alias_type (공식/일반/과거명)
```

#### Legacy Schema (현재 사용 중)
```sql
subway_stations
├── name (역명)
├── line_number (노선번호)
├── station_code (역코드)
├── latitude/longitude (좌표)
└── region/city (지역정보)
```

### 서비스 계층

#### 1. StationNameResolver
```java
// 동명역 구분 및 역명 표준화
StandardizedStation standardizeStationName(String rawName, String region, String city)
```

#### 2. CoordinateIntegrationService
```java
// 좌표 통합 및 보완
CoordinateResult determineGroupCoordinate(List<StationCoordinate> coordinates)
CoordinateResult supplementCoordinate(String stationName, String region, String city)
```

#### 3. IntegratedSubwayDataService
```java
// 전체 데이터 통합 동기화
CompletableFuture<SyncResult> performIntegratedSync()
```

## API 엔드포인트

### v1 API (Legacy)
```
GET /api/subway/stations           - 전체 역 목록
GET /api/subway/stations/{id}      - 특정 역 정보
POST /api/subway/sync              - 데이터 동기화
```

### v2 API (Enhanced - 구현 예정)
```
POST /api/v2/subway/sync/integrated           - 통합 데이터 동기화
GET  /api/v2/subway/stations/groups/search    - 역 그룹 검색 (동명역 구분)
GET  /api/v2/subway/stations/groups/{id}      - 역 그룹 상세정보
GET  /api/v2/subway/stations/transfers        - 환승역 목록
GET  /api/v2/subway/stations/nearby           - 좌표 기반 근처 역 검색
GET  /api/v2/subway/lines/{code}/stations     - 노선별 역 목록
GET  /api/v2/subway/stations/autocomplete     - 역명 자동완성
GET  /api/v2/subway/stats                     - 데이터 통계
```

## 데이터 동기화 프로세스

### 1단계: 서울시 API 데이터 수집
```java
Set<String> stationNames = seoulApiClient.getAllStationNames();
```

### 2단계: 국토교통부 API 상세정보 수집
```java
for (String stationName : stationNames) {
    List<MolitStationInfo> details = molitApiClient.getStationDetails(stationName);
    // 1초 대기 (API 호출 제한)
}
```

### 3단계: 데이터 정규화 및 그룹화
```java
// 동명역 구분
StandardizedStation standardized = nameResolver.standardizeStationName(name, region, city);

// 그룹화
StationGroup group = groupMap.computeIfAbsent(standardized.getCanonicalName(), 
    k -> new StationGroup(standardized));
```

### 4단계: 좌표 통합 및 보완
```java
// 기존 좌표들로 대표 좌표 결정
CoordinateResult result = coordinateService.determineGroupCoordinate(coordinates);

// OSM으로 보완
if (!result.isValid()) {
    result = coordinateService.supplementCoordinate(stationName, region, city);
}
```

### 5단계: Enhanced 데이터베이스 저장
```java
// station_groups, subway_lines, subway_stations_enhanced 테이블에 저장
```

## 설정 정보

### application.properties
```properties
# 서울시 API
api.korea.subway.base.url=http://openAPI.seoul.go.kr:8088
api.korea.subway.key=${KOREA_API_KEY:your_seoul_api_key}

# 국토교통부 API
api.molit.service.key=${MOLIT_SERVICE_KEY:your_molit_api_key}
```

### API 키 발급
1. **서울시 API**: https://data.seoul.go.kr
2. **국토교통부 API**: https://www.data.go.kr (지하철정보 서비스)

## 문제 해결

### 국토교통부 API 연동 이슈
1. **SERVICE_KEY_IS_NOT_REGISTERED_ERROR**
   - 공공데이터포털에서 새로운 API 키 발급 필요
   - 환경변수 MOLIT_SERVICE_KEY 설정 확인

2. **HTTP ROUTING ERROR**
   - API 키 형식 확인 (URL 인코딩 필요)
   - 서비스 활성화 상태 확인

3. **응답 파싱 오류**
   - items 필드가 문자열/객체/배열로 다양하게 옴
   - MolitApiResponse.Body.getItemsList() 메서드로 처리

### 좌표 정보 이슈
1. **좌표 없는 역**
   - OpenStreetMap으로 보완
   - 수동 좌표 입력 고려

2. **좌표 불일치**
   - 좌표 클러스터링으로 대표값 선정
   - 우선순위: MOLIT > SEOUL > OSM

### 동명역 처리
1. **지역 정보 부족**
   - 시/구 이름으로 지역 추정
   - 수동 매핑 테이블 활용

2. **표준명 생성 규칙**
   - 동명역 후보: DUPLICATE_STATION_NAMES 설정
   - 형식: "역명(지역)" (예: 시청역(서울))

## 개발 가이드

### 새로운 데이터 소스 추가
1. 새로운 Client 클래스 생성
2. CoordinateIntegrationService에 우선순위 추가
3. IntegratedSubwayDataService에 통합 로직 추가

### 동명역 추가
1. StationNameResolver.DUPLICATE_STATION_NAMES에 추가
2. 지역 매핑 규칙 확인

### API 엔드포인트 추가
1. IntegratedSubwayController에 메서드 추가
2. Swagger 문서 업데이트
3. 테스트 코드 작성

## 향후 개발 계획

### Phase 1: Enhanced Schema 구현
- [ ] Enhanced 데이터베이스 스키마 적용
- [ ] 기존 데이터 마이그레이션
- [ ] v2 API 엔드포인트 구현

### Phase 2: 고도화 기능
- [ ] 실시간 열차 정보 연동
- [ ] 지하철역 출구 정보 관리
- [ ] 주변 시설 정보 연동

### Phase 3: 성능 최적화
- [ ] 캐시 시스템 도입
- [ ] 배치 처리 최적화
- [ ] API 응답 속도 개선

## 라이선스 및 데이터 출처

- **서울시 공공데이터**: 서울열린데이터광장 이용약관
- **국토교통부 데이터**: 공공데이터포털 이용약관
- **OpenStreetMap**: ODbL 라이선스

## 연락처

시스템 관련 문의사항이나 개선 제안은 개발팀으로 연락 바랍니다.