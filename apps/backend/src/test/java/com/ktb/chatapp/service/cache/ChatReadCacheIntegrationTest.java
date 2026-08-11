package com.ktb.chatapp.service.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.chatapp.config.MongoTestContainer;
import com.ktb.chatapp.config.RedisTestContainer;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.MessageType;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.repository.MessageRepository;
import com.ktb.chatapp.repository.RoomRepository;
import java.time.LocalDateTime;
import java.util.HashSet;
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
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private StringRedisTemplate redisTemplate;

    @BeforeEach
    void clearState() {
        mongoTemplate.remove(new Query(), Room.class);
        mongoTemplate.remove(new Query(), Message.class);
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
        roomRepository.addParticipant(room.getId(), "user-2");

        Room updatedRoom = roomRepository.findById(room.getId()).orElseThrow();
        assertThat(updatedRoom.getParticipantIds()).contains("user-1", "user-2");
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
