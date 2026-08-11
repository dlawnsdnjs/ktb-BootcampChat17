package com.ktb.chatapp.service.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.chatapp.config.MongoTestContainer;
import com.ktb.chatapp.config.RedisTestContainer;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.MessageType;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.dto.CreateRoomRequest;
import com.ktb.chatapp.repository.MessageRepository;
import com.ktb.chatapp.repository.RoomRepository;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.service.RoomService;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import({MongoTestContainer.class, RedisTestContainer.class})
@TestPropertySource(properties = {
        "socketio.enabled=false",
        "chat.read-cache.enabled=true",
        "chat.read-cache.key-prefix=test-chat-read"
})
class ChatReadCacheIntegrationTest {

    @Autowired private RoomRepository roomRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private MessagePageCache messagePageCache;
    @Autowired private RoomService roomService;
    @Autowired private UserRepository userRepository;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearState() {
        mongoTemplate.remove(new Query(), Room.class);
        mongoTemplate.remove(new Query(), Message.class);
        mongoTemplate.remove(new Query(), User.class);
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
    }

    @Test
    void roomCacheIsEvictedWhenParticipantsChange() {
        Room room = Room.builder()
                .name("cached room")
                .creator("user-1")
                .participantIds(new HashSet<>(java.util.Set.of("user-1")))
                .createdAt(LocalDateTime.now())
                .build();
        room = roomRepository.save(room);

        assertThat(roomRepository.findById(room.getId())).isPresent();
        roomRepository.addParticipantAndReturn(room.getId(), "user-2");

        Room updatedRoom = roomRepository.findById(room.getId()).orElseThrow();
        assertThat(updatedRoom.getParticipantIds()).contains("user-1", "user-2");
    }

    @Test
    void participantChangeKeepsRoomListCacheButEvictsRoomDetail() {
        Room room = roomRepository.save(Room.builder()
                .name("cached list room")
                .creator("user-1")
                .participantIds(new HashSet<>(java.util.Set.of("user-1")))
                .createdAt(LocalDateTime.now())
                .build());

        roomRepository.findAllByOrderByCreatedAtDesc();
        roomRepository.findById(room.getId()).orElseThrow();
        roomRepository.addParticipantAndReturn(room.getId(), "user-2");

        Room cachedListRoom = roomRepository.findAllByOrderByCreatedAtDesc().getFirst();
        Room refreshedDetail = roomRepository.findById(room.getId()).orElseThrow();
        assertThat(cachedListRoom.getParticipantIds()).doesNotContain("user-2");
        assertThat(refreshedDetail.getParticipantIds()).contains("user-2");
    }

    @Test
    void roomCreationInvalidatesRoomListSoNewRoomIsVisible() {
        roomRepository.save(Room.builder()
                .name("first")
                .creator("user-1")
                .participantIds(new HashSet<>(java.util.Set.of("user-1")))
                .createdAt(LocalDateTime.now().minusSeconds(1))
                .build());
        assertThat(roomRepository.findAllByOrderByCreatedAtDesc()).hasSize(1);

        userRepository.save(User.builder()
                .id("user-2")
                .name("Second creator")
                .email("creator2@example.com")
                .password("unused")
                .build());
        roomService.createRoom(
                CreateRoomRequest.builder().name("second").build(),
                "creator2@example.com");

        assertThat(roomRepository.findAllByOrderByCreatedAtDesc())
                .extracting(Room::getName)
                .containsExactly("second", "first");
    }

    @Test
    void concurrentParticipantAddsAreAtomicAndDuplicateSafe() throws Exception {
        Room room = roomRepository.save(Room.builder()
                .name("concurrent room")
                .creator("creator")
                .participantIds(new HashSet<>(java.util.Set.of("creator")))
                .createdAt(LocalDateTime.now())
                .build());
        List<String> userIds = java.util.stream.IntStream.range(0, 20)
                .mapToObj(index -> "user-" + index)
                .toList();
        String roomId = room.getId();

        try (var executor = Executors.newFixedThreadPool(12)) {
            for (String userId : userIds) {
                for (int duplicate = 0; duplicate < 2; duplicate++) {
                    executor.submit(() -> roomRepository.addParticipantAndReturn(roomId, userId));
                }
            }
        }

        Room updated = mongoTemplate.findById(roomId, Room.class);
        assertThat(updated).isNotNull();
        assertThat(updated.getParticipantIds()).containsAll(userIds).hasSize(21);
    }

    @Test
    void latestMessagePageUsesCachedCoreUntilTheRoomGenerationAdvances() {
        Message first = messageRepository.save(Message.builder()
                .roomId("room-1")
                .senderId("user-1")
                .content("first")
                .type(MessageType.text)
                .timestamp(LocalDateTime.now().minusSeconds(1))
                .build());

        MessagePageSnapshot initial = messagePageCache.loadLatest("room-1", 30);
        assertThat(initial.messages()).extracting(MessagePageSnapshot.CachedMessage::content)
                .containsExactly("first");

        mongoTemplate.remove(
                Query.query(Criteria.where("_id").is(first.getId())),
                Message.class);
        assertThat(messagePageCache.loadLatest("room-1", 30).messages())
                .extracting(MessagePageSnapshot.CachedMessage::content)
                .containsExactly("first");

        messageRepository.save(Message.builder()
                .roomId("room-1")
                .senderId("user-1")
                .content("second")
                .type(MessageType.text)
                .timestamp(LocalDateTime.now())
                .build());

        assertThat(messagePageCache.loadLatest("room-1", 30).messages())
                .extracting(MessagePageSnapshot.CachedMessage::content)
                .containsExactly("second");
    }
}
