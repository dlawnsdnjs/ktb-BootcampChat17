package com.ktb.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnDisconnect;
import com.ktb.chatapp.websocket.socketio.ConnectedUsers;
import com.ktb.chatapp.websocket.socketio.SocketUser;
import com.ktb.chatapp.websocket.socketio.UserRooms;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.*;

/**
 * Socket.IO Chat Handler
 * 어노테이션 기반 이벤트 처리와 인증 흐름을 정의한다.
 * 연결/해제 및 중복 로그인 처리를 담당
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
public class ConnectionLoginHandler {

    private static final String USER_ROOM_PREFIX = "user:";
    private static final String SESSION_ROOM_PREFIX = "session:";

    private final SocketIOServer socketIOServer;
    private final ConnectedUsers connectedUsers;
    private final UserRooms userRooms;
    private final RoomJoinHandler roomJoinHandler;
    private final RoomLeaveHandler roomLeaveHandler;

    public ConnectionLoginHandler(
            SocketIOServer socketIOServer,
            ConnectedUsers connectedUsers,
            UserRooms userRooms,
            RoomJoinHandler roomJoinHandler,
            RoomLeaveHandler roomLeaveHandler,
            MeterRegistry meterRegistry) {
        this.socketIOServer = socketIOServer;
        this.connectedUsers = connectedUsers;
        this.userRooms = userRooms;
        this.roomJoinHandler = roomJoinHandler;
        this.roomLeaveHandler = roomLeaveHandler;

        // Register gauge metric for concurrent users
        Gauge.builder("socketio.concurrent.users", connectedUsers::size)
                .description("Current number of concurrent Socket.IO users")
                .register(meterRegistry);
    }
    
    /**
     * auth 처리가 선행되어야 해서 @OnConnect 대신 별도 메서드로 구현
     */
    public void onConnect(SocketIOClient client, SocketUser user) {
        String userId = user.id();
        
        try {
            client.set("user", user);
            client.joinRooms(connectionRooms(user));

            SocketUser previousUser = connectedUsers.set(userId, user);
            endPreviousSession(user, previousUser);
            
            userRooms.get(userId).forEach(roomId -> {
                // 재접속 시 기존 참여 방 재입장 처리
                roomJoinHandler.handleJoinRoom(client, roomId);
            });

            log.info("Socket.IO user connected: {} ({}) - Total concurrent users: {}",
                    getUserName(client), userId, connectedUsers.size());
            
        } catch (Exception e) {
            log.error("Error handling Socket.IO connection", e);
            client.sendEvent(ERROR, Map.of(
                    "message", "연결 처리 중 오류가 발생했습니다."
            ));
        }
    }
    
    @OnDisconnect
    public void onDisconnect(SocketIOClient client) {
        String userId = getUserId(client);
        String userName = getUserName(client);
        
        try {
            if (userId == null) {
                return;
            }
            
            String socketId = client.getSessionId().toString();

            // 조회와 삭제 사이에 신규 연결이 등록되어도 그 연결은 삭제하지 않는다.
            if (connectedUsers.delIfCurrent(userId, socketId)) {
                userRooms.get(userId).forEach(roomId ->
                        roomLeaveHandler.handleLeaveRoom(client, roomId));
            } else {
                log.warn("Socket.IO disconnect: User {} has a different active connection. Skipping cleanup.", userId);
            }

            client.leaveRooms(connectionRooms(getUserDto(client)));
            client.del("user");
            client.disconnect();

            log.info("Socket.IO user disconnected: {} ({}) - Total concurrent users: {}",
                    userName, userId, connectedUsers.size());
        } catch (Exception e) {
            log.error("Error handling Socket.IO disconnection", e);
            client.sendEvent(ERROR, Map.of(
                "message", "연결 종료 처리 중 오류가 발생했습니다."
            ));
        }
        
    }
    
    private SocketUser getUserDto(SocketIOClient client) {
        return client.get("user");
    }
    
    private String getUserId(SocketIOClient client) {
        SocketUser user = getUserDto(client);
        return user != null ? user.id() : null;
    }
    
    private String getUserName(SocketIOClient client) {
        SocketUser user = getUserDto(client);
        return user != null ? user.name() : null;
    }
    
    private void endPreviousSession(
            SocketUser currentUser,
            SocketUser previousUser) {
        if (previousUser == null
                || Objects.equals(previousUser.authSessionId(), currentUser.authSessionId())) {
            return;
        }

        var previousSession = socketIOServer.getRoomOperations(
                SESSION_ROOM_PREFIX + previousUser.authSessionId());
        previousSession.sendEvent(SESSION_ENDED, Map.of(
                "reason", "duplicate_login",
                "message", "다른 기기에서 로그인하여 현재 세션이 종료되었습니다."
        ));
    }

    private Set<String> connectionRooms(SocketUser user) {
        return Set.of(
                USER_ROOM_PREFIX + user.id(),
                SESSION_ROOM_PREFIX + user.authSessionId());
    }
}
