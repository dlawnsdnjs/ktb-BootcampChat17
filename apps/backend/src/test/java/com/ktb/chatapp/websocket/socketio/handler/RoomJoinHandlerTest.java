package com.ktb.chatapp.websocket.socketio.handler;

import com.corundumstudio.socketio.BroadcastOperations;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.ktb.chatapp.dto.FetchMessagesRequest;
import com.ktb.chatapp.dto.FetchMessagesResponse;
import com.ktb.chatapp.dto.JoinRoomSuccessResponse;
import com.ktb.chatapp.dto.MessageResponse;
import com.ktb.chatapp.dto.UserResponse;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.MessageType;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.MessageRepository;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.websocket.socketio.SocketUser;
import com.ktb.chatapp.websocket.socketio.UserRooms;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.JOIN_ROOM_ERROR;
import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.JOIN_ROOM_SUCCESS;
import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.MESSAGE;
import static com.ktb.chatapp.websocket.socketio.SocketIOEvents.PARTICIPANTS_UPDATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomJoinHandlerTest {

    @Mock private SocketIOServer socketIOServer;
    @Mock private MessageRepository messageRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserRooms userRooms;
    @Mock private MessageLoader messageLoader;
    @Mock private MessageResponseMapper messageResponseMapper;
    @Mock private RoomLeaveHandler roomLeaveHandler;
    @Mock private SocketIOClient client;
    @Mock private BroadcastOperations roomOperations;

    private RoomJoinHandler handler;
    private SocketUser socketUser;
    private User joiningUser;
    private MessageResponse joinMessageResponse;
    private FetchMessagesResponse loadResponse;

    @BeforeEach
    void setUp() {
        handler = new RoomJoinHandler(
                socketIOServer,
                messageRepository,
                roomRepository,
                userRepository,
                userRooms,
                messageLoader,
                messageResponseMapper,
                roomLeaveHandler);

        socketUser = new SocketUser("user-1", "tester", "session-1", "socket-1");
        joiningUser = user("user-1", "tester");
        joinMessageResponse = MessageResponse.builder()
                .id("message-1")
                .roomId("room-1")
                .content("tester님이 입장하였습니다.")
                .type(MessageType.system)
                .timestamp(1L)
                .build();
        loadResponse = FetchMessagesResponse.builder()
                .messages(List.of())
                .hasMore(false)
                .build();
    }

    @Test
    void handleJoinRoom_rejectsUnauthorizedClient() {
        when(client.get("user")).thenReturn(null);

        handler.handleJoinRoom(client, "room-1");

        verify(client).sendEvent(eq(JOIN_ROOM_ERROR), any());
    }

    @Test
    void handleJoinRoom_existingParticipantSkipsAddAndUsesSingleRoomAndBatchUserReads() {
        User otherUser = user("user-2", "other");
        Room room = room(Set.of("user-1", "user-2"));
        stubSuccessfulJoin(room, List.of(joiningUser, otherUser));

        handler.handleJoinRoom(client, "room-1");

        verify(roomRepository, never()).addParticipantAndReturn(any(), any());
        verify(roomRepository, times(1)).findById("room-1");
        verify(userRepository, times(1)).findAllById(any());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void handleJoinRoom_newParticipantAddsOnceAndIncludesParticipantInSuccessResponse() {
        User otherUser = user("user-2", "other");
        Room room = room(Set.of("user-2"));
        stubSuccessfulJoin(room, List.of(joiningUser, otherUser));

        handler.handleJoinRoom(client, "room-1");

        verify(roomRepository, times(1)).addParticipantAndReturn("room-1", "user-1");
        verify(roomRepository, times(1)).findById("room-1");
        verify(userRepository, times(1)).findAllById(any());

        JoinRoomSuccessResponse response = captureSuccessResponse();
        assertEquals("room-1", response.getRoomId());
        assertTrue(response.getParticipants().stream()
                .map(UserResponse::getId)
                .anyMatch("user-1"::equals));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<String>> idsCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(userRepository).findAllById(idsCaptor.capture());
        Set<String> requestedIds = new HashSet<>();
        idsCaptor.getValue().forEach(requestedIds::add);
        assertEquals(Set.of("user-1", "user-2"), requestedIds);
    }

    @Test
    void handleJoinRoom_handlesNullParticipantIds() {
        Room room = room(null);
        stubSuccessfulJoin(room, List.of(joiningUser));

        handler.handleJoinRoom(client, "room-1");

        verify(roomRepository).addParticipantAndReturn("room-1", "user-1");
        JoinRoomSuccessResponse response = captureSuccessResponse();
        assertEquals(List.of("user-1"), response.getParticipants().stream()
                .map(UserResponse::getId)
                .toList());
    }

    @Test
    void handleJoinRoom_rejectsMissingRoomWithExistingError() {
        when(client.get("user")).thenReturn(socketUser);
        when(roomRepository.findById("room-1")).thenReturn(Optional.empty());

        handler.handleJoinRoom(client, "room-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> errorCaptor = ArgumentCaptor.forClass(Map.class);
        verify(client).sendEvent(eq(JOIN_ROOM_ERROR), errorCaptor.capture());
        assertEquals("채팅방을 찾을 수 없습니다.", errorCaptor.getValue().get("message"));
        verify(roomRepository, times(1)).findById("room-1");
        verify(roomRepository, never()).addParticipantAndReturn(any(), any());
        verify(userRepository, never()).findAllById(any());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void handleJoinRoom_preservesSuccessPayloadAndMessageThenParticipantsBroadcasts() {
        User otherUser = user("user-2", "other");
        Room room = room(Set.of("user-1", "missing-user", "user-2"));
        stubSuccessfulJoin(room, List.of(joiningUser, otherUser));

        handler.handleJoinRoom(client, "room-1");

        verify(client).joinRoom("room-1");
        verify(userRooms).add("user-1", "room-1");
        JoinRoomSuccessResponse response = captureSuccessResponse();
        assertEquals(List.of(), response.getMessages());
        assertFalse(response.isHasMore());
        assertEquals(List.of(), response.getActiveStreams());
        assertEquals(Set.of("user-1", "user-2"), response.getParticipants().stream()
                .map(UserResponse::getId)
                .collect(java.util.stream.Collectors.toSet()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserResponse>> participantsCaptor = ArgumentCaptor.forClass(List.class);
        InOrder broadcastOrder = inOrder(roomOperations);
        broadcastOrder.verify(roomOperations).sendEvent(MESSAGE, joinMessageResponse);
        broadcastOrder.verify(roomOperations).sendEvent(eq(PARTICIPANTS_UPDATE), participantsCaptor.capture());
        assertEquals(response.getParticipants(), participantsCaptor.getValue());
    }

    @Test
    void handleJoinRoom_reconnectLoadsStateWithoutDuplicateJoinMessage() {
        User otherUser = user("user-2", "other");
        Room room = room(Set.of("user-1", "user-2"));
        when(client.get("user")).thenReturn(socketUser);
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(userRooms.isInRoom("user-1", "room-1")).thenReturn(true);
        when(messageLoader.loadMessages(any(FetchMessagesRequest.class), eq("user-1")))
                .thenReturn(loadResponse);
        when(userRepository.findAllById(any())).thenReturn(List.of(joiningUser, otherUser));
        when(socketIOServer.getRoomOperations("room-1")).thenReturn(roomOperations);

        handler.handleJoinRoom(client, "room-1");

        verify(client).joinRoom("room-1");
        verify(userRooms).add("user-1", "room-1");
        verify(messageRepository, never()).save(any(Message.class));
        verify(roomRepository, never()).addParticipantAndReturn(any(), any());
        verify(userRepository, never()).findById(any());
        assertEquals(2, captureSuccessResponse().getParticipants().size());
    }

    private void stubSuccessfulJoin(Room room, List<User> participantUsers) {
        when(client.get("user")).thenReturn(socketUser);
        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(userRooms.isInRoom("user-1", "room-1")).thenReturn(false);
        if (room.getParticipantIds() == null || !room.getParticipantIds().contains("user-1")) {
            Set<String> updatedIds = new HashSet<>();
            if (room.getParticipantIds() != null) {
                updatedIds.addAll(room.getParticipantIds());
            }
            updatedIds.add("user-1");
            when(roomRepository.addParticipantAndReturn("room-1", "user-1"))
                    .thenReturn(Optional.of(room(updatedIds)));
        }
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            message.setId("message-1");
            message.setTimestamp(LocalDateTime.now());
            return message;
        });
        when(messageLoader.loadMessages(any(FetchMessagesRequest.class), eq("user-1")))
                .thenReturn(loadResponse);
        when(userRepository.findAllById(any())).thenReturn(participantUsers);
        when(messageResponseMapper.mapToMessageResponse(any(Message.class), eq(null)))
                .thenReturn(joinMessageResponse);
        when(socketIOServer.getRoomOperations("room-1")).thenReturn(roomOperations);
    }

    private JoinRoomSuccessResponse captureSuccessResponse() {
        ArgumentCaptor<Object> responseCaptor = ArgumentCaptor.forClass(Object.class);
        verify(client).sendEvent(eq(JOIN_ROOM_SUCCESS), responseCaptor.capture());
        return assertInstanceOf(JoinRoomSuccessResponse.class, responseCaptor.getValue());
    }

    private Room room(Set<String> participantIds) {
        return Room.builder()
                .id("room-1")
                .name("room")
                .participantIds(participantIds)
                .build();
    }

    private User user(String id, String name) {
        return User.builder()
                .id(id)
                .name(name)
                .email(name + "@example.com")
                .build();
    }
}
