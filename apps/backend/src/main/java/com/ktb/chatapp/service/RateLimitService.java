package com.ktb.chatapp.service;

import com.ktb.chatapp.model.RateLimit;
import com.ktb.chatapp.service.ratelimit.RateLimitStore;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import static java.net.InetAddress.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final RateLimitStore rateLimitStore;
    private final StringRedisTemplate redisTemplate;
    private static final DefaultRedisScript<List> REDIS_INCREMENT_SCRIPT =
            new DefaultRedisScript<>("""
                    local count = redis.call('INCR', KEYS[1])
                    if count == 1 then
                        redis.call('PEXPIRE', KEYS[1], ARGV[1])
                    end
                    return {count, redis.call('PTTL', KEYS[1])}
                    """, List.class);

    @Value("${rate-limit.store.type:mongo}")
    private String storeType;
    @Value("${rate-limit.redis.key-prefix:rate-limit}")
    private String redisKeyPrefix;
    @Value("${HOSTNAME:''}")
    private String hostName;
    
    @PostConstruct
    public void init() {
        if (!hostName.isEmpty()) {
            return;
        }
        hostName = generateHostname();
    }
    
    private String generateHostname() {
        try {
            return getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }
    }
    
    
    @Transactional
    public RateLimitCheckResult checkRateLimit(String _clientId, int maxRequests, Duration window) {
        Duration effectiveWindow = window != null ? window : Duration.ofSeconds(1);
        long windowSeconds = Math.max(1L, effectiveWindow.getSeconds());
        if ("redis".equalsIgnoreCase(storeType)) {
            try {
                return checkRedis(_clientId, maxRequests, effectiveWindow, windowSeconds);
            } catch (Exception e) {
                log.warn("Redis rate limit failed; using Mongo fallback for client {}", _clientId, e);
            }
        }

        return checkMongo(hostName + ":" + _clientId, maxRequests, windowSeconds);
    }

    private RateLimitCheckResult checkRedis(
            String clientId, int maxRequests, Duration window, long windowSeconds) {
        String key = redisKeyPrefix + ":" + clientId;
        List<?> result = redisTemplate.execute(
                REDIS_INCREMENT_SCRIPT,
                List.of(key),
                Long.toString(Math.max(1L, window.toMillis())));
        if (result == null || result.size() < 2) {
            throw new IllegalStateException("Redis rate limit script returned no result");
        }

        long count = ((Number) result.get(0)).longValue();
        long ttlMillis = Math.max(1L, ((Number) result.get(1)).longValue());
        long ttlSeconds = Math.max(1L, (ttlMillis + 999L) / 1000L);
        long resetEpochSeconds = Instant.now().getEpochSecond() + ttlSeconds;
        if (count > maxRequests) {
            return RateLimitCheckResult.rejected(
                    maxRequests, windowSeconds, resetEpochSeconds, ttlSeconds);
        }
        return RateLimitCheckResult.allowed(
                maxRequests,
                Math.max(0, maxRequests - (int) count),
                windowSeconds,
                resetEpochSeconds,
                ttlSeconds);
    }

    private RateLimitCheckResult checkMongo(
            String actualClientId, int maxRequests, long windowSeconds) {
        Instant now = Instant.now();
        long nowEpochSeconds = now.getEpochSecond();
        Instant expiresAt = now.plusSeconds(windowSeconds);

        try {
            RateLimit rateLimit = rateLimitStore.findByClientId(actualClientId).orElse(null);
            if (rateLimit != null && !rateLimit.getExpiresAt().isAfter(now)) {
                rateLimit.setCount(0);
                rateLimit.setExpiresAt(expiresAt);
            }

            int currentCount = rateLimit != null ? rateLimit.getCount() : 0;

            if (rateLimit != null && currentCount >= maxRequests) {
                long retryAfterSeconds = Math.max(1L,
                    rateLimit.getExpiresAt().getEpochSecond() - nowEpochSeconds);
                long resetEpochSeconds = rateLimit.getExpiresAt().getEpochSecond();
                return RateLimitCheckResult.rejected(
                        maxRequests, windowSeconds, resetEpochSeconds, retryAfterSeconds);
            }

            // Create or update rate limit
            if (rateLimit == null) {
                rateLimit = RateLimit.builder()
                        .clientId(actualClientId)
                        .count(1)
                        .expiresAt(expiresAt)
                        .build();
            } else {
                rateLimit.setCount(currentCount + 1);
            }
            rateLimitStore.save(rateLimit);

            int newCount = currentCount + 1;
            int remaining = Math.max(0, maxRequests - newCount);
            long ttlSeconds = Math.max(1L, rateLimit.getExpiresAt().getEpochSecond() - nowEpochSeconds);
            long resetEpochSeconds = rateLimit.getExpiresAt().getEpochSecond();

            return RateLimitCheckResult.allowed(
                    maxRequests, remaining, windowSeconds, resetEpochSeconds, ttlSeconds);
        } catch (Exception e) {
            log.error("Rate limit check failed for client: {}", actualClientId, e);
            long resetEpochSeconds = nowEpochSeconds + windowSeconds;
            return RateLimitCheckResult.allowed(
                    maxRequests, maxRequests, windowSeconds, resetEpochSeconds, windowSeconds);
        }
    }
    
}
