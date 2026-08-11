package com.ktb.chatapp.websocket.socketio;

import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.ROOM_ACTIVITY;
import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.ROOM_CREATED;
import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.ROOM_UPDATE;

import com.corundumstudio.socketio.BroadcastOperations;
import com.corundumstudio.socketio.SocketIOServer;
import com.ktb.chatapp.dto.RoomResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Moves room-list fan-out off request threads and collapses repeated updates per room. */
@Slf4j
@Component
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
public class RoomListEventDispatcher {

    private static final String ROOM_LIST = "room-list";

    private final SocketIOServer socketIOServer;
    private final ScheduledExecutorService executor;
    private final long coalesceDelayMs;
    private final Map<String, RoomResponse> created = new LinkedHashMap<>();
    private final Map<String, RoomResponse> updated = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> activity = new LinkedHashMap<>();
    private boolean flushScheduled;

    public RoomListEventDispatcher(
            SocketIOServer socketIOServer,
            @Qualifier("roomListEventExecutor") ScheduledExecutorService executor,
            @Value("${socketio.room-list.coalesce-delay-ms:100}") long coalesceDelayMs) {
        this.socketIOServer = socketIOServer;
        this.executor = executor;
        this.coalesceDelayMs = coalesceDelayMs;
    }

    public synchronized void enqueueCreated(RoomResponse response) {
        created.put(response.getId(), response);
        scheduleFlush();
    }

    public synchronized void enqueueUpdated(String roomId, RoomResponse response) {
        updated.put(roomId, response);
        scheduleFlush();
    }

    public synchronized void enqueueActivity(String roomId, int recentMessageCount) {
        activity.put(roomId, Map.of(
                "_id", roomId,
                "recentMessageCount", recentMessageCount));
        scheduleFlush();
    }

    private void scheduleFlush() {
        if (flushScheduled) {
            return;
        }
        flushScheduled = true;
        try {
            executor.schedule(this::flush, coalesceDelayMs, TimeUnit.MILLISECONDS);
        } catch (RuntimeException exception) {
            flushScheduled = false;
            throw exception;
        }
    }

    void flush() {
        List<PendingEvent> events;
        synchronized (this) {
            events = new ArrayList<>(created.size() + updated.size() + activity.size());
            created.values().forEach(payload -> events.add(new PendingEvent(ROOM_CREATED, payload)));
            updated.values().forEach(payload -> events.add(new PendingEvent(ROOM_UPDATE, payload)));
            activity.values().forEach(payload -> events.add(new PendingEvent(ROOM_ACTIVITY, payload)));
            created.clear();
            updated.clear();
            activity.clear();
            flushScheduled = false;
        }

        if (events.isEmpty()) {
            return;
        }

        BroadcastOperations roomList = socketIOServer.getRoomOperations(ROOM_LIST);
        for (PendingEvent event : events) {
            try {
                roomList.sendEvent(event.name(), event.payload());
            } catch (RuntimeException exception) {
                log.error("Failed to broadcast {} to room list", event.name(), exception);
            }
        }
    }

    private record PendingEvent(String name, Object payload) {}
}
