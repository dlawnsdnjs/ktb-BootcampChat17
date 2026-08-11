package com.ktb.chatapp.websocket.socketio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class LocalChatDataStoreTest {

    private final LocalChatDataStore store = new LocalChatDataStore();

    @Test
    void storesValuesAndDeletesOnlyTheExpectedValue() {
        String trackerKey = "conn_users:index";
        SocketUser oldConnection = new SocketUser("user-1", "tester", "session-1", "socket-1");
        SocketUser newConnection = new SocketUser("user-1", "tester", "session-2", "socket-2");

        assertThat(store.getAndSetTracked(
                "conn_users:userid:user-1", oldConnection, SocketUser.class, trackerKey)).isEmpty();
        assertThat(store.getAndSetTracked(
                "conn_users:userid:user-1", newConnection, SocketUser.class, trackerKey))
                .contains(oldConnection);

        assertThat(store.get("conn_users:userid:user-1", SocketUser.class))
                .contains(newConnection);
        assertThat(store.trackedSize(trackerKey)).isEqualTo(1);
        assertThat(store.deleteIfEqualsTracked(
                "conn_users:userid:user-1", oldConnection, trackerKey)).isFalse();
        assertThat(store.deleteIfEqualsTracked(
                "conn_users:userid:user-1", newConnection, trackerKey)).isTrue();
        assertThat(store.get("conn_users:userid:user-1", SocketUser.class)).isEmpty();
        assertThat(store.trackedSize(trackerKey)).isZero();
    }

    @Test
    void mutatesRoomSetsWithoutReplacingOtherMembers() {
        String key = "userroom:roomids:user-1";

        store.addToSet(key, "room-1");
        store.addToSet(key, "room-2");

        assertThat(store.getSet(key)).isEqualTo(Set.of("room-1", "room-2"));
        assertThat(store.containsInSet(key, "room-2")).isTrue();

        store.removeFromSet(key, "room-1");
        assertThat(store.getSet(key)).containsExactly("room-2");

        store.removeFromSet(key, "room-2");
        assertThat(store.getSet(key)).isEmpty();
    }

    @Test
    void tracksSizeWithoutScanningStoredKeys() {
        String trackerKey = "conn_users:index";
        store.setTracked("conn_users:userid:user-1", "socket-1", trackerKey);
        store.setTracked("conn_users:userid:user-2", "socket-2", trackerKey);
        store.addToSet("userroom:roomids:user-1", "room-1");

        assertThat(store.trackedSize(trackerKey)).isEqualTo(2);
    }
}
