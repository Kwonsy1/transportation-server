package com.example.transportationserver.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {
    // Simple cache configuration
    // For production, consider using Redis or other distributed caching solutions
}