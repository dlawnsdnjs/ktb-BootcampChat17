package com.ktb.chatapp.config;

import com.ktb.chatapp.service.cache.ChatReadCacheNames;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Redis read-through caches for rooms and immutable message page data.
 *
 * <p>Time-to-idle is enabled, so every successful lookup touches the Redis TTL.
 * Cache failures are deliberately fail-open: MongoDB remains the source of truth
 * and a Redis outage must not break chat behavior.</p>
 */
@Slf4j
@EnableCaching
@Configuration(proxyBeanMethods = false)
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@ConditionalOnProperty(name = "chat.read-cache.enabled", havingValue = "true")
public class ChatReadCacheConfig implements CachingConfigurer {

    @Bean
    public CacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Value("${chat.read-cache.key-prefix:chat-read}") String keyPrefix,
            @Value("${chat.read-cache.room-detail-ttl:30m}") String roomDetailTtl,
            @Value("${chat.read-cache.room-list-ttl:5m}") String roomListTtl,
            @Value("${chat.read-cache.message-latest-ttl:1m}") String messageLatestTtl,
            @Value("${chat.read-cache.message-history-ttl:30m}") String messageHistoryTtl) {

        RedisCacheConfiguration defaults = configuration(
                DurationStyle.detectAndParse(messageLatestTtl), keyPrefix);

        Map<String, RedisCacheConfiguration> configurations = Map.of(
                ChatReadCacheNames.ROOM_BY_ID,
                configuration(DurationStyle.detectAndParse(roomDetailTtl), keyPrefix),
                ChatReadCacheNames.ROOM_LIST,
                configuration(DurationStyle.detectAndParse(roomListTtl), keyPrefix),
                ChatReadCacheNames.MESSAGE_LATEST,
                configuration(DurationStyle.detectAndParse(messageLatestTtl), keyPrefix),
                ChatReadCacheNames.MESSAGE_HISTORY,
                configuration(DurationStyle.detectAndParse(messageHistoryTtl), keyPrefix));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(configurations)
                .build();
    }

    private RedisCacheConfiguration configuration(Duration ttl, String keyPrefix) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .enableTimeToIdle()
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> keyPrefix + "::" + cacheName + "::");
    }

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new FailOpenCacheErrorHandler();
    }

    private static final class FailOpenCacheErrorHandler implements CacheErrorHandler {
        private static final long LOG_INTERVAL_MS = Duration.ofSeconds(30).toMillis();
        private final AtomicLong nextLogAt = new AtomicLong();

        @Override
        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
            log("get", exception, cache, key);
        }

        @Override
        public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
            log("put", exception, cache, key);
        }

        @Override
        public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
            log("evict", exception, cache, key);
        }

        @Override
        public void handleCacheClearError(RuntimeException exception, Cache cache) {
            log("clear", exception, cache, "*");
        }

        private void log(String operation, RuntimeException exception, Cache cache, Object key) {
            long now = System.currentTimeMillis();
            long next = nextLogAt.get();
            if (now >= next && nextLogAt.compareAndSet(next, now + LOG_INTERVAL_MS)) {
                ChatReadCacheConfig.log.warn(
                        "Chat read cache {} failed; falling back to MongoDB. cache={}, key={}",
                        operation, cache.getName(), key, exception);
            }
        }
    }
}
