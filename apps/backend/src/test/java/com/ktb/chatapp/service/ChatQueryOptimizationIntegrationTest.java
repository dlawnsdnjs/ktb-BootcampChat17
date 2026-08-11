package com.ktb.chatapp.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.chatapp.config.MongoTestContainer;
import com.ktb.chatapp.config.RedisTestContainer;
import com.ktb.chatapp.model.Message;
import com.ktb.chatapp.repository.MessageRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@Import({MongoTestContainer.class, RedisTestContainer.class})
@TestPropertySource(properties = {
        "spring.data.mongodb.auto-index-creation=true",
        "socketio.enabled=false"
})
class ChatQueryOptimizationIntegrationTest {

    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private MessageRepository messageRepository;
    @Autowired private RecentMessageCounter recentMessageCounter;

    @AfterEach
    void tearDown() {
        messageRepository.deleteAll();
    }

    @Test
    void createsChatReadIndexesWithExpectedKeyOrder() {
        Map<String, Document> messageIndexes = indexesByName("messages");
        Map<String, Document> roomIndexes = indexesByName("rooms");

        assertThat(messageIndexes.get("room_timestamp_desc_idx"))
                .containsEntry("room", 1)
                .containsEntry("timestamp", -1);
        assertThat(new ArrayList<>(messageIndexes.get("room_timestamp_desc_idx").keySet()))
                .containsExactly("room", "timestamp");
        assertThat(roomIndexes.get("created_at_desc_idx"))
                .containsExactlyEntriesOf(new Document("createdAt", -1));
    }

    @Test
    void messageHistoryIndexLimitsDocumentsExamined() {
        String roomId = "room-index-test";
        for (int i = 0; i < 100; i++) {
            saveMessage(roomId, LocalDateTime.now().minusMinutes(i));
            saveMessage("another-room", LocalDateTime.now().minusMinutes(i));
        }

        Document find = new Document("find", "messages")
                .append("filter", new Document("room", roomId)
                        .append("timestamp", new Document("$lt", LocalDateTime.now())))
                .append("sort", new Document("timestamp", -1))
                .append("limit", 30)
                .append("hint", "room_timestamp_desc_idx");
        Document explain = mongoTemplate.executeCommand(new Document("explain", find)
                .append("verbosity", "executionStats"));

        assertThat(explain.toJson()).contains("room_timestamp_desc_idx", "IXSCAN");
        assertThat(explain.get("executionStats", Document.class)
                .getInteger("totalDocsExamined")).isLessThanOrEqualTo(30);
    }

    @Test
    void countsRecentMessagesForAllRoomsInOneAggregation() {
        saveMessage("room-1", LocalDateTime.now().minusMinutes(5));
        saveMessage("room-1", LocalDateTime.now().minusMinutes(10));
        saveMessage("room-1", LocalDateTime.now().minusHours(2));
        saveMessage("room-2", LocalDateTime.now().minusMinutes(1));

        Map<String, Integer> counts = recentMessageCounter
                .countRecentMessages(List.of("room-1", "room-2", "room-3"));

        assertThat(counts).containsExactlyInAnyOrderEntriesOf(
                Map.of("room-1", 2, "room-2", 1));
    }

    private Map<String, Document> indexesByName(String collection) {
        List<Document> indexes = mongoTemplate.getCollection(collection)
                .listIndexes()
                .into(new ArrayList<>());
        return indexes.stream().collect(java.util.stream.Collectors.toMap(
                index -> index.getString("name"),
                index -> index.get("key", Document.class)));
    }

    private void saveMessage(String roomId, LocalDateTime timestamp) {
        Message saved = messageRepository.save(Message.builder()
                .roomId(roomId)
                .content("test")
                .build());
        saved.setTimestamp(timestamp);
        messageRepository.save(saved);
    }
}
