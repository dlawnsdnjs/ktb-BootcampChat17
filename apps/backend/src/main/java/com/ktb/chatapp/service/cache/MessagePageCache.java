package com.ktb.chatapp.service.cache;

import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.repository.MessageRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Read-through cache boundary for message history pages.
 */
@Component
@RequiredArgsConstructor
public class MessagePageCache {

    private final MessageRepository messageRepository;

    @Cacheable(
            cacheNames = ChatReadCacheNames.MESSAGE_LATEST,
            key = "@chatReadCacheKeys.latestMessagePage(#roomId)",
            condition = "#limit == 30",
            sync = true)
    public MessagePageSnapshot loadLatest(String roomId, int limit) {
        return load(roomId, limit, LocalDateTime.now());
    }

    @Cacheable(
            cacheNames = ChatReadCacheNames.MESSAGE_HISTORY,
            key = "#roomId + ':' + #limit + ':' + #before",
            sync = true)
    public MessagePageSnapshot loadBefore(String roomId, int limit, LocalDateTime before) {
        return load(roomId, limit, before);
    }

    private MessagePageSnapshot load(String roomId, int limit, LocalDateTime before) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by("timestamp").descending());
        Slice<Message> messagePage = messageRepository
                .findByRoomIdAndTimestampBefore(roomId, before, pageable);

        List<Message> sortedMessages = new ArrayList<>(messagePage.getContent());
        Collections.reverse(sortedMessages);
        return MessagePageSnapshot.from(sortedMessages, messagePage.hasNext());
    }
}
