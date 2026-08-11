package com.ktb.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.OnEvent;
import com.ktb.chatapp.dto.ChatMessageRequest;
import com.ktb.chatapp.dto.FileResponse;
import com.ktb.chatapp.dto.MessageContent;
import com.ktb.chatapp.dto.MessageResponse;
import com.ktb.chatapp.dto.UserResponse;
import com.ktb.chatapp.model.*;
import com.ktb.chatapp.monitoring.ChatPerformanceMetrics;
import com.ktb.chatapp.repository.FileRepository;
import com.ktb.chatapp.repository.MessageRepository;
import com.ktb.chatapp.util.BannedWordChecker;
import com.ktb.chatapp.websocket.socketio.ai.AiService;
import com.ktb.chatapp.service.RoomActivityNotifier;
import com.ktb.chatapp.service.SessionValidationResult;
import com.ktb.chatapp.service.RateLimitService;
import com.ktb.chatapp.service.RateLimitCheckResult;
import com.ktb.chatapp.websocket.socketio.SocketUser;
import com.ktb.chatapp.websocket.socketio.SocketSessionValidator;
import com.ktb.chatapp.websocket.socketio.UserRooms;
import com.ktb.chatapp.websocket.socketio.MessageDeduplicator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.*;

@Slf4j
@Component
@ConditionalOnProperty(name = "socketio.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ChatMessageHandler {
    private final SocketIOServer socketIOServer;
    private final MessageRepository messageRepository;
    private final FileRepository fileRepository;
    private final AiService aiService;
    private final SocketSessionValidator sessionValidator;
    private final RoomActivityNotifier roomActivityNotifier;
    private final BannedWordChecker bannedWordChecker;
    private final RateLimitService rateLimitService;
    private final MeterRegistry meterRegistry;
    private final ChatPerformanceMetrics performanceMetrics;
    private final UserRooms userRooms;
    private final MessageDeduplicator messageDeduplicator;
    
    @OnEvent(CHAT_MESSAGE)
    public void handleChatMessage(SocketIOClient client, ChatMessageRequest data) {
        Timer.Sample timerSample = Timer.start(meterRegistry);
        MessageDeduplicator.Claim deduplicationClaim = null;

        if (data == null) {
            recordError("null_data");
            client.sendEvent(ERROR, Map.of(
                    "code", "MESSAGE_ERROR",
                    "message", "메시지 데이터가 없습니다."
            ));
            timerSample.stop(createTimer("error", "null_data"));
            return;
        }

        var socketUser = (SocketUser) client.get("user");

        if (socketUser == null) {
            recordError("session_null");
            client.sendEvent(ERROR, Map.of(
                    "code", "SESSION_EXPIRED",
                    "message", "세션이 만료되었습니다. 다시 로그인해주세요."
            ));
            timerSample.stop(createTimer("error", "session_null"));
            return;
        }

        String roomId = data.getRoom();
        deduplicationClaim = messageDeduplicator.claim(
                socketUser.id(), data.getClientMessageId());
        if (!deduplicationClaim.accepted()) {
            if (deduplicationClaim.messageId() != null) {
                client.sendEvent(MESSAGE_ACK, Map.of(
                        "clientMessageId", data.getClientMessageId(),
                        "messageId", deduplicationClaim.messageId(),
                        "roomId", roomId));
            }
            timerSample.stop(createTimer("duplicate", data.getMessageType()));
            return;
        }

        SessionValidationResult validation = performanceMetrics.recordMessagePhase(
                "session_validation",
                () -> sessionValidator.validate(socketUser.id(), socketUser.authSessionId()));
        if (!validation.isValid()) {
            messageDeduplicator.reject(deduplicationClaim);
            deduplicationClaim = null;
            recordError("session_expired");
            client.sendEvent(ERROR, Map.of(
                    "code", "SESSION_EXPIRED",
                    "message", "세션이 만료되었습니다. 다시 로그인해주세요."
            ));
            timerSample.stop(createTimer("error", "session_expired"));
            return;
        }

        // Rate limit check
        RateLimitCheckResult rateLimitResult = performanceMetrics.recordMessagePhase(
                "rate_limit",
                () -> rateLimitService.checkRateLimit(
                        socketUser.id(), 10000, Duration.ofMinutes(1)));
        if (!rateLimitResult.allowed()) {
            messageDeduplicator.reject(deduplicationClaim);
            deduplicationClaim = null;
            recordError("rate_limit_exceeded");
            Counter.builder("socketio.messages.rate_limit")
                    .description("Socket.IO rate limit exceeded count")
                    .register(meterRegistry)
                    .increment();
            client.sendEvent(ERROR, Map.of(
                    "code", "RATE_LIMIT_EXCEEDED",
                    "message", "메시지 전송 횟수 제한을 초과했습니다. 잠시 후 다시 시도해주세요.",
                    "retryAfter", rateLimitResult.retryAfterSeconds()
            ));
            log.warn("Rate limit exceeded for user: {}, retryAfter: {}s",
                    socketUser.id(), rateLimitResult.retryAfterSeconds());
            timerSample.stop(createTimer("error", "rate_limit"));
            return;
        }
        
        try {
            UserResponse sender = client.get("userResponse");
            if (sender == null) {
                messageDeduplicator.reject(deduplicationClaim);
                deduplicationClaim = null;
                recordError("user_not_found");
                client.sendEvent(ERROR, Map.of(
                    "code", "MESSAGE_ERROR",
                    "message", "User not found"
                ));
                timerSample.stop(createTimer("error", "user_not_found"));
                return;
            }

            boolean hasRoomAccess = performanceMetrics.recordMessagePhase(
                    "room_membership", () -> userRooms.isInRoom(socketUser.id(), roomId));
            if (!hasRoomAccess) {
                messageDeduplicator.reject(deduplicationClaim);
                deduplicationClaim = null;
                recordError("room_access_denied");
                client.sendEvent(ERROR, Map.of(
                    "code", "MESSAGE_ERROR",
                    "message", "채팅방 접근 권한이 없습니다."
                ));
                timerSample.stop(createTimer("error", "room_access_denied"));
                return;
            }

            MessageContent messageContent = data.getParsedContent();

            log.debug("Message received - type: {}, room: {}, userId: {}, hasFileData: {}",
                data.getMessageType(), roomId, socketUser.id(), data.hasFileData());

            boolean containsBannedWord = performanceMetrics.recordMessagePhase(
                    "banned_word",
                    () -> bannedWordChecker.containsBannedWord(messageContent.getTrimmedContent()));
            if (containsBannedWord) {
                messageDeduplicator.reject(deduplicationClaim);
                deduplicationClaim = null;
                recordError("banned_word");
                client.sendEvent(ERROR, Map.of(
                        "code", "MESSAGE_REJECTED",
                        "message", "금칙어가 포함된 메시지는 전송할 수 없습니다."
                ));
                timerSample.stop(createTimer("error", "banned_word"));
                return;
            }

            String messageType = data.getMessageType();
            Message message = switch (messageType) {
                case "file" -> handleFileMessage(roomId, socketUser.id(), messageContent, data.getFileData());
                case "text" -> handleTextMessage(roomId, socketUser.id(), messageContent);
                default -> throw new IllegalArgumentException("Unsupported message type: " + messageType);
            };

            if (message == null) {
                messageDeduplicator.reject(deduplicationClaim);
                deduplicationClaim = null;
                log.warn("Empty message - ignoring. room: {}, userId: {}, messageType: {}", roomId, socketUser.id(), messageType);
                timerSample.stop(createTimer("ignored", messageType));
                return;
            }

            Message savedMessage = performanceMetrics.recordMessagePhase(
                    "message_save", () -> messageRepository.save(message));
            messageDeduplicator.complete(deduplicationClaim, savedMessage.getId());
            deduplicationClaim = null;
            MessageResponse messageResponse = createMessageResponse(savedMessage, sender);

            performanceMetrics.recordMessagePhase("broadcast", () -> {
                socketIOServer.getRoomOperations(roomId)
                        .sendEvent(MESSAGE, messageResponse);
            });

            if (data.getClientMessageId() != null && !data.getClientMessageId().isBlank()) {
                client.sendEvent(MESSAGE_ACK, Map.of(
                        "clientMessageId", data.getClientMessageId(),
                        "messageId", savedMessage.getId(),
                        "roomId", roomId,
                        "timestamp", savedMessage.toTimestampMillis()));
            }

            performanceMetrics.recordMessagePhase(
                    "room_activity", () -> roomActivityNotifier.notifyMessageStored(roomId));

            // AI 멘션 처리
            aiService.handleAIMentions(roomId, socketUser.id(), messageContent);

            // Record success metrics
            recordMessageSuccess(messageType);
            timerSample.stop(createTimer("success", messageType));

            log.debug("Message processed - messageId: {}, type: {}, room: {}",
                savedMessage.getId(), savedMessage.getType(), roomId);

        } catch (Exception e) {
            if (deduplicationClaim != null) {
                messageDeduplicator.release(deduplicationClaim);
            }
            recordError("exception");
            log.error("Message handling error", e);
            client.sendEvent(ERROR, Map.of(
                "code", "MESSAGE_ERROR",
                "message", e.getMessage() != null ? e.getMessage() : "메시지 전송 중 오류가 발생했습니다."
            ));
            timerSample.stop(createTimer("error", "exception"));
        }
    }

    private Message handleFileMessage(String roomId, String userId, MessageContent messageContent, Map<String, Object> fileData) {
        if (fileData == null || fileData.get("_id") == null) {
            throw new IllegalArgumentException("파일 데이터가 올바르지 않습니다.");
        }

        String fileId = (String) fileData.get("_id");
        File file = fileRepository.findById(fileId).orElse(null);

        if (file == null || !file.getUser().equals(userId)) {
            throw new IllegalStateException("파일을 찾을 수 없거나 접근 권한이 없습니다.");
        }

        Message message = new Message();
        message.setRoomId(roomId);
        message.setSenderId(userId);
        message.setType(MessageType.file);
        message.setFileId(fileId);
        message.setContent(messageContent.getTrimmedContent());
        message.setTimestamp(LocalDateTime.now());
        message.setMentions(messageContent.aiMentions());
        
        // 메타데이터는 Map<String, Object>
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fileType", file.getMimetype());
        metadata.put("fileSize", file.getSize());
        metadata.put("originalName", file.getOriginalname());
        message.setMetadata(metadata);

        return message;
    }

    private Message handleTextMessage(String roomId, String userId, MessageContent messageContent) {
        if (messageContent.isEmpty()) {
            return null; // 빈 메시지는 무시
        }

        Message message = new Message();
        message.setRoomId(roomId);
        message.setSenderId(userId);
        message.setContent(messageContent.getTrimmedContent());
        message.setType(MessageType.text);
        message.setTimestamp(LocalDateTime.now());
        message.setMentions(messageContent.aiMentions());

        return message;
    }

    private MessageResponse createMessageResponse(Message message, UserResponse sender) {
        var messageResponse = new MessageResponse();
        messageResponse.setId(message.getId());
        messageResponse.setRoomId(message.getRoomId());
        messageResponse.setContent(message.getContent());
        messageResponse.setType(message.getType());
        messageResponse.setTimestamp(message.toTimestampMillis());
        messageResponse.setReactions(message.getReactions() != null ? message.getReactions() : Collections.emptyMap());
        messageResponse.setSender(sender);
        messageResponse.setMetadata(message.getMetadata());

        if (message.getFileId() != null) {
            fileRepository.findById(message.getFileId())
                    .ifPresent(file -> messageResponse.setFile(FileResponse.from(file)));
        }

        return messageResponse;
    }

    // Metrics helper methods
    private Timer createTimer(String status, String messageType) {
        return Timer.builder("socketio.messages.processing.time")
                .description("Socket.IO message processing time")
                .tag("status", status)
                .tag("message_type", messageType)
                .register(meterRegistry);
    }

    private void recordMessageSuccess(String messageType) {
        Counter.builder("socketio.messages.total")
                .description("Total Socket.IO messages processed")
                .tag("status", "success")
                .tag("message_type", messageType)
                .register(meterRegistry)
                .increment();
    }

    private void recordError(String errorType) {
        Counter.builder("socketio.messages.errors")
                .description("Socket.IO message processing errors")
                .tag("error_type", errorType)
                .register(meterRegistry)
                .increment();
    }
}
