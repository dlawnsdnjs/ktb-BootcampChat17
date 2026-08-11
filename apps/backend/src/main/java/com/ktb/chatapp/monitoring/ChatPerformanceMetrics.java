package com.ktb.chatapp.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/** Low-cardinality timers for the room-entry and message hot paths. */
@Component
@RequiredArgsConstructor
public class ChatPerformanceMetrics {

    private final MeterRegistry meterRegistry;

    public <T> T recordRoomPhase(String phase, Supplier<T> action) {
        return timer("chat.rooms.phase.duration", "phase", phase).record(action);
    }

    public void recordRoomPhase(String phase, Runnable action) {
        timer("chat.rooms.phase.duration", "phase", phase).record(action);
    }

    public <T> T recordMessagePhase(String phase, Supplier<T> action) {
        return timer("socketio.messages.phase.duration", "phase", phase).record(action);
    }

    public void recordMessagePhase(String phase, Runnable action) {
        timer("socketio.messages.phase.duration", "phase", phase).record(action);
    }

    public Timer.Sample startRoomHttp() {
        return Timer.start(meterRegistry);
    }

    public void stopRoomHttp(Timer.Sample sample, String endpoint, int status) {
        stopRoomHttp(sample, endpoint, Integer.toString(status));
    }

    public <T> ResponseEntity<T> recordRoomHttp(
            String endpoint, Supplier<ResponseEntity<T>> action) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            ResponseEntity<T> response = action.get();
            stopRoomHttp(sample, endpoint, Integer.toString(response.getStatusCode().value()));
            return response;
        } catch (RuntimeException exception) {
            stopRoomHttp(sample, endpoint, "exception");
            throw exception;
        }
    }

    private void stopRoomHttp(Timer.Sample sample, String endpoint, String status) {
        sample.stop(timer(
                "chat.rooms.http.duration",
                "endpoint", endpoint,
                "status", status));
    }

    private Timer timer(String name, String... tags) {
        return Timer.builder(name)
                .publishPercentileHistogram()
                .tags(tags)
                .register(meterRegistry);
    }
}
