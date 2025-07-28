package com.example.transportationserver.service;

import com.example.transportationserver.model.SubwayStation;
import com.example.transportationserver.util.ReactiveRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 배치 처리로 N+1 문제를 해결한 좌표 보완 서비스
 */
@Service
public class BatchCoordinateService {
    
    private static final Logger logger = LoggerFactory.getLogger(BatchCoordinateService.class);
    
    @Autowired
    private OpenStreetMapService openStreetMapService;
    
    @Autowired
    private SubwayStationService stationService;
    
    @Autowired
    private StreamingStationService streamingStationService;
    
    @Autowired
    private ReactiveRateLimiter rateLimiter;
    
    // 진행 상황 추적
    private final AtomicInteger totalCount = new AtomicInteger(0);
    private final AtomicInteger currentCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    
    /**
     * 스트리밍 방식으로 좌표 보완 (메모리 효율적)
     */
    @Async
    public CompletableFuture<CoordinateSupplementResult> supplementCoordinatesStreaming() {
        logger.info("=== 스트리밍 좌표 보완 작업 시작 ===");
        
        // 카운터 초기화
        currentCount.set(0);
        successCount.set(0);
        failureCount.set(0);
        
        return streamingStationService
            .processStationsWithoutCoordinatesStreaming(this::processStationAsync, 20) // 20개씩 배치
            .reduce(new CoordinateSupplementResult(0, 0, 0), (acc, result) -> {
                acc.total++;
                if (result.success) {
                    acc.success++;
                } else {
                    acc.failure++;
                }
                return acc;
            })
            .doOnSuccess(result -> {
                logger.info("=== 스트리밍 좌표 보완 작업 완료 ===");
                logger.info("🎯 처리 결과: 전체 {}개, 성공 {}개, 실패 {}개", 
                    result.total, result.success, result.failure);
                logger.info("📈 성공률: {:.1f}%", 
                    result.total > 0 ? (double) result.success / result.total * 100 : 0);
            })
            .toFuture();
    }
    
    /**
     * 기존 배치 처리 방식 (하위 호환성)
     */
    @Async
    public CompletableFuture<CoordinateSupplementResult> supplementCoordinatesBatch() {
        logger.info("=== 배치 좌표 보완 작업 시작 ===");
        
        // 좌표가 없는 역들 조회
        List<SubwayStation> stationsWithoutCoords = stationService.getStationsWithoutCoordinates();
        
        if (stationsWithoutCoords.isEmpty()) {
            logger.info("모든 역에 좌표가 이미 존재합니다.");
            return CompletableFuture.completedFuture(new CoordinateSupplementResult(0, 0, 0));
        }
        
        // 카운터 초기화
        totalCount.set(stationsWithoutCoords.size());
        currentCount.set(0);
        successCount.set(0);
        failureCount.set(0);
        
        logger.info("좌표가 없는 역 수: {}개", totalCount.get());
        logger.info("예상 소요 시간: {:.1f}분 (OpenStreetMap API 제한)", 
            Math.ceil(totalCount.get() / 60.0));
        
        return processStationsBatchOptimized(stationsWithoutCoords)
            .thenApply(result -> {
                logger.info("=== 배치 좌표 보완 작업 완료 ===");
                logger.info("🎯 처리 결과: 전체 {}개, 성공 {}개, 실패 {}개", 
                    result.total, result.success, result.failure);
                logger.info("📈 성공률: {:.1f}%", 
                    result.total > 0 ? (double) result.success / result.total * 100 : 0);
                return result;
            });
    }
    
    /**
     * 최적화된 배치 처리 - 스트리밍과 백프레셔 적용
     */
    private CompletableFuture<CoordinateSupplementResult> processStationsBatchOptimized(
            List<SubwayStation> stations) {
        
        return Flux.fromIterable(stations)
            .index() // 인덱스와 함께 처리
            .flatMap(indexedStation -> {
                long index = indexedStation.getT1();
                SubwayStation station = indexedStation.getT2();
                
                return processStationWithRateLimit(station, index)
                    .doOnSuccess(result -> {
                        // 진행률 로깅 (5%마다)
                        int current = currentCount.incrementAndGet();
                        if (current % Math.max(1, stations.size() / 20) == 0) {
                            double progress = ((double) current / stations.size()) * 100;
                            logger.info("📊 진행률: {:.1f}% ({}/{}) - 성공: {}, 실패: {}", 
                                progress, current, stations.size(), 
                                successCount.get(), failureCount.get());
                        }
                    });
            }, 1) // 동시 처리 수 1로 제한 (OpenStreetMap API 정책)
            .reduce(new CoordinateSupplementResult(0, 0, 0), (acc, result) -> {
                acc.total++;
                if (result.success) {
                    acc.success++;
                } else {
                    acc.failure++;
                }
                return acc;
            })
            .toFuture();
    }
    
