#!/bin/bash

echo "=== Transportation Server 종료 ==="

# transportation-server 프로세스 찾기
PID=$(ps aux | grep "transportation-server" | grep -v grep | awk '{print $2}' | head -1)

if [ ! -z "$PID" ]; then
    echo "서버 프로세스 종료 중... (PID: $PID)"
    kill $PID
    
    # 종료 확인
    sleep 3
    if ps -p $PID > /dev/null 2>&1; then
        echo "강제 종료 시도 중..."
        kill -9 $PID
        sleep 2
    fi
    
    if ! ps -p $PID > /dev/null 2>&1; then
        echo "✅ 서버가 성공적으로 종료되었습니다."
    else
        echo "❌ 서버 종료에 실패했습니다."
        exit 1
    fi
else
    echo "실행 중인 서버가 없습니다."
fi

# Gradle daemon 정리
./gradlew --stop > /dev/null 2>&1

echo "💡 서버 재시작: ./start.sh"