# Transportation Server

한국 지하철역 정보 관리 및 검색 API 서버

## 개요

이 프로젝트는 한국의 지하철역 정보를 통합 관리하고, 다양한 외부 API를 통해 역 정보를 조회할 수 있는 REST API 서버입니다.

## 주요 기능

### 📍 데이터 소스
- **서울시 공공데이터 API**: 서울 지하철역 기본 정보
- **국토교통부 MOLIT API**: 전국 지하철역 상세 정보  
- **OpenStreetMap API**: 지하철역 좌표 정보 보완

### 🚇 핵심 기능
- 지하철역 통합 검색 (이름, 호선, 지역별)
- 좌표 기반 주변 역 검색
- 외부 API 연동을 통한 실시간 정보 조회
- 자동 좌표 보완 시스템
- 데이터 동기화 및 캐싱

## 기술 스택

- **Backend**: Spring Boot 3.3.2, Java 17
- **Database**: PostgreSQL
- **ORM**: MyBatis
- **API 문서**: Swagger/OpenAPI 3
- **Build Tool**: Gradle

## 설치 및 실행

### 필수 요구사항
- Java 17 이상
- Gradle 8.0 이상
- PostgreSQL 12 이상

### 실행 방법

1. **저장소 클론**
```bash
git clone <repository-url>
cd transportation-server
```

2. **데이터베이스 설정**
```bash
# PostgreSQL 설치 및 시작
sudo apt install postgresql postgresql-contrib
sudo service postgresql start

# 데이터베이스 생성
sudo -u postgres createdb transportation_db

# 스키마 적용 (자동으로 적용됨)
```

3. **환경 설정**
```bash
# 환경 변수 설정 (선택사항)
export POSTGRES_PASSWORD=your_password
export KOREA_API_KEY=YOUR_SEOUL_API_KEY
export MOLIT_SERVICE_KEY=YOUR_MOLIT_API_KEY
```

4. **서버 실행**
```bash
# 서버 실행
./gradlew bootRun

# 또는 백그라운드 실행
nohup ./gradlew bootRun > server.log 2>&1 &
```

5. **서버 확인**
- 서버 주소: http://localhost:5300
- API 문서: http://localhost:5300/swagger-ui.html

## API 엔드포인트

### 🔍 지하철역 검색
```http
GET /api/subway/stations/search?name={역명}
GET /api/subway/stations/search?lineNumber={호선}
GET /api/subway/stations/nearby?lat={위도}&lng={경도}&radius={반경}
```

### 🌐 외부 API 연동
```http
GET /api/subway/stations/search-external?stationName={역명}
```

### 📊 시스템 정보
```http
GET /api/subway/coordinates/statistics
GET /api/subway/coordinates/missing
```

### 🧪 테스트 도구
```http
GET /api/subway/debug/osm-test?stationName={역명}
```

## 설정

### 데이터베이스 연결 설정
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/transportation_db
spring.datasource.username=postgres
spring.datasource.password=${POSTGRES_PASSWORD:postgres}
spring.datasource.driver-class-name=org.postgresql.Driver
```

### 주요 설정 파일
- `src/main/resources/application.properties`: 메인 설정

## 데이터베이스

### 데이터베이스 구조
PostgreSQL 데이터베이스에 799개의 지하철역 데이터가 저장되어 있습니다.
- 전국 지하철역 정보 (서울, 부산, 대구, 인천, 광주, 대전 등)
- 모든 역에 좌표 정보 포함
- 다중 데이터 소스 지원 (서울API, 국토교통부API, OSM)

### 주요 테이블
```sql
-- 지하철역 정보
CREATE TABLE subway_stations (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    line_number VARCHAR(20),
    station_code VARCHAR(20),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    address TEXT,
    external_id VARCHAR(50),
    region VARCHAR(50),
    city VARCHAR(50),
    full_name VARCHAR(200),
    aliases TEXT,
    data_source VARCHAR(20),
    has_coordinates BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 지하철 시간표 (확장용)
CREATE TABLE subway_schedules (...);

-- 지하철역 출구 정보 (확장용)  
CREATE TABLE subway_exits (...);
```

## 외부 API 설정

### 서울시 공공데이터 API
- 기본 키가 포함되어 있어 바로 사용 가능
- 환경변수 `KOREA_API_KEY`로 커스텀 키 설정 가능

### 국토교통부 MOLIT API
- 공공데이터포털에서 서비스키 발급 필요
- 환경변수 `MOLIT_SERVICE_KEY`로 설정

### OpenStreetMap Nominatim API
- 별도 인증 불필요
- API 요청 제한: 초당 1회

## 개발

### 프로젝트 구조
```
src/main/java/com/example/transportationserver/
├── controller/     # REST API 컨트롤러
├── service/        # 비즈니스 로직
├── repository/     # 데이터 접근 계층
├── model/          # 데이터 모델
├── config/         # 설정 클래스
└── exception/      # 예외 처리
```

### 주요 컴포넌트
- `SubwayController`: 메인 API 엔드포인트
- `IntegratedSubwayDataService`: 통합 데이터 관리
- `OpenStreetMapService`: OSM 좌표 검색
- `KoreanSubwayApiClient`: 서울시 API 클라이언트
- `MolitApiClient`: 국토교통부 API 클라이언트

## 문제 해결

### MOLIT API 오류
```
SERVICE_KEY_IS_NOT_REGISTERED_ERROR
```
- 해결: 공공데이터포털에서 유효한 서비스키 발급 후 설정

### 포트 충돌
- 기본 포트 5300이 사용 중인 경우 `server.port` 설정 변경

### 데이터베이스 연결 실패
- PostgreSQL 서비스 실행 확인: `sudo service postgresql status`
- 데이터베이스 존재 확인: `sudo -u postgres psql -l`
- 연결 권한 확인: `pg_hba.conf` 설정

## 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다.

## 기여

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## 연락처

프로젝트 관련 문의사항이 있으시면 이슈를 등록해 주세요.