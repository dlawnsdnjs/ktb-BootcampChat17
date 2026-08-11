package com.ktb.chatapp.websocket.socketio;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MessageDeduplicatorTest {

    @Test
    void claimsOnceAndReturnsTheStoredMessageIdForLaterDuplicates() {
        MessageDeduplicator deduplicator = new MessageDeduplicator(new LocalChatDataStore());

        MessageDeduplicator.Claim first = deduplicator.claim("user-1", "client-1");
        assertTrue(first.accepted());

        MessageDeduplicator.Claim inFlight = deduplicator.claim("user-1", "client-1");
        assertFalse(inFlight.accepted());
        assertNull(inFlight.messageId());

        deduplicator.complete(first, "message-1");
        MessageDeduplicator.Claim completed = deduplicator.claim("user-1", "client-1");
        assertFalse(completed.accepted());
        assertEquals("message-1", completed.messageId());
    }

    @Test
    void releasesFailedClaimsForRetry() {
        MessageDeduplicator deduplicator = new MessageDeduplicator(new LocalChatDataStore());
        MessageDeduplicator.Claim first = deduplicator.claim("user-1", "client-1");

        deduplicator.release(first);

        assertTrue(deduplicator.claim("user-1", "client-1").accepted());
    }

    @Test
    void rejectedClaimIsIgnoredWithoutAStoredMessageAck() {
        MessageDeduplicator deduplicator = new MessageDeduplicator(new LocalChatDataStore());
        MessageDeduplicator.Claim first = deduplicator.claim("user-1", "client-1");

        deduplicator.reject(first);
        MessageDeduplicator.Claim duplicate = deduplicator.claim("user-1", "client-1");

        assertFalse(duplicate.accepted());
        assertNull(duplicate.messageId());
    }
}
