package com.ktb.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.BroadcastOperations;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.ktb.chatapp.websocket.socketio.ConnectedUsers;
import com.ktb.chatapp.websocket.socketio.SocketUser;
import com.ktb.chatapp.websocket.socketio.UserRooms;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.SESSION_ENDED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectionLoginHandlerTest {

    @Mock private SocketIOServer socketIOServer;
    @Mock private ConnectedUsers connectedUsers;
    @Mock private UserRooms userRooms;
    @Mock private RoomJoinHandler roomJoinHandler;
    @Mock private RoomLeaveHandler roomLeaveHandler;
    @Mock private SocketIOClient client;
    @Mock private BroadcastOperations previousSessionOperations;

    private ConnectionLoginHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ConnectionLoginHandler(
                socketIOServer,
                connectedUsers,
                userRooms,
                roomJoinHandler,
                roomLeaveHandler,
                new SimpleMeterRegistry());
    }

    @Test
    void onConnect_setsUserRejoinsRoomsStoresUserAndJoinsUserRooms() {
        SocketUser user = new SocketUser("user-1", "tester", "session-1", "socket-1");
        when(client.get("user")).thenReturn(user);
        when(userRooms.get(user.id())).thenReturn(Set.of("room-1", "room-2"));

        handler.onConnect(client, user);

        verify(client).set("user", user);
        verify(roomJoinHandler).handleJoinRoom(client, "room-1");
        verify(roomJoinHandler).handleJoinRoom(client, "room-2");
        verify(connectedUsers).set(user.id(), user);
        verify(client).joinRooms(Set.of(
                "user:" + user.id(),
                "session:" + user.authSessionId(),
                "room-list"));
        verify(socketIOServer, never()).getRoomOperations(anyString());
    }

    @Test
    void onConnect_notifiesAndImmediatelyEndsOnlyThePreviousSession() {
        SocketUser previous = new SocketUser("user-1", "tester", "session-1", "socket-1");
        SocketUser current = new SocketUser("user-1", "tester", "session-2", "socket-2");
        when(client.get("user")).thenReturn(current);
        when(userRooms.get(current.id())).thenReturn(Set.of());
        when(connectedUsers.set(current.id(), current)).thenReturn(previous);
        when(socketIOServer.getRoomOperations("session:" + previous.authSessionId()))
                .thenReturn(previousSessionOperations);

        handler.onConnect(client, current);

        verify(previousSessionOperations).sendEvent(eq(SESSION_ENDED), any());
        verify(socketIOServer).getRoomOperations("session:" + previous.authSessionId());
        verify(socketIOServer, never()).getClient(any());
    }

    @Test
    void onConnect_doesNotEndAnotherSocketFromTheSameAuthSession() {
        SocketUser previous = new SocketUser("user-1", "tester", "session-1", "socket-1");
        SocketUser current = new SocketUser("user-1", "tester", "session-1", "socket-2");
        when(client.get("user")).thenReturn(current);
        when(userRooms.get(current.id())).thenReturn(Set.of());
        when(connectedUsers.set(current.id(), current)).thenReturn(previous);

        handler.onConnect(client, current);

        verify(socketIOServer, never()).getRoomOperations(anyString());
        verifyNoInteractions(previousSessionOperations);
    }

    @Test
    void onDisconnect_removesCurrentConnectionAndLeavesRooms() {
        UUID socketId = UUID.randomUUID();
        SocketUser user = new SocketUser("user-1", "tester", "session-1", socketId.toString());
        when(client.get("user")).thenReturn(user);
        when(userRooms.get(user.id())).thenReturn(Set.of("room-1"));
        when(client.getSessionId()).thenReturn(socketId);
        when(connectedUsers.delIfCurrent(user.id(), socketId.toString())).thenReturn(true);

        handler.onDisconnect(client);

        verify(roomLeaveHandler).handleLeaveRoom(client, "room-1");
        verify(connectedUsers).delIfCurrent(user.id(), socketId.toString());
        verify(client).leaveRooms(Set.of(
                "user:" + user.id(),
                "session:" + user.authSessionId(),
                "room-list"));
        verify(client).del("user");
        verify(client).disconnect();
    }

    @Test
    void onDisconnect_doesNotRemoveRoomsOwnedByANewerConnection() {
        UUID socketId = UUID.randomUUID();
        SocketUser user = new SocketUser("user-1", "tester", "session-1", socketId.toString());
        when(client.get("user")).thenReturn(user);
        when(client.getSessionId()).thenReturn(socketId);
        when(connectedUsers.delIfCurrent(user.id(), socketId.toString())).thenReturn(false);

        handler.onDisconnect(client);

        verify(userRooms, never()).get(user.id());
        verifyNoInteractions(roomLeaveHandler);
        verify(client).disconnect();
    }
}
