package com.example.transportationserver.util;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reactive Rate Limiter for API calls
 * 각 API 타입별로 요청 제한을 관리하는 유틸리티
 */
@Component
public class ReactiveRateLimiter {
    
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
}