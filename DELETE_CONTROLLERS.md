# 삭제할 컨트롤러 목록

다음 컨트롤러들은 중복되므로 삭제해주세요:

1. **DataSyncController.java** - SubwayController로 통합됨
2. **EnhancedDataSyncController.java** - SubwayController로 통합됨  
3. **MolitApiTestController.java** - SubwayController로 통합됨
4. **SubwayApiController.java** - SubwayController로 통합됨
5. **SubwayStationController.java** - SubwayController로 통합됨

## 통합된 기능

새로운 `SubwayController.java`에서 다음 기능들을 모두 제공합니다:

- 전체 데이터 동기화
- 좌표 보완 작업
- 역명 검색
- 노선별 역 목록
- 시스템 상태 확인
- 헬스체크

## 서비스 정리

다음 서비스들도 정리가 필요합니다:

1. **SubwayDataSyncService** - IntegratedSubwayDataService로 통합
2. **EnhancedSubwayDataService** - IntegratedSubwayDataService로 통합

이렇게 정리하면 코드가 훨씬 깔끔해지고 유지보수가 쉬워집니다.