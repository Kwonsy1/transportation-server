package com.example.transportationserver.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "표준 API 응답 모델")
public class StandardApiResponse<T> {
    
    @Schema(description = "응답 상태", example = "success", allowableValues = {"success", "error"})
    private String status;
    
    @Schema(description = "HTTP 상태 코드", example = "200")
    private Integer code;
    
    @Schema(description = "응답 메시지", example = "데이터 조회 성공")
    private String message;
    
    @Schema(description = "응답 데이터")
    private T data;
    
    @Schema(description = "리스트 데이터인 경우 총 개수")
    private Integer totalCount;
    
    @Schema(description = "오류 상세 정보")
    private String error;
    
    @Schema(description = "타임스탬프", example = "2025-07-25T13:45:00.000Z")
    private String timestamp;
    
    public StandardApiResponse() {
        this.timestamp = java.time.Instant.now().toString();
    }
    
    public StandardApiResponse(String status, Integer code, String message, T data) {
        this();
        this.status = status;
        this.code = code;
        this.message = message;
        this.data = data;
    }
    
    public StandardApiResponse(String status, Integer code, String message, T data, Integer totalCount) {
        this(status, code, message, data);
        this.totalCount = totalCount;
    }
    
    // 성공 응답 팩토리 메서드들
    public static <T> StandardApiResponse<T> success(T data) {
        return new StandardApiResponse<>("success", 200, "요청이 성공적으로 처리되었습니다", data);
    }
    
    public static <T> StandardApiResponse<T> success(T data, String message) {
        return new StandardApiResponse<>("success", 200, message, data);
    }
    
    public static <T> StandardApiResponse<T> success(T data, String message, Integer code) {
        return new StandardApiResponse<>("success", code, message, data);
    }
    
    // 리스트 데이터용 팩토리 메서드들
    public static <T> StandardApiResponse<T> successWithCount(T data, String message, Integer totalCount) {
        return new StandardApiResponse<>("success", 200, message, data, totalCount);
    }
    
    public static <T> StandardApiResponse<T> successWithCount(T data, Integer totalCount) {
        return new StandardApiResponse<>("success", 200, "요청이 성공적으로 처리되었습니다", data, totalCount);
    }
    
    // 에러 응답 팩토리 메서드들
    public static <T> StandardApiResponse<T> error(String message) {
        StandardApiResponse<T> response = new StandardApiResponse<>("error", 500, message, null);
        response.setError(message);
        return response;
    }
    
    public static <T> StandardApiResponse<T> error(String message, Integer code) {
        StandardApiResponse<T> response = new StandardApiResponse<>("error", code, message, null);
        response.setError(message);
        return response;
    }
    
    public static <T> StandardApiResponse<T> error(String message, String error, Integer code) {
        StandardApiResponse<T> response = new StandardApiResponse<>("error", code, message, null);
        response.setError(error);
        return response;
    }
    
    // Getters and Setters
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public Integer getCode() {
        return code;
    }
    
    public void setCode(Integer code) {
        this.code = code;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public T getData() {
        return data;
    }
    
    public void setData(T data) {
        this.data = data;
    }
    
    public String getError() {
        return error;
    }
    
    public void setError(String error) {
        this.error = error;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    public Integer getTotalCount() {
        return totalCount;
    }
    
    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }
}