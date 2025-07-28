package com.example.transportationserver.service;

import com.example.transportationserver.model.SubwayStation;
import com.example.transportationserver.repository.SubwayStationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 메모리 효율적인 스트리밍 기반 역 정보 처리 서비스
 */
@Service
public class StreamingStationService {
    
    private static final Logger logger = LoggerFactory.getLogger(StreamingStationService.class);
    private static final int DEFAULT_PAGE_SIZE = 50; // 메모리 효율을 위한 작은 페이지 크기
    
    @Autowired
    private SubwayStationMapper stationMapper;
    
    /**
     * 모든 역을 스트리밍으로 처리 (메모리 효율적)
     */
    public <T> Flux<T> processAllStationsStreaming(Function<SubwayStation, T> processor) {
        return processAllStationsStreaming(processor, DEFAULT_PAGE_SIZE);
    }
    
    /**
     * 모든 역을 지정된 페이지 크기로 스트리밍 처리
     */
    public <T> Flux<T> processAllStationsStreaming(Function<SubwayStation, T> processor, int pageSize) {
        return Mono.fromCallable(() -> stationMapper.countAll())
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapMany(totalCount -> {
                logger.info("전체 역 수: {}개, 페이지 크기: {}개로 스트리밍 처리 시작", totalCount, pageSize);
                
                int totalPages = (int) Math.ceil((double) totalCount / pageSize);
                
                return Flux.range(0, totalPages)
                    .flatMap(page -> {
                        int offset = page * pageSize;
                        logger.debug("페이지 {}/{} 처리 중 (offset: {}, limit: {})", 
                            page + 1, totalPages, offset, pageSize);
                        
                        return Mono.fromCallable(() -> stationMapper.findWithPaging(offset, pageSize))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMapMany(Flux::fromIterable);
                    }, 1) // 순차 처리로 메모리 사용량 제어
                    .map(processor)
                    .doOnComplete(() -> logger.info("전체 역 스트리밍 처리 완료"));
            });
    }
    
    /**
     * 좌표가 없는 역들을 스트리밍으로 처리 (메모리 효율적)
     */
    public <T> Flux<T> processStationsWithoutCoordinatesStreaming(Function<SubwayStation, T> processor) {
        return processStationsWithoutCoordinatesStreaming(processor, DEFAULT_PAGE_SIZE);
    }
    
    /**
     * 좌표가 없는 역들을 지정된 페이지 크기로 스트리밍 처리
     */
    public <T> Flux<T> processStationsWithoutCoordinatesStreaming(
            Function<SubwayStation, T> processor, int pageSize) {
        
        return Mono.fromCallable(() -> stationMapper.countStationsWithoutCoordinates())
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapMany(totalCount -> {
                if (totalCount == 0) {
                    logger.info("좌표가 없는 역이 없습니다.");
                    return Flux.empty();
                }
                
                logger.info("좌표가 없는 역 수: {}개, 페이지 크기: {}개로 스트리밍 처리 시작", totalCount, pageSize);
                
                int totalPages = (int) Math.ceil((double) totalCount / pageSize);
                
                return Flux.range(0, totalPages)
                    .flatMap(page -> {
                        int offset = page * pageSize;
                        logger.debug("좌표 없는 역 페이지 {}/{} 처리 중 (offset: {}, limit: {})", 
                            page + 1, totalPages, offset, pageSize);
                        
                        return Mono.fromCallable(() -> 
                            stationMapper.findStationsWithoutCoordinatesWithPaging(offset, pageSize))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMapMany(Flux::fromIterable);
                    }, 1) // 순차 처리로 메모리 사용량 제어
                    .map(processor)
                    .doOnComplete(() -> logger.info("좌표 없는 역 스트리밍 처리 완료"));
            });
    }
    
    /**
     * 모든 역에 대해 배치 작업 수행 (메모리 효율적)
     */
    public Mono<BatchProcessResult> processBatchOperation(
            Function<List<SubwayStation>, Integer> batchProcessor) {
        return processBatchOperation(batchProcessor, DEFAULT_PAGE_SIZE);
    }
    
