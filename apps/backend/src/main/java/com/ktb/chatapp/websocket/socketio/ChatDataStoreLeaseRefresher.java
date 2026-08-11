package com.ktb.chatapp.websocket.socketio;

import com.corundumstudio.socketio.SocketIOServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Renews Redis leases for sockets owned by this application instance.
 *
 * <p>When the instance terminates without receiving disconnect callbacks, lease
 * renewal stops and Redis removes its stale connection state after the configured TTL.
 */
@Slf4j
@RequiredArgsConstructor
public class ChatDataStoreLeaseRefresher {

    private final SocketIOServer socketIOServer;
    private final ConnectedUsers connectedUsers;
    private final UserRooms userRooms;

    @Scheduled(fixedDelayString = "${chat.store.lease-refresh-ms:30000}")
    public void refreshActiveClientLeases() {
        try {
            socketIOServer.getAllClients().forEach(client -> {
                SocketUser user = client.get("user");
                if (user == null) {
                    return;
                }

                if (connectedUsers.refresh(user)) {
                    userRooms.refresh(user.id());
                }
            });
        } catch (RuntimeException e) {
            // Do not fall back to local memory: surfacing the failure avoids split-brain state.
            log.error("Failed to refresh chat data store leases", e);
        }
    }
}
