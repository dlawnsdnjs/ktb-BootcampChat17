package com.ktb.chatapp.config;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
public class RoomListEventConfig {

    @Bean(name = "roomListEventExecutor", destroyMethod = "shutdown")
    public ScheduledExecutorService roomListEventExecutor() {
        return newDaemonExecutor("room-list-events");
    }

    @Bean(name = "roomActivityExecutor", destroyMethod = "shutdown")
    public ScheduledExecutorService roomActivityExecutor() {
        return newDaemonExecutor("room-activity-events");
    }

    private ScheduledExecutorService newDaemonExecutor(String threadName) {
        return Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }
}
