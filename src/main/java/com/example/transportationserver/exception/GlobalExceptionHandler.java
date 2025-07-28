package com.example.transportationserver.exception;

import com.example.transportationserver.dto.StandardApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

@RestControllerAdvice
@CrossOrigin(origins = "*", maxAge = 3600)
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<StandardApiResponse<Object>> handleResourceNotFoundException(ResourceNotFoundException ex, WebRequest request) {
        logger.warn("Resource not found: {}", ex.getMessage());
        StandardApiResponse<Object> response = StandardApiResponse.error(
            ex.getMessage(), 
            HttpStatus.NOT_FOUND.value()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<StandardApiResponse<Object>> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        logger.warn("Invalid argument: {}", ex.getMessage());
        StandardApiResponse<Object> response = StandardApiResponse.error(
            "잘못된 요청 파라미터입니다", 
            ex.getMessage(), 
            HttpStatus.BAD_REQUEST.value()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<StandardApiResponse<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex, WebRequest request) {
        logger.warn("Validation error: {}", ex.getMessage());
        Map<String, String> errors = new HashMap<>();
        
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        
        StandardApiResponse<Map<String, String>> response = StandardApiResponse.error(
            "입력값 검증 실패", 
            HttpStatus.BAD_REQUEST.value()
        );
        response.setData(errors);
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<StandardApiResponse<Object>> handleRuntimeException(RuntimeException ex, WebRequest request) {
        logger.error("Runtime error occurred", ex);
        StandardApiResponse<Object> response = StandardApiResponse.error(
            ex.getMessage(), 
            HttpStatus.BAD_REQUEST.value()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<StandardApiResponse<Object>> handleWebClientResponseException(WebClientResponseException ex, WebRequest request) {
        logger.error("외부 API 호출 실패 (HTTP {}): {}", ex.getStatusCode(), ex.getMessage());
        String message = String.format("외부 API 연동 오류 (HTTP %d)", ex.getStatusCode().value());
        StandardApiResponse<Object> response = StandardApiResponse.error(
            message,
            ex.getResponseBodyAsString(),
            HttpStatus.BAD_GATEWAY.value()
        );
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }
    
    @ExceptionHandler(WebClientException.class)
    public ResponseEntity<StandardApiResponse<Object>> handleWebClientException(WebClientException ex, WebRequest request) {
        logger.error("외부 API 연결 오류: {}", ex.getMessage());
        StandardApiResponse<Object> response = StandardApiResponse.error(
            "외부 API 연결에 실패했습니다. 네트워크 연결을 확인해주세요.",
            ex.getMessage(),
            HttpStatus.SERVICE_UNAVAILABLE.value()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
    
    @ExceptionHandler(ConnectException.class)
    public ResponseEntity<StandardApiResponse<Object>> handleConnectException(ConnectException ex, WebRequest request) {
        logger.error("네트워크 연결 실패: {}", ex.getMessage());
        StandardApiResponse<Object> response = StandardApiResponse.error(
            "외부 서비스에 연결할 수 없습니다",
            ex.getMessage(),
            HttpStatus.SERVICE_UNAVAILABLE.value()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }
    
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<StandardApiResponse<Object>> handleTimeoutException(TimeoutException ex, WebRequest request) {
        logger.error("요청 시간 초과: {}", ex.getMessage());
        StandardApiResponse<Object> response = StandardApiResponse.error(
            "요청 처리 시간이 초과되었습니다",
            ex.getMessage(),
            HttpStatus.REQUEST_TIMEOUT.value()
        );
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(response);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<StandardApiResponse<Object>> handleGenericException(Exception ex, WebRequest request) {
        logger.error("Unexpected error occurred", ex);
        StandardApiResponse<Object> response = StandardApiResponse.error(
            "서버 내부 오류가 발생했습니다", 
            ex.getMessage(), 
            HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}