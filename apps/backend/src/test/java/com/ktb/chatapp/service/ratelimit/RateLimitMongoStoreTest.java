package com.ktb.chatapp.service.ratelimit;

import com.ktb.chatapp.model.RateLimit;
import com.ktb.chatapp.repository.RateLimitRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitMongoStoreTest {

    @Mock
    private RateLimitRepository rateLimitRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    private RateLimitMongoStore store;

    @BeforeEach
    void setUp() {
        store = new RateLimitMongoStore(rateLimitRepository, mongoTemplate);
    }

    @Test
    void newClientIsCreatedWithAtomicUpsert() {
        RateLimit candidate = RateLimit.builder()
                .clientId("host:client")
                .count(1)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        RateLimit persisted = RateLimit.builder()
                .id("rate-limit-id")
                .clientId(candidate.getClientId())
                .count(1)
                .expiresAt(candidate.getExpiresAt())
                .build();
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(RateLimit.class)))
                .thenReturn(persisted);

        RateLimit result = store.save(candidate);

        assertThat(result).isSameAs(persisted);
        verify(rateLimitRepository, never()).save(any());
    }

    @Test
    void existingClientUsesRepositoryUpdate() {
        RateLimit existing = RateLimit.builder()
                .id("rate-limit-id")
                .clientId("host:client")
                .count(2)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(rateLimitRepository.save(existing)).thenReturn(existing);

        RateLimit result = store.save(existing);

        assertThat(result).isSameAs(existing);
        verify(mongoTemplate, never()).findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(RateLimit.class));
    }
}
