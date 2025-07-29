package com.example.transportationserver.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Iterator;
import java.util.Map;

/**
 * Reactive Rate Limiter for API calls
 * 각 API 타입별로 요청 제한을 관리하는 유틸리티
 */
@Component
public class ReactiveRateLimiter {
    
    private static final Logger logger = LoggerFactory.getLogger(ReactiveRateLimiter.class);
    private static final Duration CLEANUP_THRESHOLD = Duration.ofHours(1);
    
    private final ConcurrentHashMap<ApiType, AtomicLong> lastRequestTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ApiType, Duration> intervals = new ConcurrentHashMap<>();
    
    public enum ApiType {
        OPENSTREETMAP(Duration.ofSeconds(1)),    // 1초당 1회
        MOLIT(Duration.ofMillis(500)),           // 0.5초당 1회  
        SEOUL(Duration.ofMillis(100));           // 0.1초당 1회
        
        private final Duration defaultInterval;
        
        ApiType(Duration defaultInterval) {
            this.defaultInterval = defaultInterval;
        }
        
        public Duration getDefaultInterval() {
            return defaultInterval;
        }
    }
    
    public ReactiveRateLimiter() {
        // 기본 간격 설정
        for (ApiType apiType : ApiType.values()) {
            intervals.put(apiType, apiType.getDefaultInterval());
            lastRequestTimes.put(apiType, new AtomicLong(0));
        }
    }
    
    /**
     * Rate limit이 적용된 API 호출 실행
     */
    public <T> Mono<T> executeLimited(ApiType apiType, Mono<T> operation) {
        return Mono.defer(() -> {
            long now = System.currentTimeMillis();
            AtomicLong lastTime = lastRequestTimes.get(apiType);
            Duration interval = intervals.get(apiType);
            
            long timeSinceLastRequest = now - lastTime.get();
            long requiredInterval = interval.toMillis();
            
            if (timeSinceLastRequest >= requiredInterval) {
                // 충분한 시간이 지났으면 바로 실행
                lastTime.set(now);
                return operation;
            } else {
                // 대기가 필요한 경우
                long delay = requiredInterval - timeSinceLastRequest;
                return Mono.delay(Duration.ofMillis(delay), Schedulers.boundedElastic())
                    .then(Mono.fromCallable(() -> {
                        lastTime.set(System.currentTimeMillis());
                        return null;
                    }))
                    .then(operation);
            }
        });
    }
    
    /**
     * 동적으로 Rate Limit 간격 조정
     */
    public void setInterval(ApiType apiType, Duration interval) {
        intervals.put(apiType, interval);
    }
    
    /**
     * 현재 Rate Limit 상태 확인
     */
    public long getTimeSinceLastRequest(ApiType apiType) {
        return System.currentTimeMillis() - lastRequestTimes.get(apiType).get();
    }
    
    /**
     * 1시간마다 오래된 데이터 정리 (메모리 누수 방지)
     */
    @Scheduled(fixedRate = 3600000) // 1시간마다
    public void cleanupOldData() {
        long cutoffTime = System.currentTimeMillis() - CLEANUP_THRESHOLD.toMillis();
        int resetCount = 0;
        
        for (Map.Entry<ApiType, AtomicLong> entry : lastRequestTimes.entrySet()) {
            AtomicLong lastTime = entry.getValue();
            if (lastTime.get() < cutoffTime) {
                lastTime.set(0);
                resetCount++;
            }
        }
        
        if (resetCount > 0) {
            logger.debug("ReactiveRateLimiter: {}개 API 타입의 오래된 데이터를 정리했습니다", resetCount);
        }
    }
    
    /**
     * 리소스 정리
     */
    @PreDestroy
    public void cleanup() {
        lastRequestTimes.clear();
        intervals.clear();
        logger.info("ReactiveRateLimiter 리소스 정리 완료");
    }
}