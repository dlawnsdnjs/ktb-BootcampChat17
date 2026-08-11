package com.ktb.chatapp.websocket.socketio.handler;

import com.ktb.chatapp.dto.FetchMessagesRequest;
import com.ktb.chatapp.dto.FetchMessagesResponse;
import com.ktb.chatapp.dto.MessageResponse;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.MessageRepository;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.service.MessageReadStatusService;
import com.ktb.chatapp.service.cache.MessagePageCache;
import com.ktb.chatapp.service.cache.MessagePageSnapshot;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static java.util.Collections.emptyList;

@Slf4j
@Component
public class MessageLoader {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MessageResponseMapper messageResponseMapper;
    private final MessageReadStatusService messageReadStatusService;
    private final MessagePageCache messagePageCache;

    private static final int BATCH_SIZE = 30;

    @Autowired
    public MessageLoader(
            MessageRepository messageRepository,
            UserRepository userRepository,
            MessageResponseMapper messageResponseMapper,
            MessageReadStatusService messageReadStatusService,
            MessagePageCache messagePageCache) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.messageResponseMapper = messageResponseMapper;
        this.messageReadStatusService = messageReadStatusService;
        this.messagePageCache = messagePageCache;
    }

    /**
     * Keeps direct unit/integration construction backward compatible. Caching is
     * applied by the Spring-managed MessagePageCache proxy in the running app.
     */
    public MessageLoader(
            MessageRepository messageRepository,
            UserRepository userRepository,
            MessageResponseMapper messageResponseMapper,
            MessageReadStatusService messageReadStatusService) {
        this(
                messageRepository,
                userRepository,
                messageResponseMapper,
                messageReadStatusService,
                new MessagePageCache(messageRepository));
    }

    /**
     * 메시지 로드
     */
    public FetchMessagesResponse loadMessages(FetchMessagesRequest data, String userId) {
        try {
            int limit = data.limit(BATCH_SIZE);
            MessagePageSnapshot page = data.before() != null && data.before() > 0
                    ? messagePageCache.loadBefore(
                            data.roomId(), limit, data.before(LocalDateTime.now()))
                    : messagePageCache.loadLatest(data.roomId(), limit);
            return createResponse(data.roomId(), page, userId);
        } catch (Exception e) {
            log.error("Error loading initial messages for room {}", data.roomId(), e);
            return FetchMessagesResponse.builder()
                    .messages(emptyList())
                    .hasMore(false)
                    .build();
        }
    }

    private FetchMessagesResponse createResponse(
            String roomId,
            MessagePageSnapshot page,
            String userId) {
        List<Message> sortedMessages = page.toMessages();
        overlayMutableState(sortedMessages);

        var messageIds = sortedMessages.stream().map(Message::getId).toList();
        messageReadStatusService.updateReadStatus(messageIds, userId);
        
        // 메시지 응답 생성
        var senderIds = sortedMessages.stream()
                .map(Message::getSenderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, User> usersById = userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<MessageResponse> messageResponses =
                messageResponseMapper.mapToMessageResponses(sortedMessages, usersById);

        log.debug("Messages loaded - roomId: {}, count: {}, hasMore: {}",
                roomId, messageResponses.size(), page.hasMore());

        return FetchMessagesResponse.builder()
                .messages(messageResponses)
                .hasMore(page.hasMore())
                .build();
    }

    private void overlayMutableState(List<Message> messages) {
        if (messages.isEmpty()) {
            return;
        }

        List<String> messageIds = messages.stream().map(Message::getId).toList();
        List<Message> mutableStates = messageRepository.findMutableStateByIdIn(messageIds);
        if (mutableStates == null || mutableStates.isEmpty()) {
            return;
        }

        Map<String, Message> mutableStatesById = mutableStates.stream()
                .filter(message -> message.getId() != null)
                .collect(Collectors.toMap(Message::getId, Function.identity()));

        for (Message message : messages) {
            Message mutableState = mutableStatesById.get(message.getId());
            if (mutableState == null) {
                continue;
            }
            message.setReaders(mutableState.getReaders() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(mutableState.getReaders()));
            message.setReactions(copyReactions(mutableState.getReactions()));
        }
    }

    private Map<String, Set<String>> copyReactions(Map<String, Set<String>> reactions) {
        if (reactions == null || reactions.isEmpty()) {
            return new HashMap<>();
        }
        return reactions.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new HashSet<>(entry.getValue())));
    }
}
