#!/bin/bash

echo "=== 지하철 데이터 동기화 ==="
echo ""

# 서버 상태 확인
if ! curl -s http://localhost:5300/api/health > /dev/null; then
    echo "❌ 서버가 실행되지 않았습니다. 서버를 먼저 시작하세요:"
    echo "./start.sh"
    exit 1
fi

echo "✅ 서버 실행 확인됨"
echo ""

# API 키 확인
if [ -z "$KOREA_API_KEY" ]; then
    echo "⚠️  경고: KOREA_API_KEY 환경변수가 설정되지 않았습니다."
    echo "서울 공공데이터 포털에서 API 키를 발급받고 설정하세요:"
    echo "export KOREA_API_KEY='your-api-key'"
    echo ""
    echo "테스트용으로 계속 진행합니다..."
else
    echo "✅ API 키 설정 확인됨"
fi

echo ""
echo "📊 데이터 동기화를 시작합니다..."
echo ""

# 1. 전체 지하철 데이터 동기화
echo "1️⃣ 전체 지하철 데이터 동기화 중..."
RESPONSE=$(curl -s -X POST http://localhost:5300/api/subway/sync/full)
echo "응답: $RESPONSE"
echo ""

# 동기화 진행 상태 확인 (로그 모니터링)
echo "2️⃣ 동기화 진행 상태 확인 중..."
echo "실시간 로그를 10초간 확인합니다..."
echo ""

# 백그라운드에서 로그 모니터링
timeout 10s tail -f server.log | grep -E "(Synced|station|error|Starting|completed)" &
LOG_PID=$!

# 10초 대기
sleep 10

# 로그 모니터링 종료
kill $LOG_PID 2>/dev/null

echo ""
echo "3️⃣ 데이터베이스 확인 중..."

# 데이터 확인 (테스트용 검색)
TEST_RESPONSE=$(curl -s "http://localhost:5300/api/subway/stations/search-grouped?stationName=강남" || echo "error")

if [[ "$TEST_RESPONSE" != "error" ]] && [[ "$TEST_RESPONSE" == *"강남"* ]]; then
    echo "✅ 성공: 지하철역 데이터가 정상적으로 저장되었습니다!"
    echo "   (테스트: 강남역 검색 결과 확인됨)"
else
    echo "⚠️  아직 데이터가 저장되지 않았거나 동기화가 진행 중일 수 있습니다."
fi

echo ""
echo "📋 확인 방법:"
echo "• 실시간 로그: ./logs.sh"
echo "• Swagger UI: http://localhost:5300/swagger-ui.html"
echo "• H2 Console: http://localhost:5300/h2-console"
echo "• 역 검색 테스트: curl 'http://localhost:5300/api/subway/stations/search-grouped?stationName=강남'"
echo "• 스테이션 ID 상태: curl 'http://localhost:5300/api/admin/subway-station-id-status'"
echo ""

# 4. 지하철역 ID 업데이트
echo "4️⃣ 지하철역 ID 업데이트 중..."
echo "MOLIT API에서 지하철역 ID를 가져와서 업데이트합니다 (1-2분 소요)"
STATION_ID_RESPONSE=$(curl -s -X POST http://localhost:5300/api/admin/update-subway-station-ids)
echo "응답: $STATION_ID_RESPONSE"
echo ""

# 5. 업데이트 상태 확인
echo "5️⃣ 업데이트 상태 확인 중..."
STATUS_RESPONSE=$(curl -s "http://localhost:5300/api/admin/subway-station-id-status")
echo "응답: $STATUS_RESPONSE"
echo ""

# 추가 정보
echo "6️⃣ 동기화 완료"
echo "전체 데이터 동기화에는 지하철역 정보, 시간표, 역 ID 업데이트가 모두 포함됩니다."

echo ""
echo "🎉 데이터 동기화 작업이 완료되었습니다!"
echo "자세한 로그는 './logs.sh' 명령으로 확인하세요."