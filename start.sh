#!/bin/bash

echo "=== Transportation Server 시작 ==="
echo ""

# 프로젝트 디렉토리로 이동
cd "$(dirname "$0")"

# 현재 실행 중인 서버 확인 및 종료
if [ -f server.log ]; then
    PID=$(ps aux | grep "transportation-server" | grep -v grep | awk '{print $2}' | head -1)
    if [ ! -z "$PID" ]; then
        echo "기존 서버 프로세스 종료 중... (PID: $PID)"
        kill $PID
        sleep 3
    fi
fi

# 로그 파일 초기화
> server.log

echo "서버 시작 중..."
echo "로그는 server.log 파일에 저장됩니다."
echo ""

# 개발 프로파일로 서버 시작 (백그라운드)
SPRING_PROFILES_ACTIVE=dev nohup ./gradlew bootRun > server.log 2>&1 &

# 서버 시작 대기
echo "서버 시작을 기다리는 중..."
for i in {1..30}; do
    if curl -s http://localhost:5300/api/health > /dev/null 2>&1; then
        echo "✅ 서버가 성공적으로 시작되었습니다!"
        echo ""
        echo "📍 로컬 접속: http://localhost:5300"
        echo "📍 외부 접속: http://kkssyy.ipdisk.co.kr:5300"
        echo "📍 Swagger UI: http://localhost:5300/swagger-ui.html"
        echo "📍 H2 Console: http://localhost:5300/h2-console"
        echo ""
        echo "💡 로그 확인: tail -f server.log"
        echo "💡 서버 종료: ./stop.sh"
        exit 0
    fi
    sleep 2
    echo -n "."
done

echo ""
echo "❌ 서버 시작에 실패했습니다. 로그를 확인하세요:"
echo "tail -f server.log"
exit 1