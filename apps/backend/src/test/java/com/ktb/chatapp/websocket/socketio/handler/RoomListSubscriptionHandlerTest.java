package com.ktb.chatapp.websocket.socketio.handler;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.corundumstudio.socketio.SocketIOClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomListSubscriptionHandlerTest {

    @Mock private SocketIOClient client;

    private final RoomListSubscriptionHandler handler = new RoomListSubscriptionHandler();

    @Test
    void authenticatedClientCanSubscribeAndUnsubscribe() {
        when(client.get("user")).thenReturn(new Object());

        handler.joinRoomList(client);
        handler.leaveRoomList(client);

        verify(client).joinRoom("room-list");
        verify(client).leaveRoom("room-list");
    }

    @Test
    void unauthenticatedClientCannotSubscribe() {
        when(client.get("user")).thenReturn(null);

        handler.joinRoomList(client);

        verify(client, never()).joinRoom("room-list");
    }
}
