#!/bin/bash

echo "=== Transportation Server 간단 테스트 ==="

cd /mnt/d/Claude/transportation-server

echo "1. Java 버전 확인..."
java -version 2>&1 | head -3

echo ""
echo "2. 컴파일 테스트..."
./gradlew clean compileJava --stacktrace 2>&1 | tee compile-test.log

echo ""
echo "3. 컴파일 결과 확인..."
if grep -q "BUILD SUCCESSFUL" compile-test.log; then
    echo "✅ 컴파일 성공!"
else
    echo "❌ 컴파일 실패!"
    echo "오류 내용:"
    grep -A 5 -B 5 "error:" compile-test.log || echo "오류 로그 없음"
fi

echo "=== 테스트 완료 ==="