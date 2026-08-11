package com.ktb.chatapp.websocket.socketio;

import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.ROOM_ACTIVITY;
import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.ROOM_UPDATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.corundumstudio.socketio.BroadcastOperations;
import com.corundumstudio.socketio.SocketIOServer;
import com.ktb.chatapp.dto.RoomResponse;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomListEventDispatcherTest {

    @Mock private SocketIOServer socketIOServer;
    @Mock private BroadcastOperations roomListOperations;
    @Mock private ScheduledExecutorService executor;
    @Mock private ScheduledFuture<Object> scheduledFuture;

    private RoomListEventDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new RoomListEventDispatcher(socketIOServer, executor, 100);
        doReturn(scheduledFuture).when(executor)
                .schedule(any(Runnable.class), eq(100L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void defersBroadcastAndCoalescesLatestActivityPerRoom() {
        dispatcher.enqueueActivity("room-1", 1);
        dispatcher.enqueueActivity("room-1", 2);

        verifyNoInteractions(socketIOServer);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).schedule(taskCaptor.capture(), eq(100L), eq(TimeUnit.MILLISECONDS));
        when(socketIOServer.getRoomOperations("room-list")).thenReturn(roomListOperations);

        taskCaptor.getValue().run();

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(roomListOperations).sendEvent(eq(ROOM_ACTIVITY), payloadCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertEquals("room-1", payload.get("_id"));
        assertEquals(2, payload.get("recentMessageCount"));
    }

    @Test
    void keepsUpdatesForDifferentRoomsInTheSameFlush() {
        RoomResponse first = RoomResponse.builder().id("room-1").build();
        RoomResponse second = RoomResponse.builder().id("room-2").build();
        dispatcher.enqueueUpdated("room-1", first);
        dispatcher.enqueueUpdated("room-2", second);

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).schedule(taskCaptor.capture(), eq(100L), eq(TimeUnit.MILLISECONDS));
        when(socketIOServer.getRoomOperations("room-list")).thenReturn(roomListOperations);

        taskCaptor.getValue().run();

        verify(roomListOperations).sendEvent(ROOM_UPDATE, first);
        verify(roomListOperations).sendEvent(ROOM_UPDATE, second);
        verify(roomListOperations, times(2)).sendEvent(eq(ROOM_UPDATE), any());
    }
}
