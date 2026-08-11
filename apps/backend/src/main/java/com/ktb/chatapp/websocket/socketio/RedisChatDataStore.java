package com.ktb.chatapp.websocket.socketio;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis implementation for application-level Socket.IO connection and room state.
 *
 * <p>All keys are leases: writes apply a TTL and active application instances renew
 * their locally-owned socket state. Redis errors deliberately propagate instead of
 * falling back to local memory, which would split state between application nodes.
 *
 * <p>TODO: Add explicit Redis outage readiness, retry and state resynchronization
 * policies when datastore-level failure handling is introduced.
 */
public class RedisChatDataStore implements ChatDataStore {

    private static final DefaultRedisScript<String> GET_AND_SET_TRACKED = new DefaultRedisScript<>("""
            local previous = redis.call('get', KEYS[1])
            local now = redis.call('time')
            local expiresAt = now[1] * 1000 + math.floor(now[2] / 1000) + tonumber(ARGV[2])
            redis.call('psetex', KEYS[1], ARGV[2], ARGV[1])
            redis.call('zadd', KEYS[2], expiresAt, KEYS[1])
            return previous
            """, String.class);

    private static final DefaultRedisScript<Long> DELETE_TRACKED = new DefaultRedisScript<>("""
            local deleted = redis.call('del', KEYS[1])
            redis.call('zrem', KEYS[2], KEYS[1])
            return deleted
            """, Long.class);

    private static final DefaultRedisScript<Long> DELETE_IF_EQUALS_TRACKED = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                redis.call('del', KEYS[1])
                redis.call('zrem', KEYS[2], KEYS[1])
                return 1
            end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> TOUCH_IF_EQUALS_TRACKED = new DefaultRedisScript<>("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                local now = redis.call('time')
                local expiresAt = now[1] * 1000 + math.floor(now[2] / 1000) + tonumber(ARGV[2])
                redis.call('pexpire', KEYS[1], ARGV[2])
                redis.call('zadd', KEYS[2], expiresAt, KEYS[1])
                return 1
            end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> TRACKED_SIZE = new DefaultRedisScript<>("""
            local now = redis.call('time')
            local nowMillis = now[1] * 1000 + math.floor(now[2] / 1000)
            redis.call('zremrangebyscore', KEYS[1], '-inf', nowMillis)
            return redis.call('zcard', KEYS[1])
            """, Long.class);

    private static final DefaultRedisScript<Long> ADD_TO_SET = new DefaultRedisScript<>("""
            local added = redis.call('sadd', KEYS[1], ARGV[1])
            redis.call('pexpire', KEYS[1], ARGV[2])
            return added
            """, Long.class);

    private static final DefaultRedisScript<Long> REMOVE_FROM_SET = new DefaultRedisScript<>("""
            local removed = redis.call('srem', KEYS[1], ARGV[1])
            if redis.call('exists', KEYS[1]) == 1 then
                redis.call('pexpire', KEYS[1], ARGV[2])
            end
            return removed
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration leaseTtl;
    private final String keyPrefix;

    public RedisChatDataStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            Duration leaseTtl,
            String keyPrefix) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        if (leaseTtl == null || leaseTtl.isZero() || leaseTtl.isNegative()) {
            throw new IllegalArgumentException("leaseTtl must be positive");
        }
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("keyPrefix must not be blank");
        }
        this.leaseTtl = leaseTtl;
        this.keyPrefix = keyPrefix.endsWith(":") ? keyPrefix : keyPrefix + ":";
    }

    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        String value = redisTemplate.opsForValue().get(storageKey(key));
        if (value == null) {
            return Optional.empty();
        }
        return deserialize(key, value, type);
    }

    @Override
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(storageKey(key), serialize(key, value), leaseTtl);
    }

    @Override
    public boolean setIfAbsent(String key, Object value) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
                storageKey(key), serialize(key, value), leaseTtl));
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(storageKey(key));
    }

    @Override
    public void setTracked(String key, Object value, String trackerKey) {
        redisTemplate.execute(
                GET_AND_SET_TRACKED,
                List.of(storageKey(key), storageKey(trackerKey)),
                serialize(key, value),
                Long.toString(leaseTtl.toMillis()));
    }

    @Override
    public <T> Optional<T> getAndSetTracked(
            String key,
            Object value,
            Class<T> type,
            String trackerKey) {
        String previous = redisTemplate.execute(
                GET_AND_SET_TRACKED,
                List.of(storageKey(key), storageKey(trackerKey)),
                serialize(key, value),
                Long.toString(leaseTtl.toMillis()));
        if (previous == null) {
            return Optional.empty();
        }
        return deserialize(key, previous, type);
    }

    @Override
    public void deleteTracked(String key, String trackerKey) {
        redisTemplate.execute(
                DELETE_TRACKED,
                List.of(storageKey(key), storageKey(trackerKey)));
    }

    @Override
    public boolean deleteIfEqualsTracked(
            String key,
            Object expectedValue,
            String trackerKey) {
        Long deleted = redisTemplate.execute(
                DELETE_IF_EQUALS_TRACKED,
                List.of(storageKey(key), storageKey(trackerKey)),
                serialize(key, expectedValue));
        return Long.valueOf(1L).equals(deleted);
    }

    @Override
    public Set<String> getSet(String key) {
        Set<String> values = redisTemplate.opsForSet().members(storageKey(key));
        return values == null ? Set.of() : Set.copyOf(values);
    }

    @Override
    public void addToSet(String key, String value) {
        redisTemplate.execute(
                ADD_TO_SET,
                List.of(storageKey(key)),
                value,
                Long.toString(leaseTtl.toMillis()));
    }

    @Override
    public void removeFromSet(String key, String value) {
        redisTemplate.execute(
                REMOVE_FROM_SET,
                List.of(storageKey(key)),
                value,
                Long.toString(leaseTtl.toMillis()));
    }

    @Override
    public boolean containsInSet(String key, String value) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(storageKey(key), value));
    }

    @Override
    public boolean touch(String key) {
        return Boolean.TRUE.equals(redisTemplate.expire(storageKey(key), leaseTtl));
    }

    @Override
    public boolean touchIfEqualsTracked(
            String key,
            Object expectedValue,
            String trackerKey) {
        Long touched = redisTemplate.execute(
                TOUCH_IF_EQUALS_TRACKED,
                List.of(storageKey(key), storageKey(trackerKey)),
                serialize(key, expectedValue),
                Long.toString(leaseTtl.toMillis()));
        return Long.valueOf(1L).equals(touched);
    }

    @Override
    public int trackedSize(String trackerKey) {
        Long size = redisTemplate.execute(
                TRACKED_SIZE,
                List.of(storageKey(trackerKey)));
        return Math.toIntExact(size == null ? 0L : size);
    }

    String storageKey(String logicalKey) {
        return keyPrefix + logicalKey;
    }

    private String serialize(String key, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Unable to serialize chat data for key " + key, e);
        }
    }

    private <T> Optional<T> deserialize(String key, String value, Class<T> type) {
        try {
            return Optional.of(objectMapper.readValue(value, type));
        } catch (JacksonException e) {
            throw new IllegalStateException("Unable to deserialize chat data for key " + key, e);
        }
    }
}
