package com.ktb.chatapp.websocket.socketio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ktb.chatapp.config.MongoTestContainer;
import com.ktb.chatapp.config.RedisTestContainer;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@Import({MongoTestContainer.class, RedisTestContainer.class})
@TestPropertySource(properties = "socketio.enabled=false")
class RedisChatDataStoreIntegrationTest {

    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private ObjectMapper objectMapper;

    private RedisChatDataStore store;

    @BeforeEach
    void setUp() {
        store = new RedisChatDataStore(
                redisTemplate,
                objectMapper,
                Duration.ofSeconds(5),
                "test-chat-data:" + UUID.randomUUID());
    }

    @Test
    void roundTripsJsonAndConditionallyDeletesTheCurrentValue() {
        String key = "conn_users:userid:user-1";
        String trackerKey = "conn_users:index";
        SocketUser oldConnection = new SocketUser("user-1", "tester", "session-1", "socket-1");
        SocketUser currentConnection = new SocketUser("user-1", "tester", "session-2", "socket-2");

        assertThat(store.getAndSetTracked(key, oldConnection, SocketUser.class, trackerKey)).isEmpty();
        assertThat(store.getAndSetTracked(key, currentConnection, SocketUser.class, trackerKey))
                .contains(oldConnection);

        assertThat(store.get(key, SocketUser.class)).contains(currentConnection);
        assertThat(store.trackedSize(trackerKey)).isEqualTo(1);
        assertThat(store.deleteIfEqualsTracked(key, oldConnection, trackerKey)).isFalse();
        assertThat(store.trackedSize(trackerKey)).isEqualTo(1);
        assertThat(store.deleteIfEqualsTracked(key, currentConnection, trackerKey)).isTrue();
        assertThat(store.get(key, SocketUser.class)).isEmpty();
        assertThat(store.trackedSize(trackerKey)).isZero();
    }

    @Test
    void atomicallyReturnsThePreviousValueAcrossStoreInstances() throws Exception {
        String keyPrefix = "test-chat-data:" + UUID.randomUUID();
        RedisChatDataStore firstStore = new RedisChatDataStore(
                redisTemplate, objectMapper, Duration.ofSeconds(5), keyPrefix);
        RedisChatDataStore secondStore = new RedisChatDataStore(
                redisTemplate, objectMapper, Duration.ofSeconds(5), keyPrefix);
        String key = "conn_users:userid:user-1";
        String trackerKey = "conn_users:index";
        SocketUser initial = new SocketUser("user-1", "tester", "session-0", "socket-0");
        SocketUser first = new SocketUser("user-1", "tester", "session-1", "socket-1");
        SocketUser second = new SocketUser("user-1", "tester", "session-2", "socket-2");
        firstStore.setTracked(key, initial, trackerKey);

        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var firstSwap = executor.submit(() -> {
                start.await();
                return firstStore.getAndSetTracked(key, first, SocketUser.class, trackerKey);
            });
            var secondSwap = executor.submit(() -> {
                start.await();
                return secondStore.getAndSetTracked(key, second, SocketUser.class, trackerKey);
            });

            start.countDown();
            List<SocketUser> previousValues = List.of(
                    firstSwap.get(3, TimeUnit.SECONDS).orElseThrow(),
                    secondSwap.get(3, TimeUnit.SECONDS).orElseThrow());

            assertThat(previousValues).contains(initial);
            assertThat(previousValues).anyMatch(value -> value.equals(first) || value.equals(second));
            assertThat(firstStore.get(key, SocketUser.class))
                    .isPresent()
                    .get()
                    .isIn(first, second);
            assertThat(firstStore.trackedSize(trackerKey)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void usesNativeRedisSetOperationsForRooms() {
        String key = "userroom:roomids:user-1";

        store.addToSet(key, "room-1");
        store.addToSet(key, "room-2");

        assertThat(store.getSet(key)).containsExactlyInAnyOrder("room-1", "room-2");
        assertThat(store.containsInSet(key, "room-1")).isTrue();

        store.removeFromSet(key, "room-1");
        assertThat(store.getSet(key)).containsExactly("room-2");
    }

    @Test
    void expiresStateWhenAnInstanceStopsRenewingItsLease() {
        RedisChatDataStore shortLeaseStore = new RedisChatDataStore(
                redisTemplate,
                objectMapper,
                Duration.ofMillis(250),
                "test-chat-data:" + UUID.randomUUID());
        String key = "conn_users:userid:user-1";
        String trackerKey = "conn_users:index";
        SocketUser user = new SocketUser("user-1", "tester", "session-1", "socket-1");

        shortLeaseStore.setTracked(key, user, trackerKey);
        assertThat(shortLeaseStore.touchIfEqualsTracked(key, user, trackerKey)).isTrue();

        await().atMost(Duration.ofSeconds(3))
                .untilAsserted(() -> {
                    assertThat(shortLeaseStore.get(key, SocketUser.class)).isEmpty();
                    assertThat(shortLeaseStore.trackedSize(trackerKey)).isZero();
                });
    }
}
