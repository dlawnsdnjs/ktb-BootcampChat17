package com.ktb.chatapp.websocket.socketio;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local in-memory implementation of ChatDataStore using ConcurrentHashMap.
 * Thread-safe storage for chat-related data without external dependencies.
 */
public class LocalChatDataStore implements ChatDataStore {
    
    private final ConcurrentHashMap<String, Object> storage = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> trackers = new HashMap<>();
    
    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = storage.get(key);
        if (value == null) {
            return Optional.empty();
        }
        
        try {
            return Optional.of(type.cast(value));
        } catch (ClassCastException e) {
            return Optional.empty();
        }
    }
    
    @Override
    public void set(String key, Object value) {
        storage.put(key, value);
    }
    
    @Override
    public void delete(String key) {
        storage.remove(key);
    }

    @Override
    public synchronized void setTracked(String key, Object value, String trackerKey) {
        storage.put(key, value);
        trackers.computeIfAbsent(trackerKey, ignored -> new HashSet<>()).add(key);
    }

    @Override
    public synchronized <T> Optional<T> getAndSetTracked(
            String key,
            Object value,
            Class<T> type,
            String trackerKey) {
        Object previous = storage.put(key, value);
        trackers.computeIfAbsent(trackerKey, ignored -> new HashSet<>()).add(key);
        if (previous == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(type.cast(previous));
        } catch (ClassCastException e) {
            return Optional.empty();
        }
    }

    @Override
    public synchronized void deleteTracked(String key, String trackerKey) {
        storage.remove(key);
        removeFromTracker(key, trackerKey);
    }

    @Override
    public synchronized boolean deleteIfEqualsTracked(
            String key,
            Object expectedValue,
            String trackerKey) {
        if (!storage.remove(key, expectedValue)) {
            return false;
        }
        removeFromTracker(key, trackerKey);
        return true;
    }

    @Override
    public Set<String> getSet(String key) {
        return copyStringSet(storage.get(key));
    }

    @Override
    public void addToSet(String key, String value) {
        storage.compute(key, (ignored, storedValue) -> {
            Set<String> values = copyStringSet(storedValue);
            values.add(value);
            return Set.copyOf(values);
        });
    }

    @Override
    public void removeFromSet(String key, String value) {
        storage.computeIfPresent(key, (ignored, storedValue) -> {
            Set<String> values = copyStringSet(storedValue);
            values.remove(value);
            return values.isEmpty() ? null : Set.copyOf(values);
        });
    }

    @Override
    public boolean containsInSet(String key, String value) {
        return getSet(key).contains(value);
    }

    @Override
    public boolean touch(String key) {
        return storage.containsKey(key);
    }

    @Override
    public synchronized boolean touchIfEqualsTracked(
            String key,
            Object expectedValue,
            String trackerKey) {
        return Objects.equals(storage.get(key), expectedValue);
    }

    @Override
    public synchronized int trackedSize(String trackerKey) {
        Set<String> trackedKeys = trackers.get(trackerKey);
        return trackedKeys == null ? 0 : trackedKeys.size();
    }

    private void removeFromTracker(String key, String trackerKey) {
        Set<String> trackedKeys = trackers.get(trackerKey);
        if (trackedKeys == null) {
            return;
        }
        trackedKeys.remove(key);
        if (trackedKeys.isEmpty()) {
            trackers.remove(trackerKey);
        }
    }

    private Set<String> copyStringSet(Object storedValue) {
        if (storedValue == null) {
            return new HashSet<>();
        }
        if (!(storedValue instanceof Set<?> storedSet)) {
            throw new IllegalStateException("Value is not a set");
        }

        Set<String> copy = new HashSet<>();
        for (Object member : storedSet) {
            if (!(member instanceof String stringMember)) {
                throw new IllegalStateException("Set contains a non-string value");
            }
            copy.add(stringMember);
        }
        return copy;
    }
}
