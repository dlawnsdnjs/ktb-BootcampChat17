package com.ktb.chatapp.repository;

import com.ktb.chatapp.model.Room;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import com.ktb.chatapp.service.cache.ChatReadCacheNames;

@RequiredArgsConstructor
public class RoomRepositoryImpl implements RoomRepositoryCustom {

    private final MongoTemplate mongoTemplate;
    private final ObjectProvider<CacheManager> cacheManagerProvider;

    @Override
    public Optional<Room> addParticipantAndReturn(String roomId, String userId) {
        Query query = Query.query(Criteria.where("_id").is(roomId));
        Update update = new Update().addToSet("participantIds", userId);
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);

        Room updated = mongoTemplate.findAndModify(query, update, options, Room.class);
        updateRoomDetail(roomId, updated);
        return Optional.ofNullable(updated);
    }

    @Override
    public void removeParticipant(String roomId, String userId) {
        Query query = Query.query(Criteria.where("_id").is(roomId));
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true);
        Room updated = mongoTemplate.findAndModify(
                query, new Update().pull("participantIds", userId), options, Room.class);
        updateRoomDetail(roomId, updated);
    }

    private void updateRoomDetail(String roomId, Room updated) {
        CacheManager cacheManager = cacheManagerProvider.getIfAvailable();
        if (cacheManager != null && cacheManager.getCache(ChatReadCacheNames.ROOM_BY_ID) != null) {
            if (updated == null) {
                cacheManager.getCache(ChatReadCacheNames.ROOM_BY_ID).evict(roomId);
            } else {
                cacheManager.getCache(ChatReadCacheNames.ROOM_BY_ID).put(roomId, updated);
            }
        }
    }
}
