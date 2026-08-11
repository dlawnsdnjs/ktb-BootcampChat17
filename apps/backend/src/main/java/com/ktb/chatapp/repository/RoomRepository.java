package com.ktb.chatapp.repository;

import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.service.cache.ChatReadCacheNames;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface RoomRepository extends MongoRepository<Room, String>, RoomRepositoryCustom {

    @Cacheable(cacheNames = ChatReadCacheNames.ROOM_LIST, sync = true)
    List<Room> findAllByOrderByCreatedAtDesc();

    @Override
    @Cacheable(cacheNames = ChatReadCacheNames.ROOM_BY_ID, key = "#id", sync = true)
    Optional<Room> findById(String id);

    @Override
    @Caching(
            put = @CachePut(cacheNames = ChatReadCacheNames.ROOM_BY_ID, key = "#result.id"),
            evict = @CacheEvict(cacheNames = ChatReadCacheNames.ROOM_LIST, allEntries = true))
    <S extends Room> S save(S entity);

    // 가장 최근에 생성된 방 조회 (Health Check용)
    @Query(value = "{}", sort = "{ 'createdAt': -1 }")
    Optional<Room> findMostRecentRoom();

    // Health Check용 단순 조회 (지연 시간 측정)
    @Query(value = "{}", fields = "{ '_id': 1 }")
    Optional<Room> findOneForHealthCheck();

    @Override
    Optional<Room> addParticipantAndReturn(String roomId, String userId);

    @Override
    void removeParticipant(String roomId, String userId);
}