    /**
     * 지정된 배치 크기로 모든 역에 대해 배치 작업 수행
     */
    public Mono<BatchProcessResult> processBatchOperation(
            Function<List<SubwayStation>, Integer> batchProcessor, int batchSize) {
        
        return Mono.fromCallable(() -> stationMapper.countAll())
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap(totalCount -> {
                logger.info("배치 작업 시작: 전체 {}개 역을 {}개씩 배치 처리", totalCount, batchSize);
                
                int totalBatches = (int) Math.ceil((double) totalCount / batchSize);
                
                return Flux.range(0, totalBatches)
                    .flatMap(batch -> {
                        int offset = batch * batchSize;
                        
                        return Mono.fromCallable(() -> {
                            List<SubwayStation> stations = stationMapper.findWithPaging(offset, batchSize);
                            int processed = batchProcessor.apply(stations);
                            
                            logger.debug("배치 {}/{} 완료: {}개 처리", 
                                batch + 1, totalBatches, processed);
                            
                            return processed;
                        }).subscribeOn(Schedulers.boundedElastic());
                    }, 1) // 순차 처리
                    .reduce(0, Integer::sum)
                    .map(totalProcessed -> {
                        logger.info("배치 작업 완료: 총 {}개 처리", totalProcessed);
                        return new BatchProcessResult(totalCount, totalProcessed);
                    });
            });
    }
    
    /**
     * 메모리 사용량을 모니터링하면서 스트리밍 처리
     */
    public <T> Flux<T> processWithMemoryMonitoring(
            Function<SubwayStation, T> processor, int pageSize) {
        
        return processAllStationsStreaming(processor, pageSize)
            .doOnSubscribe(subscription -> {
                Runtime runtime = Runtime.getRuntime();
                long usedMemory = runtime.totalMemory() - runtime.freeMemory();
                logger.info("스트리밍 처리 시작 - 현재 메모리 사용량: {:.2f} MB", 
                    usedMemory / 1024.0 / 1024.0);
            })
            .doOnNext(result -> {
                // 주기적으로 메모리 사용량 체크
                if (System.currentTimeMillis() % 10000 < 1000) { // 대략 10초마다
                    Runtime runtime = Runtime.getRuntime();
                    long usedMemory = runtime.totalMemory() - runtime.freeMemory();
                    long maxMemory = runtime.maxMemory();
                    double usagePercentage = ((double) usedMemory / maxMemory) * 100;
                    
                    if (usagePercentage > 80) {
                        logger.warn("⚠️ 메모리 사용량 높음: {:.1f}% ({:.2f}/{:.2f} MB)", 
                            usagePercentage, 
                            usedMemory / 1024.0 / 1024.0,
                            maxMemory / 1024.0 / 1024.0);
                        
                        // 메모리 정리 시도
                        System.gc();
                    }
                }
            })
            .doOnComplete(() -> {
                Runtime runtime = Runtime.getRuntime();
                long usedMemory = runtime.totalMemory() - runtime.freeMemory();
                logger.info("스트리밍 처리 완료 - 최종 메모리 사용량: {:.2f} MB", 
                    usedMemory / 1024.0 / 1024.0);
            });
    }
    
    /**
     * 조건부 스트리밍 처리 (필터링 포함)
     */
    public <T> Flux<T> processStationsWithCondition(
            Function<SubwayStation, Boolean> condition,
            Function<SubwayStation, T> processor) {
        
        return processAllStationsStreaming(station -> station, DEFAULT_PAGE_SIZE)
            .filter(condition::apply)
            .map(processor)
            .doOnNext(result -> {
                // 필터링된 결과에 대한 로깅
                logger.debug("조건 만족 역 처리: {}", result);
            });
    }
    
    // 결과 클래스
    public static class BatchProcessResult {
        public final int total;
        public final int processed;
        public final double successRate;
        
        public BatchProcessResult(int total, int processed) {
            this.total = total;
            this.processed = processed;
            this.successRate = total > 0 ? (double) processed / total * 100 : 0;
        }
        
        @Override
        public String toString() {
            return String.format("BatchProcessResult{total=%d, processed=%d, successRate=%.1f%%}", 
                total, processed, successRate);
        }
    }
}