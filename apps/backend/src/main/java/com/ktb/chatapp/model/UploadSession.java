package com.ktb.chatapp.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "upload_sessions")
public class UploadSession {
    @Id
    private String id;
    private String ownerId;
    private UploadPurpose purpose;
    private String key;
    private String originalName;
    private String contentType;
    private long expectedSize;
    private UploadStatus status;
    private String resultFileId;
    private String resultImageUrl;
    private Instant createdAt;
    @Indexed(expireAfter = "0s")
    private Instant expiresAt;
}
