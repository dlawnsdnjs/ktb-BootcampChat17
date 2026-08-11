package com.ktb.chatapp.websocket.socketio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConnectedUsersTest {

    @Test
    void setAtomicallyReturnsThePreviousConnection() {
        LocalChatDataStore store = new LocalChatDataStore();
        ConnectedUsers connectedUsers = new ConnectedUsers(store);
        SocketUser first = new SocketUser("user-1", "tester", "session-1", "socket-1");
        SocketUser second = new SocketUser("user-1", "tester", "session-2", "socket-2");

        assertThat(connectedUsers.set(first.id(), first)).isNull();
        assertThat(connectedUsers.set(second.id(), second)).isEqualTo(first);
        assertThat(connectedUsers.get(second.id())).isEqualTo(second);
        assertThat(connectedUsers.size()).isEqualTo(1);
    }

    @Test
    void staleDisconnectDoesNotDeleteTheCurrentConnection() {
        LocalChatDataStore store = new LocalChatDataStore();
        ConnectedUsers connectedUsers = new ConnectedUsers(store);
        SocketUser current = new SocketUser("user-1", "tester", "session-2", "socket-2");
        connectedUsers.set(current.id(), current);

        assertThat(connectedUsers.delIfCurrent(current.id(), "socket-1")).isFalse();
        assertThat(connectedUsers.get(current.id())).isEqualTo(current);

        assertThat(connectedUsers.delIfCurrent(current.id(), current.socketId())).isTrue();
        assertThat(connectedUsers.get(current.id())).isNull();
    }

    @Test
    void sizeDoesNotIncludeUserRoomKeys() {
        LocalChatDataStore store = new LocalChatDataStore();
        ConnectedUsers connectedUsers = new ConnectedUsers(store);
        UserRooms userRooms = new UserRooms(store);

        connectedUsers.set("user-1", new SocketUser("user-1", "tester", "session-1", "socket-1"));
        userRooms.add("user-1", "room-1");

        assertThat(connectedUsers.size()).isEqualTo(1);
    }
}
