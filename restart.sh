#!/bin/bash

echo "=== Transportation Server 재시작 ==="
echo ""

# 서버 종료
./stop.sh

echo ""
echo "잠시 대기 중..."
sleep 2

# 서버 시작
./start.sh