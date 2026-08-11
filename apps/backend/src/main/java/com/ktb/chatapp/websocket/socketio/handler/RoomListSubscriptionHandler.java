package com.ktb.chatapp.websocket.socketio.handler;

import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.JOIN_ROOM_LIST;
import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.LEAVE_ROOM_LIST;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.annotation.OnEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Limits room-list fan-out to clients that are currently displaying the room list. */
@Component
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
public class RoomListSubscriptionHandler {

    static final String ROOM_LIST = "room-list";

    @OnEvent(JOIN_ROOM_LIST)
    public void joinRoomList(SocketIOClient client) {
        if (client.get("user") != null) {
            client.joinRoom(ROOM_LIST);
        }
    }

    @OnEvent(LEAVE_ROOM_LIST)
    public void leaveRoomList(SocketIOClient client) {
        client.leaveRoom(ROOM_LIST);
    }
}
