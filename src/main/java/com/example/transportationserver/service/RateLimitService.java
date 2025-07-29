package com.example.transportationserver.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Iterator;
import java.util.Map;

/**
 * API 호출 제한을 통합 관리하는 서비스
 * 중복된 Rate Limiting 로직을 하나로 통합
 */
@Service
public class RateLimitService {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitService.class);
    
    // 각 API별 마지막 호출 시간 추적
    private final ConcurrentHashMap<String, Instant> lastCallTimes = new ConcurrentHashMap<>();
    private final AtomicLong totalWaitTime = new AtomicLong(0);
    
    // API별 제한 설정
    private static final Duration OPENSTREETMAP_DELAY = Duration.ofSeconds(1);
    private static final Duration MOLIT_DELAY = Duration.ofMillis(500);
    private static final Duration SEOUL_API_DELAY = Duration.ofMillis(200);
    
    // 캐시 정리를 위한 설정
    private static final Duration CACHE_CLEANUP_THRESHOLD = Duration.ofHours(1);
    
    /**
     * OpenStreetMap API 호출 전 대기
     */
    public void waitForOpenStreetMap() {
        waitForApi("OPENSTREETMAP", OPENSTREETMAP_DELAY);
    }
    
    /**
     * MOLIT API 호출 전 대기
     */
    public void waitForMolit() {
        waitForApi("MOLIT", MOLIT_DELAY);
    }
    
    /**
     * 서울시 API 호출 전 대기
     */
    public void waitForSeoulApi() {
        waitForApi("SEOUL_API", SEOUL_API_DELAY);
    }
    
    /**
     * 특정 API에 대한 Rate Limiting 적용
     */
    private void waitForApi(String apiName, Duration minimumDelay) {
        Instant now = Instant.now();
        Instant lastCall = lastCallTimes.get(apiName);
        
        if (lastCall != null) {
            Duration elapsed = Duration.between(lastCall, now);
            if (elapsed.compareTo(minimumDelay) < 0) {
                Duration waitTime = minimumDelay.minus(elapsed);
                long waitMillis = waitTime.toMillis();
                
                if (waitMillis > 0) {
                    logger.debug("{} API 호출 제한으로 {}ms 대기", apiName, waitMillis);
                    totalWaitTime.addAndGet(waitMillis);
                    
                    try {
                        Thread.sleep(waitMillis);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warn("{} API 대기 중 인터럽트 발생", apiName);
                    }
                }
            }
        }
        
        lastCallTimes.put(apiName, Instant.now());
    }
    
    /**
     * 커스텀 지연 시간으로 API 호출 제한
     */
    public void waitForCustomApi(String apiName, Duration delay) {
        waitForApi(apiName, delay);
    }
    
    /**
     * 통계 정보 반환
     */
    public long getTotalWaitTimeMillis() {
        return totalWaitTime.get();
    }
    
    /**
     * 마지막 호출 시간 정보 반환
     */
    public Instant getLastCallTime(String apiName) {
        return lastCallTimes.get(apiName);
    }
    
    /**
     * 통계 정보 초기화
     */
    public void resetStatistics() {
        totalWaitTime.set(0);
        lastCallTimes.clear();
        logger.info("Rate limit 통계 정보가 초기화되었습니다");
    }
    
    /**
     * 현재 Rate Limiting 상태 정보 반환
     */
    public java.util.Map<String, Object> getStatus() {
        java.util.Map<String, Object> status = new java.util.HashMap<>();
        status.put("totalWaitTimeMs", totalWaitTime.get());
        status.put("apiCallTimes", new java.util.HashMap<>(lastCallTimes));
        status.put("trackedApis", lastCallTimes.keySet());
        return status;
    }
    
    /**
     * 1시간마다 오래된 캐시 엔트리 정리
     */
    @Scheduled(fixedRate = 3600000) // 1시간마다 실행
    public void cleanupOldEntries() {
        Instant cutoff = Instant.now().minus(CACHE_CLEANUP_THRESHOLD);
        Iterator<Map.Entry<String, Instant>> iterator = lastCallTimes.entrySet().iterator();
        int removedCount = 0;
        
        while (iterator.hasNext()) {
            Map.Entry<String, Instant> entry = iterator.next();
            if (entry.getValue().isBefore(cutoff)) {
                iterator.remove();
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            logger.info("Rate limit 캐시에서 {}개의 오래된 엔트리를 정리했습니다", removedCount);
        }
    }
    
    /**
     * 리소스 정리
     */
    @PreDestroy
    public void cleanup() {
        lastCallTimes.clear();
        logger.info("RateLimitService 리소스 정리 완료");
    }
}