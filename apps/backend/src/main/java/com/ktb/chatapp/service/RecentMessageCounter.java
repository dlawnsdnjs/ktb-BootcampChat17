package com.ktb.chatapp.service;

import com.ktb.chatapp.repository.MessageRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 채팅방 목록에 노출하는 "최근 메시지 수"의 집계 창을 한곳에서 관리한다.
 */
@Component
@RequiredArgsConstructor
public class RecentMessageCounter {

    static final Duration RECENT_WINDOW = Duration.ofMinutes(30);

    private final MessageRepository messageRepository;
    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate redisTemplate;
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT =
            new DefaultRedisScript<>("""
                    local count = redis.call('INCR', KEYS[1])
                    if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
                    return count
                    """, Long.class);

    @Value("${recent-message-counter.store.type:mongo}")
    private String storeType;
    @Value("${recent-message-counter.redis.key-prefix:recent-message-count}")
    private String keyPrefix;

    public int countRecentMessages(String roomId) {
        if (usesRedis()) {
            String cached = redisTemplate.opsForValue().get(key(roomId));
            if (cached != null) {
                return Integer.parseInt(cached);
            }
        }
        LocalDateTime since = LocalDateTime.now().minus(RECENT_WINDOW);
        int count = (int) messageRepository.countRecentMessagesByRoomId(roomId, since);
        cacheCount(roomId, count);
        return count;
    }

    public int incrementAndGet(String roomId) {
        if (!usesRedis()) {
            return countRecentMessages(roomId);
        }
        Long count = redisTemplate.execute(
                INCREMENT_SCRIPT,
                List.of(key(roomId)),
                Long.toString(RECENT_WINDOW.toSeconds()));
        if (count == null) {
            return countRecentMessages(roomId);
        }
        return Math.toIntExact(count);
    }

    public Map<String, Integer> countRecentMessages(Collection<String> roomIds) {
        if (roomIds.isEmpty()) {
            return Collections.emptyMap();
        }

        if (!usesRedis()) {
            return countRecentMessagesFromMongo(roomIds);
        }

        List<String> ids = new ArrayList<>(roomIds);
        List<String> values = redisTemplate.opsForValue()
                .multiGet(ids.stream().map(this::key).toList());
        Map<String, Integer> counts = new HashMap<>();
        List<String> missing = new ArrayList<>();
        for (int index = 0; index < ids.size(); index++) {
            String value = values != null ? values.get(index) : null;
            if (value == null) {
                missing.add(ids.get(index));
            } else {
                counts.put(ids.get(index), Integer.parseInt(value));
            }
        }
        Map<String, Integer> reconciled = countRecentMessagesFromMongo(missing);
        for (String roomId : missing) {
            int count = reconciled.getOrDefault(roomId, 0);
            counts.put(roomId, count);
            cacheCount(roomId, count);
        }
        return counts;
    }

    private Map<String, Integer> countRecentMessagesFromMongo(Collection<String> roomIds) {
        if (roomIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LocalDateTime since = LocalDateTime.now().minus(RECENT_WINDOW);
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("room").in(roomIds)
                        .and("timestamp").gte(since)),
                Aggregation.group("room").count().as("count"));

        return mongoTemplate.aggregate(aggregation, "messages", Document.class)
                .getMappedResults()
                .stream()
                .collect(Collectors.toMap(
                        result -> result.getString("_id"),
                        result -> ((Number) result.get("count")).intValue()));
    }

    private boolean usesRedis() {
        return "redis".equalsIgnoreCase(storeType);
    }

    private String key(String roomId) {
        return keyPrefix + ":" + roomId;
    }

    private void cacheCount(String roomId, int count) {
        if (usesRedis()) {
            redisTemplate.opsForValue().set(key(roomId), Integer.toString(count), RECENT_WINDOW);
        }
    }
}
