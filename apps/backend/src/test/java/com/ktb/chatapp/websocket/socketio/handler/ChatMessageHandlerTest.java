package com.ktb.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.BroadcastOperations;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.ktb.chatapp.dto.ChatMessageRequest;
import com.ktb.chatapp.dto.MessageResponse;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.MessageType;
import com.ktb.chatapp.monitoring.ChatPerformanceMetrics;
import com.ktb.chatapp.repository.FileRepository;
import com.ktb.chatapp.repository.MessageRepository;
import com.ktb.chatapp.service.RateLimitCheckResult;
import com.ktb.chatapp.service.RateLimitService;
import com.ktb.chatapp.service.RoomActivityNotifier;
import com.ktb.chatapp.service.SessionValidationResult;
import com.ktb.chatapp.util.BannedWordChecker;
import com.ktb.chatapp.websocket.socketio.SocketUser;
import com.ktb.chatapp.websocket.socketio.SocketSessionValidator;
import com.ktb.chatapp.websocket.socketio.UserRooms;
import com.ktb.chatapp.websocket.socketio.MessageDeduplicator;
import com.ktb.chatapp.websocket.socketio.ai.AiService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.ERROR;
import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.MESSAGE;
import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.MESSAGE_ACK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageHandlerTest {

    @Mock private SocketIOServer socketIOServer;
    @Mock private MessageRepository messageRepository;
    @Mock private FileRepository fileRepository;
    @Mock private AiService aiService;
    @Mock private SocketSessionValidator sessionValidator;
    @Mock private RoomActivityNotifier roomActivityNotifier;
    @Mock private BannedWordChecker bannedWordChecker;
    @Mock private RateLimitService rateLimitService;
    @Mock private UserRooms userRooms;
    @Mock private MessageDeduplicator messageDeduplicator;
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private ChatMessageHandler handler;

    @BeforeEach
    void setUp() {
        handler =
                new ChatMessageHandler(
                        socketIOServer,
                        messageRepository,
                        fileRepository,
                        aiService,
                        sessionValidator,
                        roomActivityNotifier,
                        bannedWordChecker,
                        rateLimitService,
                        meterRegistry,
                        new ChatPerformanceMetrics(meterRegistry),
                        userRooms,
                        messageDeduplicator);
        lenient().when(messageDeduplicator.claim(anyString(), nullable(String.class)))
                .thenReturn(new MessageDeduplicator.Claim(false, true, null, null));
    }

    @Test
    void handleChatMessage_blocksMessagesContainingBannedWords() {
        SocketIOClient client = mock(SocketIOClient.class);
        SocketUser socketUser = new SocketUser("user-1", "tester", "session-1", "socket-1");
        when(client.get("user")).thenReturn(socketUser);

        SessionValidationResult validResult = SessionValidationResult.valid(null);
        when(sessionValidator.validate(socketUser.id(), socketUser.authSessionId()))
                .thenReturn(validResult);

        RateLimitCheckResult allowedResult = RateLimitCheckResult.allowed(10000, 9999, 60, System.currentTimeMillis() / 1000 + 60, 60);
        when(rateLimitService.checkRateLimit(eq(socketUser.id()), anyInt(), any()))
                .thenReturn(allowedResult);

        when(client.get("userResponse")).thenReturn(
                com.ktb.chatapp.dto.UserResponse.builder().id("user-1").name("Tester").build());
        when(userRooms.isInRoom("user-1", "room-1")).thenReturn(true);

        ChatMessageRequest request =
                ChatMessageRequest.builder()
                        .room("room-1")
                        .type("text")
                        .content("bad word")
                        .build();

        when(bannedWordChecker.containsBannedWord("bad word")).thenReturn(true);

        handler.handleChatMessage(client, request);

        ArgumentCaptor<Map<String, String>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(client).sendEvent(eq(ERROR), payloadCaptor.capture());
        Map<String, String> payload = payloadCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("MESSAGE_REJECTED", payload.get("code"));
        verifyNoInteractions(messageRepository);
        verify(socketIOServer, never()).getRoomOperations(any());
    }

    @Test
    void handleChatMessage_echoesSavedMessageToSenderSocket() {
        SocketIOClient client = mock(SocketIOClient.class);
        BroadcastOperations roomOperations = mock(BroadcastOperations.class);
        SocketUser socketUser = new SocketUser("user-1", "tester", "session-1", "socket-1");
        when(client.get("user")).thenReturn(socketUser);

        when(sessionValidator.validate(socketUser.id(), socketUser.authSessionId()))
                .thenReturn(SessionValidationResult.valid(null));
        when(rateLimitService.checkRateLimit(eq(socketUser.id()), anyInt(), any()))
                .thenReturn(RateLimitCheckResult.allowed(10000, 9999, 60, System.currentTimeMillis() / 1000 + 60, 60));

        when(client.get("userResponse")).thenReturn(
                com.ktb.chatapp.dto.UserResponse.builder().id("user-1").name("Tester").build());
        when(userRooms.isInRoom("user-1", "room-1")).thenReturn(true);
        when(bannedWordChecker.containsBannedWord("hello")).thenReturn(false);
        when(socketIOServer.getRoomOperations("room-1")).thenReturn(roomOperations);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId("message-1");
            message.setTimestamp(LocalDateTime.of(2026, 7, 7, 9, 0));
            message.setType(MessageType.text);
            return message;
        });

        ChatMessageRequest request =
                ChatMessageRequest.builder()
                        .room("room-1")
                        .type("text")
                        .content("hello")
                        .clientMessageId("client-1")
                        .build();

        handler.handleChatMessage(client, request);

        ArgumentCaptor<MessageResponse> payloadCaptor = ArgumentCaptor.forClass(MessageResponse.class);
        verify(roomOperations).sendEvent(eq(MESSAGE), payloadCaptor.capture());
        verify(client, never()).sendEvent(eq(MESSAGE), any());
        ArgumentCaptor<Object[]> ackCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(client).sendEvent(eq(MESSAGE_ACK), ackCaptor.capture());
        Map<?, ?> ack = (Map<?, ?>) ackCaptor.getValue()[0];
        org.junit.jupiter.api.Assertions.assertEquals("client-1", ack.get("clientMessageId"));
        org.junit.jupiter.api.Assertions.assertEquals("message-1", ack.get("messageId"));
        verify(roomActivityNotifier).notifyMessageStored("room-1");
        org.junit.jupiter.api.Assertions.assertEquals("message-1", payloadCaptor.getValue().getId());
        org.junit.jupiter.api.Assertions.assertEquals("hello", payloadCaptor.getValue().getContent());
    }

    @Test
    void handleChatMessage_ignoresDuplicateClientMessageIdClaim() {
        SocketIOClient client = mock(SocketIOClient.class);
        BroadcastOperations roomOperations = mock(BroadcastOperations.class);
        SocketUser socketUser = new SocketUser("user-1", "tester", "session-1", "socket-1");
        when(client.get("user")).thenReturn(socketUser);
        when(client.get("userResponse")).thenReturn(
                com.ktb.chatapp.dto.UserResponse.builder().id("user-1").name("Tester").build());
        when(sessionValidator.validate("user-1", "session-1"))
                .thenReturn(SessionValidationResult.valid(null));
        when(rateLimitService.checkRateLimit(eq("user-1"), anyInt(), any()))
                .thenReturn(RateLimitCheckResult.allowed(10000, 9999, 60, 60, 60));
        when(userRooms.isInRoom("user-1", "room-1")).thenReturn(true);
        when(bannedWordChecker.containsBannedWord("hello")).thenReturn(false);
        when(socketIOServer.getRoomOperations("room-1")).thenReturn(roomOperations);
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId("message-1");
            return message;
        });
        when(messageDeduplicator.claim("user-1", "client-1"))
                .thenReturn(
                        new MessageDeduplicator.Claim(true, true, "dedupe-key", null),
                        new MessageDeduplicator.Claim(true, false, null, null));

        ChatMessageRequest request = ChatMessageRequest.builder()
                .room("room-1")
                .type("text")
                .content("hello")
                .clientMessageId("client-1")
                .build();

        handler.handleChatMessage(client, request);
        handler.handleChatMessage(client, request);

        verify(messageRepository).save(any(Message.class));
        verify(roomOperations).sendEvent(eq(MESSAGE), any(MessageResponse.class));
        verify(sessionValidator).validate("user-1", "session-1");
    }

    @Test
    void handleChatMessage_sixtyOneMessagesHaveUniqueCorrelatedAcksAndSavedOrder() {
        SocketIOClient client = mock(SocketIOClient.class);
        BroadcastOperations roomOperations = mock(BroadcastOperations.class);
        SocketUser socketUser = new SocketUser("user-1", "tester", "session-1", "socket-1");
        when(client.get("user")).thenReturn(socketUser);
        when(client.get("userResponse")).thenReturn(
                com.ktb.chatapp.dto.UserResponse.builder().id("user-1").name("Tester").build());
        when(sessionValidator.validate("user-1", "session-1"))
                .thenReturn(SessionValidationResult.valid(null));
        when(rateLimitService.checkRateLimit(eq("user-1"), anyInt(), any()))
                .thenReturn(RateLimitCheckResult.allowed(10000, 9999, 60, 60, 60));
        when(userRooms.isInRoom("user-1", "room-1")).thenReturn(true);
        when(bannedWordChecker.containsBannedWord(any())).thenReturn(false);
        when(socketIOServer.getRoomOperations("room-1")).thenReturn(roomOperations);
        java.util.concurrent.atomic.AtomicInteger sequence = new java.util.concurrent.atomic.AtomicInteger();
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId("message-" + sequence.incrementAndGet());
            return message;
        });

        for (int index = 1; index <= 61; index++) {
            handler.handleChatMessage(client, ChatMessageRequest.builder()
                    .room("room-1")
                    .type("text")
                    .content("payload-" + index)
                    .clientMessageId("client-" + index)
                    .build());
        }

        ArgumentCaptor<Message> savedCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository, times(61)).save(savedCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.stream.IntStream.rangeClosed(1, 61)
                        .mapToObj(index -> "payload-" + index).toList(),
                savedCaptor.getAllValues().stream().map(Message::getContent).toList());

        ArgumentCaptor<Object[]> acksCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(client, times(61)).sendEvent(eq(MESSAGE_ACK), acksCaptor.capture());
        java.util.Set<Object> clientIds = new java.util.HashSet<>();
        java.util.Set<Object> messageIds = new java.util.HashSet<>();
        for (Object[] arguments : acksCaptor.getAllValues()) {
            Map<?, ?> ack = (Map<?, ?>) arguments[0];
            clientIds.add(ack.get("clientMessageId"));
            messageIds.add(ack.get("messageId"));
        }
        org.junit.jupiter.api.Assertions.assertEquals(61, clientIds.size());
        org.junit.jupiter.api.Assertions.assertEquals(61, messageIds.size());
    }
}
