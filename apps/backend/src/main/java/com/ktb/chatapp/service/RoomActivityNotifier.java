package com.ktb.chatapp.service;

import com.ktb.chatapp.event.RoomActivityEvent;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 새 메시지가 저장되면 채팅방 목록의 활성도 지표를 갱신하도록 알린다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
public class RoomActivityNotifier {

    private final RecentMessageCounter recentMessageCounter;
    private final ApplicationEventPublisher eventPublisher;
    private final ScheduledExecutorService executor;
    private final long coalesceDelayMs;
    private final Set<String> pendingRoomIds = new LinkedHashSet<>();
    private boolean flushScheduled;

    public RoomActivityNotifier(
            RecentMessageCounter recentMessageCounter,
            ApplicationEventPublisher eventPublisher,
            @Qualifier("roomActivityExecutor") ScheduledExecutorService executor,
            @Value("${socketio.room-list.activity-coalesce-delay-ms:1000}") long coalesceDelayMs) {
        this.recentMessageCounter = recentMessageCounter;
        this.eventPublisher = eventPublisher;
        this.executor = executor;
        this.coalesceDelayMs = coalesceDelayMs;
    }

    public synchronized void notifyMessageStored(String roomId) {
        if (roomId == null) {
            return;
        }

        pendingRoomIds.add(roomId);
        if (flushScheduled) {
            return;
        }

        flushScheduled = true;
        try {
            executor.schedule(this::flush, coalesceDelayMs, TimeUnit.MILLISECONDS);
        } catch (RuntimeException exception) {
            flushScheduled = false;
            log.error("roomActivity 집계 예약 실패: roomId={}", roomId, exception);
        }
    }

    void flush() {
        Set<String> roomIds;
        synchronized (this) {
            roomIds = new LinkedHashSet<>(pendingRoomIds);
            pendingRoomIds.clear();
            flushScheduled = false;
        }

        for (String roomId : roomIds) {
            try {
                int recentMessageCount = recentMessageCounter.countRecentMessages(roomId);
                eventPublisher.publishEvent(new RoomActivityEvent(this, roomId, recentMessageCount));
            } catch (Exception e) {
                log.error("roomActivity 이벤트 발행 실패: roomId={}", roomId, e);
            }
        }
    }
}
