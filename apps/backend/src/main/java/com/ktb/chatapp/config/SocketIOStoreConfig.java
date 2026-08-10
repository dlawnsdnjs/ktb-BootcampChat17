package com.ktb.chatapp.config;

import com.corundumstudio.socketio.store.MemoryStoreFactory;
import com.corundumstudio.socketio.store.RedissonStoreFactory;
import com.corundumstudio.socketio.store.StoreFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 실행 환경에 맞는 Socket.IO 내부 StoreFactory를 구성한다.
 *
 * <p>프로필을 지정하지 않은 실행과 테스트에서는 기존 메모리 저장소를 기본으로 유지하고,
 * dev와 prod 환경에서는 {@code socketio.store.type=redis}를 통해 Redis 기반 세션 저장소와
 * Pub/Sub을 사용한다.
 * 이 설정은 애플리케이션의 {@code ChatDataStore} 구현과는 별개다.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
public class SocketIOStoreConfig {

    @Bean
    @ConditionalOnProperty(name = "socketio.store.type", havingValue = "memory", matchIfMissing = true)
    StoreFactory memorySocketIOStoreFactory() {
        log.info("Socket.IO store configured with local memory");
        return new MemoryStoreFactory();
    }

    @Bean
    @ConditionalOnProperty(name = "socketio.store.type", havingValue = "redis")
    StoreFactory redisSocketIOStoreFactory(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.username:}") String username,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${socketio.redis.ssl:false}") boolean ssl) {
        Config redissonConfig = buildRedissonConfig(host, port, username, password, ssl);
        RedissonClient redissonClient = Redisson.create(redissonConfig);

        log.info("Socket.IO store configured with Redis at {}:{} (ssl={})", host, port, ssl);
        return new IdempotentRedissonStoreFactory(redissonClient);
    }

    static Config buildRedissonConfig(
            String host,
            int port,
            String username,
            String password,
            boolean ssl) {
        if (!StringUtils.hasText(host)) {
            throw new IllegalArgumentException("Redis host must not be blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Redis port must be between 1 and 65535");
        }
        Config config = new Config();
        config.useSingleServer()
                .setAddress(buildRedisAddress(host, port, ssl));

        if (StringUtils.hasText(username)) {
            config.setUsername(username);
        }
        if (StringUtils.hasText(password)) {
            config.setPassword(password);
        }
        return config;
    }

    static String buildRedisAddress(String host, int port, boolean ssl) {
        return (ssl ? "rediss://" : "redis://") + host + ":" + port;
    }

    /**
     * SocketIOServer와 Spring이 종료 과정에서 StoreFactory#shutdown을 각각 호출해도
     * Redisson 연결 종료는 한 번만 수행되도록 한다.
     */
    private static final class IdempotentRedissonStoreFactory extends RedissonStoreFactory {

        private final RedissonClient redissonClient;
        private final AtomicBoolean shutdown = new AtomicBoolean();

        private IdempotentRedissonStoreFactory(RedissonClient redissonClient) {
            super(redissonClient);
            this.redissonClient = redissonClient;
        }

        @Override
        public void shutdown() {
            if (shutdown.compareAndSet(false, true)) {
                redissonClient.shutdown();
            }
        }
    }
}
