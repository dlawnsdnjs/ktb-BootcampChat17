package com.ktb.chatapp.service.cache;

import com.ktb.chatapp.model.AiType;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.MessageType;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializable snapshot of the immutable part of a message page.
 *
 * <p>Readers and reactions are intentionally excluded because those fields are
 * mutable. MessageLoader overlays their current values using an indexed ID query.</p>
 */
public record MessagePageSnapshot(List<CachedMessage> messages, boolean hasMore)
        implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public MessagePageSnapshot {
        messages = new ArrayList<>(messages);
    }

    public static MessagePageSnapshot from(List<Message> messages, boolean hasMore) {
        return new MessagePageSnapshot(
                messages.stream().map(CachedMessage::from).toList(),
                hasMore);
    }

    public List<Message> toMessages() {
        return messages.stream().map(CachedMessage::toMessage).toList();
    }

    public record CachedMessage(
            String id,
            String roomId,
            String content,
            String senderId,
            MessageType type,
            String fileId,
            AiType aiType,
            List<String> mentions,
            LocalDateTime timestamp,
            Map<String, Object> metadata) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        public CachedMessage {
            mentions = mentions == null ? new ArrayList<>() : new ArrayList<>(mentions);
            metadata = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
        }

        static CachedMessage from(Message message) {
            return new CachedMessage(
                    message.getId(),
                    message.getRoomId(),
                    message.getContent(),
                    message.getSenderId(),
                    message.getType(),
                    message.getFileId(),
                    message.getAiType(),
                    message.getMentions(),
                    message.getTimestamp(),
                    message.getMetadata());
        }

        Message toMessage() {
            return Message.builder()
                    .id(id)
                    .roomId(roomId)
                    .content(content)
                    .senderId(senderId)
                    .type(type)
                    .fileId(fileId)
                    .aiType(aiType)
                    .mentions(new ArrayList<>(mentions))
                    .timestamp(timestamp)
                    .metadata(new HashMap<>(metadata))
                    .build();
        }
    }
}
