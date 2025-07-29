package com.example.transportationserver.util;

import com.example.transportationserver.dto.StandardApiResponse;
import org.slf4j.Logger;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 에러 처리 로직을 통합한 유틸리티 클래스
 * 중복된 에러 처리 패턴을 하나로 통합
 */
public class ErrorHandler {
    
    /**
     * 표준 에러 응답 생성
     */
    public static <T> ResponseEntity<StandardApiResponse<T>> createErrorResponse(String message, int statusCode) {
        return ResponseEntity.status(statusCode)
                .body(StandardApiResponse.error(message, statusCode));
    }
    
    /**
     * 내부 서버 에러 응답 생성
     */
    public static <T> ResponseEntity<StandardApiResponse<T>> createInternalServerError(String message) {
        return createErrorResponse("내부 서버 오류: " + message, 500);
    }
    
    /**
     * 예외로부터 에러 응답 생성
     */
    public static <T> ResponseEntity<StandardApiResponse<T>> createErrorFromException(Exception e, String operation) {
        String message = operation + " 실패: " + e.getMessage();
        return createInternalServerError(message);
    }
    
    /**
     * Map 형태의 에러 응답 생성
     */
    public static Map<String, Object> createErrorMap(String status, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", status);
        response.put("error", message);
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }
    
    /**
     * Map 형태의 성공 응답 생성
     */
    public static Map<String, Object> createSuccessMap(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", message);
        response.put("data", data);
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }
    
    /**
     * Reactive 스트림용 에러 처리 함수 생성
     */
    public static <T> Function<Throwable, Mono<T>> createReactiveErrorHandler(T fallbackValue, Logger logger, String operation) {
        return error -> {
            logger.error("{} 중 오류 발생: {}", operation, error.getMessage());
            return Mono.just(fallbackValue);
        };
    }
    
    /**
     * 빈 리스트를 반환하는 Reactive 에러 핸들러
     */
    public static <T> Function<Throwable, Mono<List<T>>> createListErrorHandler(Logger logger, String operation) {
        return createReactiveErrorHandler(new ArrayList<>(), logger, operation);
    }
    
    /**
     * Optional.empty()를 반환하는 Reactive 에러 핸들러  
     */
    public static <T> Function<Throwable, Mono<java.util.Optional<T>>> createOptionalErrorHandler(Logger logger, String operation) {
        return createReactiveErrorHandler(java.util.Optional.empty(), logger, operation);
    }
    
    /**
     * 로깅과 함께 예외 처리
     */
    public static void logAndHandle(Logger logger, String operation, Exception e) {
        logger.error("{} 실패: {}", operation, e.getMessage(), e);
    }
    
    /**
     * 로깅과 함께 예외 처리 (스택 트레이스 제외)
     */
    public static void logAndHandleSimple(Logger logger, String operation, Exception e) {
        logger.error("{} 실패: {}", operation, e.getMessage());
    }
    
    /**
     * Try-Catch 블록을 간소화하는 헬퍼 메서드
     */
    public static <T> ResponseEntity<StandardApiResponse<T>> handleWithTryCatch(
            java.util.function.Supplier<T> operation,
            String operationName,
            String successMessage,
            Logger logger) {
        
        try {
            T result = operation.get();
            logger.info("{} 성공", operationName);
            return ResponseEntity.ok(StandardApiResponse.success(result, successMessage));
        } catch (Exception e) {
            logAndHandle(logger, operationName, e);
            return createErrorFromException(e, operationName);
        }
    }
    
    /**
     * 리스트 결과를 위한 Try-Catch 헬퍼 메서드
     */
    public static <T> ResponseEntity<StandardApiResponse<List<T>>> handleListWithTryCatch(
            java.util.function.Supplier<List<T>> operation,
            String operationName,
            Logger logger) {
        
        try {
            List<T> result = operation.get();
            String message = result.size() + "개 " + operationName + " 완료";
            logger.info("{} 성공: {}개 결과", operationName, result.size());
            return ResponseEntity.ok(StandardApiResponse.successWithCount(result, message, result.size()));
        } catch (Exception e) {
            logAndHandle(logger, operationName, e);
            return createErrorFromException(e, operationName);
        }
    }
    
    /**
     * 유효성 검사 실패 응답 생성
     */
    public static <T> ResponseEntity<StandardApiResponse<T>> createValidationError(String message) {
        return createErrorResponse("유효하지 않은 요청: " + message, 400);
    }
    
    /**
     * 리소스를 찾을 수 없음 응답 생성
     */
    public static <T> ResponseEntity<StandardApiResponse<T>> createNotFoundError(String resource) {
        return createErrorResponse(resource + "을(를) 찾을 수 없습니다", 404);
    }
}