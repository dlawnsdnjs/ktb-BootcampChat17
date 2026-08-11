package com.ktb.chatapp.websocket.socketio;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatDataStoreLeaseRefresherTest {

    @Mock private SocketIOServer socketIOServer;
    @Mock private ConnectedUsers connectedUsers;
    @Mock private UserRooms userRooms;
    @Mock private SocketIOClient client;

    @InjectMocks private ChatDataStoreLeaseRefresher refresher;

    @Test
    void refreshesLeasesOnlyForTheConnectionStillOwnedByThisInstance() {
        SocketUser user = new SocketUser("user-1", "tester", "session-1", "socket-1");
        when(socketIOServer.getAllClients()).thenReturn(List.of(client));
        when(client.get("user")).thenReturn(user);
        when(connectedUsers.refresh(user)).thenReturn(true);

        refresher.refreshActiveClientLeases();

        verify(connectedUsers).refresh(user);
        verify(userRooms).refresh(user.id());
    }

    @Test
    void doesNotRefreshRoomsAfterTheConnectionWasReplaced() {
        SocketUser user = new SocketUser("user-1", "tester", "session-1", "socket-1");
        when(socketIOServer.getAllClients()).thenReturn(List.of(client));
        when(client.get("user")).thenReturn(user);
        when(connectedUsers.refresh(user)).thenReturn(false);

        refresher.refreshActiveClientLeases();

        verify(userRooms, never()).refresh(user.id());
    }
}
