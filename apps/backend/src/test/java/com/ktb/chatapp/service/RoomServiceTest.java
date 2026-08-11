package com.ktb.chatapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb.chatapp.dto.RoomsResponse;
import com.ktb.chatapp.dto.RoomResponse;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.monitoring.ChatPerformanceMetrics;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock private RoomRepository roomRepository;
    @Mock private UserRepository userRepository;
    @Mock private RecentMessageCounter recentMessageCounter;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Test
    void getAllRooms_usesSortedAndBatchedQueries() {
        User creator = User.builder().id("user-1").name("Creator")
                .email("creator@example.com").build();
        User participant = User.builder().id("user-2").name("Participant")
                .email("participant@example.com").build();
        Room newest = Room.builder().id("room-2").name("Newest").creator("user-1")
                .participantIds(Set.of("user-1", "user-2"))
                .createdAt(LocalDateTime.now()).build();
        Room oldest = Room.builder().id("room-1").name("Oldest").creator("user-1")
                .participantIds(Set.of("user-1"))
                .createdAt(LocalDateTime.now().minusHours(1)).build();

        when(roomRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(newest, oldest));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(creator, participant));
        when(recentMessageCounter.countRecentMessages(anyCollection()))
                .thenReturn(Map.of("room-2", 7, "room-1", 2));
        RoomService service = new RoomService(roomRepository, userRepository,
                recentMessageCounter, passwordEncoder, eventPublisher,
                new ChatPerformanceMetrics(new SimpleMeterRegistry()));

        RoomsResponse response = service.getAllRooms("creator@example.com");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).extracting("id")
                .containsExactly("room-2", "room-1");
        assertThat(response.getData()).extracting("recentMessageCount")
                .containsExactly(7, 2);
        assertThat(response.getData().getFirst().getParticipants()).hasSize(2);
        verify(userRepository).findAllById(anyCollection());
        verify(userRepository, never()).findById(org.mockito.ArgumentMatchers.anyString());
        verify(recentMessageCounter).countRecentMessages(anyCollection());
        verify(recentMessageCounter, never())
                .countRecentMessages(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void joinRoom_addsParticipantWithAtomicRepositoryUpdate() {
        Room room = Room.builder().id("room-1").name("Room").creator("creator")
                .participantIds(Set.of("creator")).build();
        Room updated = Room.builder().id("room-1").name("Room").creator("creator")
                .participantIds(Set.of("creator", "user-1")).build();
        User user = User.builder().id("user-1").email("user@example.com").name("User").build();

        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(roomRepository.addParticipantAndReturn("room-1", "user-1"))
                .thenReturn(Optional.of(updated));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(user));
        when(recentMessageCounter.countRecentMessages("room-1")).thenReturn(0);

        RoomService service = new RoomService(roomRepository, userRepository,
                recentMessageCounter, passwordEncoder, eventPublisher,
                new ChatPerformanceMetrics(new SimpleMeterRegistry()));

        RoomResponse result = service.joinRoom("room-1", null, "user@example.com");

        assertThat(result.getParticipants()).extracting("id").containsExactly("user-1");
        verify(roomRepository).addParticipantAndReturn("room-1", "user-1");
        verify(roomRepository, never()).save(org.mockito.ArgumentMatchers.any(Room.class));
        verify(userRepository).findAllById(anyCollection());
        verify(userRepository, never()).findById(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void joinRoom_isIdempotentForExistingParticipant() {
        Room room = Room.builder().id("room-1").name("Room").creator("creator")
                .participantIds(Set.of("creator", "user-1")).build();
        User user = User.builder().id("user-1").email("user@example.com").name("User").build();

        when(roomRepository.findById("room-1")).thenReturn(Optional.of(room));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(user));
        when(recentMessageCounter.countRecentMessages("room-1")).thenReturn(0);

        RoomService service = new RoomService(roomRepository, userRepository,
                recentMessageCounter, passwordEncoder, eventPublisher,
                new ChatPerformanceMetrics(new SimpleMeterRegistry()));

        RoomResponse result = service.joinRoom("room-1", null, "user@example.com");

        assertThat(result.getId()).isEqualTo("room-1");
        verify(roomRepository, never()).addParticipantAndReturn(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
