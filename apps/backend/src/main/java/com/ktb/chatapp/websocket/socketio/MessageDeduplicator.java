package com.ktb.chatapp.websocket.socketio;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Redis-backed idempotency state for at-least-once client message delivery. */
@Component
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MessageDeduplicator {

    private static final String PROCESSING = "processing";
    private static final String REJECTED = "rejected";
    private static final String KEY_PREFIX = "message:dedupe:";

    private final ChatDataStore dataStore;

    public Claim claim(String userId, String clientMessageId) {
        if (clientMessageId == null || clientMessageId.isBlank()) {
            return Claim.untracked();
        }

        String key = key(userId, clientMessageId);
        if (dataStore.setIfAbsent(key, PROCESSING)) {
            return Claim.accepted(key);
        }

        String stored = dataStore.get(key, String.class).orElse(PROCESSING);
        return Claim.duplicate(
                PROCESSING.equals(stored) || REJECTED.equals(stored) ? null : stored);
    }

    public void complete(Claim claim, String messageId) {
        if (claim.tracked() && claim.accepted()) {
            dataStore.set(claim.key(), messageId);
        }
    }

    public void reject(Claim claim) {
        if (claim.tracked() && claim.accepted()) {
            dataStore.set(claim.key(), REJECTED);
        }
    }

    public void release(Claim claim) {
        if (claim.tracked() && claim.accepted()) {
            dataStore.delete(claim.key());
        }
    }

    private String key(String userId, String clientMessageId) {
        return KEY_PREFIX + userId + ':' + clientMessageId;
    }

    public record Claim(boolean tracked, boolean accepted, String key, String messageId) {
        static Claim untracked() {
            return new Claim(false, true, null, null);
        }

        static Claim accepted(String key) {
            return new Claim(true, true, key, null);
        }

        static Claim duplicate(String messageId) {
            return new Claim(true, false, null, messageId);
        }
    }
}
