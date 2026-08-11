package com.ktb.chatapp.service.cache;

import com.ktb.chatapp.model.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.stereotype.Component;

/**
 * Advances the latest-page generation after MongoDB has stored a message.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "chat.read-cache.enabled", havingValue = "true")
public class MessageCacheInvalidationListener extends AbstractMongoEventListener<Message> {

    private final ChatReadCacheKeys cacheKeys;

    @Override
    public void onAfterSave(AfterSaveEvent<Message> event) {
        cacheKeys.advanceLatestMessageVersion(event.getSource().getRoomId());
    }
}
