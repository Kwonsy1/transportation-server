#!/bin/bash

echo "=== 서버 컴파일 및 실행 테스트 ==="

cd /mnt/d/Claude/transportation-server

echo "1. 현재 디렉토리: $(pwd)"

echo "2. Java 버전 확인:"
java -version

echo ""
echo "3. Gradle Clean 수행..."
./gradlew clean

echo ""
echo "4. Java 컴파일 테스트..."
./gradlew compileJava

if [ $? -eq 0 ]; then
    echo "✅ 컴파일 성공!"
    
    echo ""
    echo "5. 서버 백그라운드 시작..."
    nohup ./gradlew bootRun > server-test.log 2>&1 &
    SERVER_PID=$!
    
    echo "서버 PID: $SERVER_PID"
    echo "10초 대기 중..."
    sleep 10
    
    echo ""
    echo "6. 서버 상태 확인..."
    curl -s -w "%{http_code}" http://localhost:5300/api/health
    
    echo ""
    echo "7. 지하철 API 테스트..."
    curl -s http://localhost:5300/api/subway/test
    
    echo ""
    echo "서버 로그 확인:"
    tail -10 server-test.log
    
else
    echo "❌ 컴파일 실패!"
    echo ""
    echo "컴파일 에러 확인:"
    ./gradlew compileJava 2>&1 | tail -20
fi

echo ""
echo "=== 테스트 완료 ==="