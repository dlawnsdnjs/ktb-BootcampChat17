package com.ktb.chatapp.websocket.socketio;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ConnectedUsers {
    
    private static final String USER_SOCKET_KEY_PREFIX = "conn_users:userid:";
    private static final String USER_SOCKET_TRACKER_KEY = "conn_users:index";
    
    private final ChatDataStore chatDataStore;
    
    public SocketUser get(String userId) {
        return chatDataStore.get(buildKey(userId), SocketUser.class).orElse(null);
    }
    
    public SocketUser set(String userId, SocketUser socketUser) {
        return chatDataStore.getAndSetTracked(
                buildKey(userId),
                socketUser,
                SocketUser.class,
                USER_SOCKET_TRACKER_KEY).orElse(null);
    }
    
    public void del(String userId) {
        chatDataStore.deleteTracked(buildKey(userId), USER_SOCKET_TRACKER_KEY);
    }

    /**
     * Remove only the socket that is still the user's current connection.
     */
    public boolean delIfCurrent(String userId, String socketId) {
        SocketUser current = get(userId);
        if (current == null || !socketId.equals(current.socketId())) {
            return false;
        }
        return chatDataStore.deleteIfEqualsTracked(
                buildKey(userId),
                current,
                USER_SOCKET_TRACKER_KEY);
    }

    public boolean refresh(SocketUser socketUser) {
        return chatDataStore.touchIfEqualsTracked(
                buildKey(socketUser.id()),
                socketUser,
                USER_SOCKET_TRACKER_KEY);
    }
    
    public int size() {
        return chatDataStore.trackedSize(USER_SOCKET_TRACKER_KEY);
    }
    
    private String buildKey(String userId) {
        return USER_SOCKET_KEY_PREFIX + userId;
    }
}
