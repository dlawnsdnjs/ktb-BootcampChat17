package com.ktb.chatapp.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.chatapp.config.MongoTestContainer;
import com.ktb.chatapp.config.RedisTestContainer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import({MongoTestContainer.class, RedisTestContainer.class})
@TestPropertySource(properties = {
        "socketio.enabled=false",
        "rate-limit.store.type=redis",
        "rate-limit.redis.key-prefix=test-rate-limit"
})
class RateLimitRedisIntegrationTest {

    @Autowired private RateLimitService rateLimitService;
    @Autowired private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearRedis() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    void concurrentRequestsUseOneAtomicSharedLimitAndTtl() throws Exception {
        List<Future<RateLimitCheckResult>> futures = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(16)) {
            for (int index = 0; index < 50; index++) {
                futures.add(executor.submit(() -> rateLimitService.checkRateLimit(
                        "shared-user", 25, Duration.ofSeconds(2))));
            }
        }

        long allowed = 0;
        for (Future<RateLimitCheckResult> future : futures) {
            if (future.get().allowed()) {
                allowed++;
            }
        }
        assertThat(allowed).isEqualTo(25);
        assertThat(redisTemplate.getExpire("test-rate-limit:shared-user"))
                .isBetween(1L, 2L);
    }

    @Test
    void expiredWindowStartsWithAFreshCounter() throws Exception {
        assertThat(rateLimitService.checkRateLimit(
                "ttl-user", 1, Duration.ofSeconds(1)).allowed()).isTrue();
        assertThat(rateLimitService.checkRateLimit(
                "ttl-user", 1, Duration.ofSeconds(1)).allowed()).isFalse();

        Thread.sleep(1_100L);

        assertThat(rateLimitService.checkRateLimit(
                "ttl-user", 1, Duration.ofSeconds(1)).allowed()).isTrue();
    }
}
