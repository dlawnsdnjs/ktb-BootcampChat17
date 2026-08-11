package com.ktb.chatapp.websocket.socketio;

import java.util.Optional;
import java.util.Set;

/**
 * Data store interface for chat-related data storage.
 * Provides key-value storage operations for chat user and room data.
 */
public interface ChatDataStore {
    
    /**
     * Retrieve a value by key
     *
     * @param key the storage key
     * @param type the type of value to retrieve
     * @param <T> the type parameter
     * @return Optional containing the value if found, empty otherwise
     */
    <T> Optional<T> get(String key, Class<T> type);
    
    /**
     * Store a value with the given key
     *
     * @param key the storage key
     * @param value the value to store
     */
    void set(String key, Object value);
    
    /**
     * Delete a value by key
     *
     * @param key the storage key
     */
    void delete(String key);

    /**
     * Store a leased value and atomically register it in a size tracker.
     */
    void setTracked(String key, Object value, String trackerKey);

    /**
     * Atomically replace a leased value, register it in a size tracker and return
     * the value that was stored immediately before the replacement.
     */
    <T> Optional<T> getAndSetTracked(
            String key,
            Object value,
            Class<T> type,
            String trackerKey);

    /**
     * Delete a leased value and atomically remove it from its size tracker.
     */
    void deleteTracked(String key, String trackerKey);

    /**
     * Delete a leased value only when it still matches the expected value, and
     * atomically remove it from its size tracker.
     *
     * <p>This prevents a stale socket disconnect from deleting a newer connection.
     */
    boolean deleteIfEqualsTracked(
            String key,
            Object expectedValue,
            String trackerKey);

    /**
     * Retrieve the string members stored under a set key.
     */
    Set<String> getSet(String key);

    /**
     * Atomically add a string member to a set key.
     */
    void addToSet(String key, String value);

    /**
     * Atomically remove a string member from a set key.
     */
    void removeFromSet(String key, String value);

    /**
     * Check whether a set key contains a string member.
     */
    boolean containsInSet(String key, String value);

    /**
     * Renew the lease for a key if it exists.
     */
    boolean touch(String key);

    /**
     * Renew a value and its size-tracker lease only when the value still matches.
     */
    boolean touchIfEqualsTracked(
            String key,
            Object expectedValue,
            String trackerKey);

    /**
     * Return the number of live values registered in a size tracker.
     */
    int trackedSize(String trackerKey);
}
