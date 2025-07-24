package com.example.transportationserver.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "기본 API", description = "서버 상태 및 기본 정보를 제공합니다")
public class HomeController {
    
    @Operation(summary = "API 기본 정보", description = "Transportation Server API의 기본 상태를 반환합니다")
    @ApiResponse(responseCode = "200", description = "서버 정상 동작")
    @GetMapping("/")
    public String home() {
        return "Transportation Server API is running!";
    }
    
    @Operation(summary = "간단한 헬스체크", description = "서버의 기본적인 동작 상태를 확인합니다")
    @ApiResponse(responseCode = "200", description = "서버 정상")
    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}