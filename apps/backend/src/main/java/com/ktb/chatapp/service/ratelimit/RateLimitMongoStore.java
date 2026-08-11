package com.ktb.chatapp.service.ratelimit;

import com.ktb.chatapp.model.RateLimit;
import com.ktb.chatapp.repository.RateLimitRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * MongoDB implementation of RateLimitStore.
 * Uses RateLimitRepository for persistence.
 */
@Component
@RequiredArgsConstructor
public class RateLimitMongoStore implements RateLimitStore {
    
    private final RateLimitRepository rateLimitRepository;
    private final MongoTemplate mongoTemplate;
    
    @Override
    public Optional<RateLimit> findByClientId(String clientId) {
        return rateLimitRepository.findByClientId(clientId);
    }
    
    @Override
    public RateLimit save(RateLimit rateLimit) {
        if (rateLimit.getId() == null) {
            Query query = Query.query(Criteria.where("clientId").is(rateLimit.getClientId()));
            Update insert = new Update()
                    .setOnInsert("clientId", rateLimit.getClientId())
                    .setOnInsert("count", rateLimit.getCount())
                    .setOnInsert("expiresAt", rateLimit.getExpiresAt());
            RateLimit stored = mongoTemplate.findAndModify(
                    query,
                    insert,
                    FindAndModifyOptions.options().upsert(true).returnNew(true),
                    RateLimit.class);
            return stored != null ? stored : rateLimit;
        }
        return rateLimitRepository.save(rateLimit);
    }
}
