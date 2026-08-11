package com.ktb.chatapp.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.corundumstudio.socketio.SocketIOServer;
import com.ktb.chatapp.websocket.socketio.ChatDataStore;
import com.ktb.chatapp.websocket.socketio.ChatDataStoreLeaseRefresher;
import com.ktb.chatapp.websocket.socketio.ConnectedUsers;
import com.ktb.chatapp.websocket.socketio.RedisChatDataStore;
import com.ktb.chatapp.websocket.socketio.UserRooms;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

class RedisChatDataStoreConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RedisChatDataStoreConfig.class)
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(tools.jackson.databind.ObjectMapper.class, () -> JsonMapper.builder().build())
            .withBean(SocketIOServer.class, () -> mock(SocketIOServer.class))
            .withBean(ConnectedUsers.class, () -> mock(ConnectedUsers.class))
            .withBean(UserRooms.class, () -> mock(UserRooms.class));

    @Test
    void createsRedisStoreAndLeaseRefresherWhenRedisIsSelected() {
        contextRunner
                .withPropertyValues("chat.store.type=redis")
                .run(context -> {
                    assertThat(context).hasSingleBean(ChatDataStore.class);
                    assertThat(context.getBean(ChatDataStore.class)).isInstanceOf(RedisChatDataStore.class);
                    assertThat(context).hasSingleBean(ChatDataStoreLeaseRefresher.class);
                });
    }

    @Test
    void doesNotCreateRedisStoreWhenMemoryIsSelected() {
        contextRunner
                .withPropertyValues("chat.store.type=memory")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ChatDataStore.class);
                    assertThat(context).doesNotHaveBean(ChatDataStoreLeaseRefresher.class);
                });
    }

    @Test
    void doesNotCreateRedisStoreWhenSocketIoIsDisabled() {
        contextRunner
                .withPropertyValues("chat.store.type=redis", "socketio.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ChatDataStore.class));
    }
}
