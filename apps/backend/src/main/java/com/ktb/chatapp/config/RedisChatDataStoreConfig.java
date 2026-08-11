package com.ktb.chatapp.config;

import com.corundumstudio.socketio.SocketIOServer;
import com.ktb.chatapp.websocket.socketio.ChatDataStore;
import com.ktb.chatapp.websocket.socketio.ChatDataStoreLeaseRefresher;
import com.ktb.chatapp.websocket.socketio.ConnectedUsers;
import com.ktb.chatapp.websocket.socketio.RedisChatDataStore;
import com.ktb.chatapp.websocket.socketio.UserRooms;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis-backed application chat state for multi-instance environments.
 *
 * <p>This is separate from netty-socketio's Redis StoreFactory. The StoreFactory
 * distributes Socket.IO state and events, while this store owns application-level
 * connected-user and user-room leases.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
public class RedisChatDataStoreConfig {

    @Bean
    @ConditionalOnProperty(name = "chat.store.type", havingValue = "redis")
    ChatDataStore redisChatDataStore(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${chat.store.lease-ttl:120s}") String leaseTtl,
            @Value("${chat.store.key-prefix:chat-data}") String keyPrefix) {
        return new RedisChatDataStore(
                redisTemplate,
                objectMapper,
                DurationStyle.detectAndParse(leaseTtl),
                keyPrefix);
    }

    @Bean
    @ConditionalOnProperty(name = "chat.store.type", havingValue = "redis")
    ChatDataStoreLeaseRefresher chatDataStoreLeaseRefresher(
            SocketIOServer socketIOServer,
            ConnectedUsers connectedUsers,
            UserRooms userRooms) {
        return new ChatDataStoreLeaseRefresher(socketIOServer, connectedUsers, userRooms);
    }
}
