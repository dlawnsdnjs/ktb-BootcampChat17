package com.ktb.chatapp.websocket.socketio;

import com.ktb.chatapp.service.SessionService;
import com.ktb.chatapp.service.SessionValidationResult;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SocketSessionValidator {

    private final SessionService sessionService;
    private final ConcurrentMap<SessionKey, CachedValidation> cache = new ConcurrentHashMap<>();

    @Value("${socketio.session.validation-cache-ttl:5s}")
    private String validationCacheTtl;

    @Value("${socketio.session.activity-update-interval:30s}")
    private String activityUpdateInterval;

    public SessionValidationResult validate(String userId, String sessionId) {
        long now = System.nanoTime();
        SessionKey key = new SessionKey(userId, sessionId);
        CachedValidation cached = cache.get(key);
        if (cached != null && cached.expiresAtNanos > now) {
            return cached.result;
        }

        Duration activityInterval = DurationStyle.detectAndParse(activityUpdateInterval);
        SessionValidationResult result =
                sessionService.validateSession(userId, sessionId, activityInterval);
        long ttlNanos = DurationStyle.detectAndParse(validationCacheTtl).toNanos();
        cache.put(key, new CachedValidation(result, now + ttlNanos));
        return result;
    }

    public void invalidate(String userId) {
        cache.keySet().removeIf(key -> key.userId.equals(userId));
    }

    private record SessionKey(String userId, String sessionId) {
    }

    private record CachedValidation(SessionValidationResult result, long expiresAtNanos) {
    }
}