    /**
     * Rate Limit이 적용된 개별 역 처리
     */
    private Mono<StationProcessResult> processStationWithRateLimit(SubwayStation station, long index) {
        logger.debug("[{}/{}] 좌표 보완 시도: {} ({}호선)", 
            index + 1, totalCount.get(), station.getName(), station.getLineNumber());
        
        return rateLimiter.executeLimited(
            ReactiveRateLimiter.ApiType.OPENSTREETMAP,
            openStreetMapService.searchStationCoordinates(
                station.getName(), 
                station.getRegion() != null ? station.getRegion() : "서울특별시"
            )
        )
        .flatMap(coordinate -> {
            if (coordinate.isPresent()) {
                return updateStationCoordinate(station, coordinate.get())
                    .map(updated -> {
                        if (updated) {
                            successCount.incrementAndGet();
                            logger.info("✅ 좌표 보완 성공: {} -> ({:.6f}, {:.6f})", 
                                station.getName(), coordinate.get().getLatitude(), coordinate.get().getLongitude());
                            return new StationProcessResult(station, true, null);
                        } else {
                            failureCount.incrementAndGet();
                            logger.warn("❌ 좌표 업데이트 실패: {}", station.getName());
                            return new StationProcessResult(station, false, "데이터베이스 업데이트 실패");
                        }
                    });
            } else {
                failureCount.incrementAndGet();
                logger.warn("🔍 좌표를 찾을 수 없음: {} ({}호선)", 
                    station.getName(), station.getLineNumber());
                return Mono.just(new StationProcessResult(station, false, "좌표 검색 실패"));
            }
        })
        .onErrorResume(error -> {
            failureCount.incrementAndGet();
            logger.error("좌표 보완 중 오류 발생: {}, 오류: {}", station.getName(), error.getMessage());
            return Mono.just(new StationProcessResult(station, false, error.getMessage()));
        });
    }
    
    /**
     * 데이터베이스 좌표 업데이트 (비동기)
     */
    private Mono<Boolean> updateStationCoordinate(SubwayStation station, OpenStreetMapService.Coordinate coord) {
        return Mono.fromCallable(() -> 
            stationService.updateStationCoordinates(station.getId(), coord.getLatitude(), coord.getLongitude())
        ).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }
    
    /**
     * 스트리밍용 단일 역 처리 메서드
     */
    private StationProcessResult processStationAsync(SubwayStation station) {
        try {
            int current = currentCount.incrementAndGet();
            
            // 진행률 로깅 (5%마다)
            if (current % 20 == 0) { // 20개마다 로깅
                logger.info("📊 스트리밍 진행: {}개 처리 - 성공: {}, 실패: {}", 
                    current, successCount.get(), failureCount.get());
            }
            
            logger.debug("좌표 보완 시도: {} ({}호선)", station.getName(), station.getLineNumber());
            
            // Rate Limiter 적용하여 OpenStreetMap API 호출
            Optional<OpenStreetMapService.Coordinate> coordinate = 
                rateLimiter.executeLimited(
                    ReactiveRateLimiter.ApiType.OPENSTREETMAP,
                    openStreetMapService.searchStationCoordinates(
                        station.getName(), 
                        station.getRegion() != null ? station.getRegion() : "서울특별시"
                    )
                ).block(); // 스트리밍 컨텍스트에서는 blocking 허용
            
            if (coordinate.isPresent()) {
                OpenStreetMapService.Coordinate coord = coordinate.get();
                
                // 데이터베이스에 좌표 업데이트
                boolean updated = stationService.updateStationCoordinates(
                    station.getId(), coord.getLatitude(), coord.getLongitude());
                
                if (updated) {
                    successCount.incrementAndGet();
                    logger.info("✅ 좌표 보완 성공: {} -> ({:.6f}, {:.6f})", 
                        station.getName(), coord.getLatitude(), coord.getLongitude());
                    return new StationProcessResult(station, true, null);
                } else {
                    failureCount.incrementAndGet();
                    logger.warn("❌ 좌표 업데이트 실패: {}", station.getName());
                    return new StationProcessResult(station, false, "데이터베이스 업데이트 실패");
                }
            } else {
                failureCount.incrementAndGet();
                logger.warn("🔍 좌표를 찾을 수 없음: {} ({}호선)", 
                    station.getName(), station.getLineNumber());
                return new StationProcessResult(station, false, "좌표 검색 실패");
            }
            
        } catch (Exception e) {
            failureCount.incrementAndGet();
            logger.error("좌표 보완 중 오류 발생: {}, 오류: {}", station.getName(), e.getMessage());
            return new StationProcessResult(station, false, e.getMessage());
        }
    }
    
    /**
     * 진행 상황 조회
     */
    public CoordinateProgress getProgress() {
        return new CoordinateProgress(
            totalCount.get(),
            currentCount.get(),
            successCount.get(),
            failureCount.get()
        );
    }
    
    // 결과 클래스들
    public static class CoordinateSupplementResult {
        public int total;
        public int success;
        public int failure;
        
        public CoordinateSupplementResult(int total, int success, int failure) {
            this.total = total;
            this.success = success;
            this.failure = failure;
        }
    }
    
    public static class CoordinateProgress {
        public final int total;
        public final int current;
        public final int success;
        public final int failure;
        public final double progressPercentage;
        
        public CoordinateProgress(int total, int current, int success, int failure) {
            this.total = total;
            this.current = current;
            this.success = success;
            this.failure = failure;
            this.progressPercentage = total > 0 ? (double) current / total * 100 : 0;
        }
    }
    
    private static class StationProcessResult {
        final SubwayStation station;
        final boolean success;
        final String errorMessage;
        
        StationProcessResult(SubwayStation station, boolean success, String errorMessage) {
            this.station = station;
            this.success = success;
            this.errorMessage = errorMessage;
        }
    }
}