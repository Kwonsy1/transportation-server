#!/bin/bash

echo "=== 컴파일 테스트 시작 ==="

# 1. 컴파일 테스트
echo "1. 컴파일 테스트..."
./gradlew clean compileJava --no-daemon

if [ $? -eq 0 ]; then
    echo "✅ 컴파일 성공!"
    
    # 2. 서버 실행 테스트
    echo "2. 서버 실행 테스트..."
    timeout 30s ./gradlew bootRun --no-daemon &
    SERVER_PID=$!
    
    # 30초 대기 후 서버 상태 확인
    sleep 15
    
    echo "3. API 테스트..."
    curl -s http://localhost:5300/api/health || echo "API 테스트 실패"
    curl -s http://localhost:5300/api/subway/test || echo "Subway API 테스트 실패"
    
    # 서버 종료
    kill $SERVER_PID 2>/dev/null
    
else
    echo "❌ 컴파일 실패!"
    exit 1
fi

echo "=== 테스트 완료 ==="