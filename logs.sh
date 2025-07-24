#!/bin/bash

echo "=== Transportation Server 로그 확인 ==="
echo "실시간 로그를 보려면 Ctrl+C로 종료하세요."
echo ""

if [ -f server.log ]; then
    tail -f server.log
else
    echo "❌ server.log 파일이 없습니다. 서버를 먼저 시작하세요:"
    echo "./start.sh"
fi