#!/bin/bash

echo "=== 서울시 공공데이터 API 연동 테스트 ==="
echo ""

# 1. 환경변수 설정 (sample 키 사용)
export KOREA_API_KEY="sample"
echo "1️⃣ API 키 설정: $KOREA_API_KEY"

# 2. 서버 재시작 (환경변수 적용)
echo "2️⃣ 서버 재시작 중..."
./restart.sh > /dev/null 2>&1

# 3. 서버 시작 대기
echo "3️⃣ 서버 시작 대기 중..."
for i in {1..30}; do
    if curl -s http://localhost:5300/api/health > /dev/null 2>&1; then
        echo "✅ 서버 준비 완료"
        break
    fi
    sleep 1
    echo -n "."
done

echo ""
echo "4️⃣ 서울시 API 직접 테스트:"
echo "URL: http://openAPI.seoul.go.kr:8088/sample/json/SearchInfoBySubwayNameService/1/3/"
curl -s "http://openAPI.seoul.go.kr:8088/sample/json/SearchInfoBySubwayNameService/1/3/" | head -3
echo ""

echo ""
echo "5️⃣ 서버를 통한 데이터 동기화 테스트:"
SYNC_RESULT=$(curl -s -X POST http://localhost:5300/api/sync/stations)
echo "동기화 응답: $SYNC_RESULT"

echo ""
echo "6️⃣ 로그 확인 (최근 10줄):"
tail -10 server.log | grep -E "(ERROR|INFO|synced)"

echo ""
echo "7️⃣ 데이터베이스 상태 확인:"
curl -s "http://localhost:5300/api/stations?page=0&size=1" | head -1

echo ""
echo "🔍 문제 진단:"
echo "- API 키가 sample로 설정되어 있으면 테스트 데이터 사용 가능"
echo "- 실제 운영 시에는 서울시 공공데이터포털에서 API 키 발급 필요"
echo "- 현재 API 응답 구조는 수정 완료"