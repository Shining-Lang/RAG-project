package com.lsn.ragkb.service.sales;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Arrays;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesToolCacheService {

    private static final String CACHE_PREFIX = "sales:tool:";

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${sales-agent.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${sales-agent.cache.ttl-seconds:300}")
    private long ttlSeconds;

    public String getOrCompute(String operation, Supplier<String> loader, Object... params) {
        if (!cacheEnabled) {
            return loader.get();
        }

        String key = buildKey(operation, params);
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof String value) {
                log.info("[SalesToolCache] hit operation={}", operation);
                return value;
            }
        } catch (Exception e) {
            log.warn("[SalesToolCache] read degraded: {}", e.getMessage());
        }

        String result = loader.get();
        if (shouldCache(result)) {
            try {
                redisTemplate.opsForValue().set(key, result, Duration.ofSeconds(ttlSeconds));
            } catch (Exception e) {
                log.warn("[SalesToolCache] write degraded: {}", e.getMessage());
            }
        }
        return result;
    }

    private String buildKey(String operation, Object... params) {
        return CACHE_PREFIX + operation + ":" + sha256(Arrays.deepToString(params));
    }

    private boolean shouldCache(String result) {
        return result != null
                && !result.isBlank()
                && !result.contains("出现问题")
                && !result.contains("暂时不可用");
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }
}
