package com.ktb.chatapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb.chatapp.model.Message;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@ExtendWith(MockitoExtension.class)
class MessageReadStatusServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Test
    void updateReadStatus_updatesAllUnreadMessagesWithOneOperation() {
        when(mongoTemplate.updateMulti(
                org.mockito.ArgumentMatchers.any(Query.class),
                org.mockito.ArgumentMatchers.any(Update.class),
                eq(Message.class)))
                .thenReturn(UpdateResult.acknowledged(3, 3L, null));
        MessageReadStatusService service = new MessageReadStatusService(mongoTemplate);

        service.updateReadStatus(List.of("message-1", "message-2", "message-3"), "user-1");

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateMulti(
                queryCaptor.capture(), updateCaptor.capture(), eq(Message.class));

        Document query = queryCaptor.getValue().getQueryObject();
        assertThat(query.get("_id")).isEqualTo(new Document("$in",
                List.of("message-1", "message-2", "message-3")));
        assertThat(query.get("readers.userId")).isEqualTo(new Document("$ne", "user-1"));
        assertThat(updateCaptor.getValue().getUpdateObject()).containsKey("$push");
    }

    @Test
    void updateReadStatus_skipsEmptyMessageList() {
        MessageReadStatusService service = new MessageReadStatusService(mongoTemplate);

        service.updateReadStatus(List.of(), "user-1");

        verify(mongoTemplate, never()).updateMulti(
                org.mockito.ArgumentMatchers.any(Query.class),
                org.mockito.ArgumentMatchers.any(Update.class),
                eq(Message.class));
    }
}
