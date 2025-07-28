#!/bin/bash

# OpenStreetMap API 직접 테스트 스크립트

echo "=== OpenStreetMap API 테스트 시작 ==="

# 테스트할 역명들
stations=("강남역" "서울역" "시청역")

for station in "${stations[@]}"; do
    echo
    echo "테스트: $station"
    
    # URL 인코딩된 쿼리 생성
    query=$(echo "$station 서울특별시 지하철역 대한민국" | sed 's/ /%20/g' | sed 's/역/%EC%97%AD/g')
    
    url="https://nominatim.openstreetmap.org/search?q=$query&format=json&addressdetails=1&limit=3&countrycodes=kr"
    
    echo "요청 URL: $url"
    
    # curl로 요청 전송
    response=$(curl -s -H "User-Agent: Transportation-Server-Test/1.0" "$url")
    
    if [ $? -eq 0 ]; then
        echo "응답 수신됨:"
        echo "$response" | jq -r '.[] | select(.lat != null) | "좌표: \(.lat), \(.lon) - \(.display_name)"'
        
        if [ "$(echo "$response" | jq length)" -eq 0 ]; then
            echo "❌ 결과 없음"
        else
            echo "✅ 결과 있음"
        fi
    else
        echo "❌ 요청 실패"
    fi
    
    # API 제한 고려 1초 대기
    sleep 1
done

echo
echo "=== 테스트 완료 ==="