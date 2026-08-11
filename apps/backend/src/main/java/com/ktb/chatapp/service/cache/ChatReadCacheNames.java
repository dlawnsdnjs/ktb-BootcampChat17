package com.ktb.chatapp.service.cache;

/**
 * Redis cache names used by the append-mostly chat read paths.
 */
public final class ChatReadCacheNames {

    public static final String ROOM_BY_ID = "room-by-id";
    public static final String ROOM_LIST = "room-list";
    public static final String MESSAGE_LATEST = "message-latest";
    public static final String MESSAGE_HISTORY = "message-history";

    private ChatReadCacheNames() {
    }
}
