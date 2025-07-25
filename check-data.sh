#!/bin/bash

echo "=== 데이터베이스 상태 확인 ==="
echo ""

# 서버 상태 확인
if ! curl -s http://localhost:5300/api/health > /dev/null; then
    echo "❌ 서버가 실행되지 않았습니다."
    exit 1
fi

echo "✅ 서버 상태: 정상"
echo ""

# API 테스트
echo "📊 데이터 현황:"
echo ""

# 지하철역 수
echo "🚇 지하철역 개수:"
STATIONS_RESPONSE=$(curl -s "http://localhost:5300/api/stations?page=0&size=1")
if echo "$STATIONS_RESPONSE" | grep -q "totalElements"; then
    STATION_COUNT=$(echo "$STATIONS_RESPONSE" | grep -o '"totalElements":[0-9]*' | cut -d':' -f2)
    echo "   총 $STATION_COUNT 개 저장됨"
else
    echo "   데이터 없음 또는 오류"
fi

echo ""

# 샘플 역 정보 조회
echo "📍 샘플 지하철역 정보 (상위 5개):"
SAMPLE_STATIONS=$(curl -s "http://localhost:5300/api/stations?page=0&size=5")
if echo "$SAMPLE_STATIONS" | grep -q "content"; then
    echo "$SAMPLE_STATIONS" | jq -r '.content[] | "   • \(.name) (\(.lineNumber)호선) - 위도: \(.latitude), 경도: \(.longitude)"' 2>/dev/null || echo "   JSON 파싱 오류"
else
    echo "   샘플 데이터 없음"
fi

echo ""
echo "🔍 특정 역 검색 테스트:"
echo "역명을 입력하세요 (예: 강남역, 홍대입구역):"
read -t 10 SEARCH_STATION

if [ ! -z "$SEARCH_STATION" ]; then
    echo "🔎 '$SEARCH_STATION' 검색 결과:"
    SEARCH_RESULT=$(curl -s "http://localhost:5300/api/stations/search?name=$SEARCH_STATION")
    if echo "$SEARCH_RESULT" | grep -q "name"; then
        echo "$SEARCH_RESULT" | jq -r '.[] | "   • \(.name) (\(.lineNumber)호선) - \(.address)"' 2>/dev/null || echo "   JSON 파싱 오류"
    else
        echo "   검색 결과 없음"
    fi
else
    echo "검색을 건너뜁니다."
fi

echo ""
echo "🌐 접속 URL:"
echo "• REST API: http://localhost:5300/api/stations"
echo "• Swagger UI: http://localhost:5300/swagger-ui.html"
echo "• H2 Console: http://localhost:5300/h2-console"
echo "  - JDBC URL: jdbc:h2:mem:testdb"
echo "  - Username: sa"
echo "  - Password: (빈 값)"

echo ""
echo "🔧 관리 명령어:"
echo "• 데이터 동기화: ./sync-data.sh"
echo "• 서버 로그: ./logs.sh"
echo "• 서버 재시작: ./restart.sh"