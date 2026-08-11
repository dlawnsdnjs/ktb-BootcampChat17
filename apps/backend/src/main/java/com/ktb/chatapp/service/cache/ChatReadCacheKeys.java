package com.ktb.chatapp.service.cache;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Generates race-safe cache keys for the latest message page.
 *
 * <p>Each message save advances a per-room generation. A concurrent reader may
 * still populate the old generation, but no later request can observe it.</p>
 */
@Slf4j
@Component("chatReadCacheKeys")
@ConditionalOnProperty(name = "chat.read-cache.enabled", havingValue = "true")
public class ChatReadCacheKeys {

    private static final DefaultRedisScript<Long> ADVANCE_VERSION = new DefaultRedisScript<>("""
            local version = redis.call('incr', KEYS[1])
            redis.call('pexpire', KEYS[1], ARGV[1])
            return version
            """, Long.class);
    private static final long ERROR_LOG_INTERVAL_MS = Duration.ofSeconds(30).toMillis();

    private final StringRedisTemplate redisTemplate;
    private final Duration versionTtl;
    private final String versionKeyPrefix;
    private final AtomicLong nextInvalidationErrorLogAt = new AtomicLong();

    public ChatReadCacheKeys(
            StringRedisTemplate redisTemplate,
            @Value("${chat.read-cache.key-prefix:chat-read}") String keyPrefix,
            @Value("${chat.read-cache.version-ttl:1h}") String versionTtl) {
        this.redisTemplate = redisTemplate;
        this.versionTtl = DurationStyle.detectAndParse(versionTtl);
        this.versionKeyPrefix = keyPrefix + "::version::message-latest::";
    }

    public String latestMessagePage(String roomId) {
        String version = "0";
        try {
            String cachedVersion = redisTemplate.opsForValue()
                    .getAndExpire(versionKey(roomId), versionTtl);
            if (cachedVersion != null) {
                version = cachedVersion;
            }
        } catch (RuntimeException e) {
            log.debug("Could not read latest-message cache generation for room {}", roomId, e);
        }
        return roomId + ":" + version;
    }

    public void advanceLatestMessageVersion(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return;
        }

        try {
            String key = versionKey(roomId);
            redisTemplate.execute(
                    ADVANCE_VERSION,
                    List.of(key),
                    Long.toString(versionTtl.toMillis()));
        } catch (RuntimeException e) {
            // Cache invalidation is an optimization. MongoDB writes must remain successful.
            logInvalidationFailure(roomId, e);
        }
    }

    private void logInvalidationFailure(String roomId, RuntimeException exception) {
        long now = System.currentTimeMillis();
        long next = nextInvalidationErrorLogAt.get();
        if (now >= next
                && nextInvalidationErrorLogAt.compareAndSet(
                        next, now + ERROR_LOG_INTERVAL_MS)) {
            log.warn("Could not advance latest-message cache generation for room {}",
                    roomId, exception);
        }
    }

    private String versionKey(String roomId) {
        return versionKeyPrefix + roomId;
    }
}
