package com.ktb.chatapp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.corundumstudio.socketio.store.MemoryStoreFactory;
import com.corundumstudio.socketio.store.StoreFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.config.Config;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("Socket.IO Store 환경 설정")
class SocketIOStoreConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(SocketIOStoreConfig.class);

    @Test
    @DisplayName("설정이 없으면 기존 MemoryStoreFactory를 사용한다")
    void defaultsToMemoryStoreFactory() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(StoreFactory.class);
            assertThat(context.getBean(StoreFactory.class)).isInstanceOf(MemoryStoreFactory.class);
        });
    }

    @Test
    @DisplayName("memory를 명시하면 기존 MemoryStoreFactory를 사용한다")
    void usesMemoryStoreFactoryWhenExplicitlyConfigured() {
        contextRunner
                .withPropertyValues("socketio.store.type=memory")
                .run(context -> assertThat(context.getBean(StoreFactory.class))
                        .isInstanceOf(MemoryStoreFactory.class));
    }

    @Test
    @DisplayName("Socket.IO가 비활성화되면 StoreFactory도 생성하지 않는다")
    void doesNotCreateStoreFactoryWhenSocketIOIsDisabled() {
        contextRunner
                .withPropertyValues("socketio.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(StoreFactory.class));
    }

    @Test
    @DisplayName("Redisson 설정은 Redis 접속 정보와 TLS 여부를 반영한다")
    void buildsRedissonConfigFromRedisProperties() {
        Config config = SocketIOStoreConfig.buildRedissonConfig(
                "cache.internal", 6380, "socket-user", "secret", true);

        assertThat(SocketIOStoreConfig.buildRedisAddress("cache.internal", 6380, true))
                .isEqualTo("rediss://cache.internal:6380");
        assertThat(config.getUsername()).isEqualTo("socket-user");
        assertThat(config.getPassword()).isEqualTo("secret");
    }

    @Test
    @DisplayName("빈 인증 정보는 Redisson에 설정하지 않는다")
    void omitsBlankRedisCredentials() {
        Config config = SocketIOStoreConfig.buildRedissonConfig(
                "localhost", 6379, "", "", false);

        assertThat(SocketIOStoreConfig.buildRedisAddress("localhost", 6379, false))
                .isEqualTo("redis://localhost:6379");
        assertThat(config.getUsername()).isNull();
        assertThat(config.getPassword()).isNull();
    }

    @Test
    @DisplayName("잘못된 Redis 주소 설정은 시작 전에 거부한다")
    void rejectsInvalidRedisConnectionProperties() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SocketIOStoreConfig.buildRedissonConfig(
                        " ", 6379, "", "", false));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SocketIOStoreConfig.buildRedissonConfig(
                        "localhost", 0, "", "", false));
    }
}
