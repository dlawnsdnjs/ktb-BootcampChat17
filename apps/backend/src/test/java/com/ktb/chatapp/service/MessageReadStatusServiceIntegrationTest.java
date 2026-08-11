package com.ktb.chatapp.service;

import com.ktb.chatapp.config.MongoTestContainer;
import com.ktb.chatapp.config.RedisTestContainer;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.model.MessageType;
import com.ktb.chatapp.repository.MessageRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import net.datafaker.Faker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({MongoTestContainer.class, RedisTestContainer.class})
@TestPropertySource(properties = {
        "spring.data.mongodb.auto-index-creation=true",
        "socketio.enabled=false"
})
class MessageReadStatusServiceIntegrationTest {

    @Autowired
    private MessageReadStatusService messageReadStatusService;

    @Autowired
    private MessageRepository messageRepository;

    private Faker faker;
    private String roomId;
    private String userId;

    @BeforeEach
    void setUp() {
        faker = new Faker();
        roomId = faker.internet().uuid();
        userId = faker.internet().uuid();
    }

    @AfterEach
    void tearDown() {
        messageRepository.deleteAll();
    }

    @Test
    @DisplayName("여러 메시지의 읽음 상태를 한 번에 반영한다")
    void updatesEveryRequestedMessage() {
        List<String> messageIds = saveMessages(5);

        messageReadStatusService.updateReadStatus(messageIds, userId);

        for (String messageId : messageIds) {
            assertThat(readerIdsOf(messageId)).containsExactly(userId);
        }
    }

    @Test
    @DisplayName("이미 읽은 메시지에는 readers 를 중복으로 추가하지 않는다")
    void doesNotDuplicateExistingReader() {
        List<String> messageIds = saveMessages(3);

        messageReadStatusService.updateReadStatus(messageIds, userId);
        messageReadStatusService.updateReadStatus(messageIds, userId);

        for (String messageId : messageIds) {
            assertThat(readerIdsOf(messageId)).containsExactly(userId);
        }
    }

    @Test
    @DisplayName("다른 사용자의 읽음 기록은 보존한다")
    void keepsReadersFromOtherUsers() {
        String otherUserId = faker.internet().uuid();
        List<String> messageIds = saveMessages(2);

        messageReadStatusService.updateReadStatus(messageIds, otherUserId);
        messageReadStatusService.updateReadStatus(messageIds, userId);

        for (String messageId : messageIds) {
            assertThat(readerIdsOf(messageId))
                    .containsExactlyInAnyOrder(otherUserId, userId);
        }
    }

    @Test
    @DisplayName("일부만 읽지 않은 상태여도 읽지 않은 메시지만 갱신한다")
    void updatesOnlyUnreadMessages() {
        List<String> alreadyRead = saveMessages(2);
        messageReadStatusService.updateReadStatus(alreadyRead, userId);

        List<String> unread = saveMessages(2);
        List<String> all = new ArrayList<>(alreadyRead);
        all.addAll(unread);

        messageReadStatusService.updateReadStatus(all, userId);

        for (String messageId : all) {
            assertThat(readerIdsOf(messageId)).containsExactly(userId);
        }
    }

    @Test
    @DisplayName("존재하지 않는 메시지 ID 는 무시한다")
    void ignoresUnknownMessageIds() {
        List<String> messageIds = saveMessages(1);
        List<String> withUnknown = new ArrayList<>(messageIds);
        withUnknown.add("507f1f77bcf86cd799439011");

        messageReadStatusService.updateReadStatus(withUnknown, userId);

        assertThat(readerIdsOf(messageIds.getFirst())).containsExactly(userId);
    }

    @Test
    @DisplayName("빈 목록은 아무 것도 하지 않는다")
    void ignoresEmptyList() {
        messageReadStatusService.updateReadStatus(List.of(), userId);

        assertThat(messageRepository.count()).isZero();
    }

    private List<String> saveMessages(int count) {
        List<String> ids = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            Message message = messageRepository.save(Message.builder()
                    .roomId(roomId)
                    .senderId(faker.internet().uuid())
                    .content(faker.lorem().sentence())
                    .type(MessageType.text)
                    .timestamp(LocalDateTime.now())
                    .build());
            ids.add(message.getId());
        }

        return ids;
    }

    private List<String> readerIdsOf(String messageId) {
        return messageRepository.findById(messageId)
                .map(Message::getReaders)
                .orElseThrow()
                .stream()
                .map(Message.MessageReader::getUserId)
                .toList();
    }
}
