package com.ktb.chatapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb.chatapp.dto.PresignUploadRequest;
import com.ktb.chatapp.model.File;
import com.ktb.chatapp.model.UploadPurpose;
import com.ktb.chatapp.model.UploadSession;
import com.ktb.chatapp.model.UploadStatus;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.FileRepository;
import com.ktb.chatapp.repository.UploadSessionRepository;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.storage.PresignedUpload;
import com.ktb.chatapp.storage.StoragePort;
import com.ktb.chatapp.storage.StoredObjectMetadata;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

@ExtendWith(MockitoExtension.class)
class DirectUploadServiceTest {
    @Mock StoragePort storage;
    @Mock UploadSessionRepository sessions;
    @Mock FileRepository files;
    @Mock UserRepository users;
    @Mock MongoTemplate mongoTemplate;

    private DirectUploadService service;
    private final User user = User.builder().id("user-1").email("user@example.com").build();

    @BeforeEach
    void setUp() {
        service = new DirectUploadService(storage, sessions, files, users, mongoTemplate);
        lenient().when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void presignCreatesServerOwnedSession() {
        when(sessions.save(any(UploadSession.class))).thenAnswer(invocation -> {
            UploadSession session = invocation.getArgument(0);
            session.setId("upload-1");
            return session;
        });
        when(storage.presignUpload(any(), any(), any())).thenReturn(Optional.of(
                new PresignedUpload(URI.create("https://signed.example/upload"),
                        Instant.now().plusSeconds(300), Map.of("Content-Type", "image/png"))));

        var response = service.presign("user@example.com",
                new PresignUploadRequest(UploadPurpose.CHAT_ATTACHMENT, "photo.png", "image/png", 12));

        assertThat(response.uploadId()).isEqualTo("upload-1");
        assertThat(response.uploadUrl()).isEqualTo("https://signed.example/upload");
    }

    @Test
    void profileRejectsNonImageBeforeCreatingSession() {
        assertThatThrownBy(() -> service.presign("user@example.com",
                new PresignUploadRequest(UploadPurpose.PROFILE_IMAGE, "doc.pdf", "application/pdf", 12)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(sessions, never()).save(any());
    }

    @Test
    void completeChatVerifiesMetadataAndPersistsFile() {
        UploadSession claimed = pending(UploadPurpose.CHAT_ATTACHMENT, "chat/file.png");
        claimed.setStatus(UploadStatus.COMPLETING);
        when(mongoTemplate.findAndModify(any(), any(), any(), any(Class.class))).thenReturn(claimed);
        when(storage.metadata("chat/file.png")).thenReturn(Optional.of(
                new StoredObjectMetadata(12, "image/png")));
        when(storage.markUploadCompleted("chat/file.png")).thenReturn(true);
        when(files.save(any(File.class))).thenAnswer(invocation -> {
            File file = invocation.getArgument(0);
            file.setId("file-1");
            return file;
        });

        File result = service.completeChat("user@example.com", "upload-1");

        assertThat(result.getId()).isEqualTo("file-1");
        assertThat(result.getFilename()).isEqualTo("file.png");
        assertThat(claimed.getStatus()).isEqualTo(UploadStatus.COMPLETED);
        verify(sessions).save(claimed);
    }

    @Test
    void completionRejectsAnotherOwner() {
        when(mongoTemplate.findAndModify(any(), any(), any(), any(Class.class))).thenReturn(null);
        UploadSession session = pending(UploadPurpose.CHAT_ATTACHMENT, "chat/file.png");
        session.setOwnerId("other-user");
        when(sessions.findById("upload-1")).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.completeChat("user@example.com", "upload-1"))
                .isInstanceOf(DirectUploadException.class)
                .extracting(ex -> ((DirectUploadException) ex).getStatus().value())
                .isEqualTo(403);
        verify(storage, never()).metadata(any());
    }

    @Test
    void repeatedCompletionReturnsExistingFileWithoutReadingS3() {
        when(mongoTemplate.findAndModify(any(), any(), any(), any(Class.class))).thenReturn(null);
        UploadSession session = pending(UploadPurpose.CHAT_ATTACHMENT, "chat/file.png");
        session.setStatus(UploadStatus.COMPLETED);
        session.setResultFileId("file-1");
        File file = File.builder().id("file-1").build();
        when(sessions.findById("upload-1")).thenReturn(Optional.of(session));
        when(files.findById("file-1")).thenReturn(Optional.of(file));

        assertThat(service.completeChat("user@example.com", "upload-1")).isSameAs(file);
        verify(storage, never()).metadata(any());
    }

    @Test
    void metadataMismatchFailsSessionAndDeletesObject() {
        UploadSession claimed = pending(UploadPurpose.CHAT_ATTACHMENT, "chat/file.png");
        claimed.setStatus(UploadStatus.COMPLETING);
        when(mongoTemplate.findAndModify(any(), any(), any(), any(Class.class))).thenReturn(claimed);
        when(storage.metadata("chat/file.png")).thenReturn(Optional.of(
                new StoredObjectMetadata(99, "image/png")));

        assertThatThrownBy(() -> service.completeChat("user@example.com", "upload-1"))
                .isInstanceOf(DirectUploadException.class);
        assertThat(claimed.getStatus()).isEqualTo(UploadStatus.FAILED);
        verify(storage).delete("chat/file.png");
        verify(files, never()).save(any());
    }

    @Test
    void completeProfileUpdatesUserAndReturnsStableRelativeUrl() {
        user.setProfileImage("profiles/old.png");
        UploadSession claimed = pending(UploadPurpose.PROFILE_IMAGE, "profiles/new.png");
        claimed.setStatus(UploadStatus.COMPLETING);
        when(mongoTemplate.findAndModify(any(), any(), any(), any(Class.class))).thenReturn(claimed);
        when(storage.metadata("profiles/new.png")).thenReturn(Optional.of(
                new StoredObjectMetadata(12, "image/png")));
        when(storage.markUploadCompleted("profiles/new.png")).thenReturn(true);
        when(users.save(user)).thenReturn(user);

        var response = service.completeProfile("user@example.com", "upload-1");

        assertThat(response.getImageUrl()).isEqualTo("/api/files/profiles/new.png");
        assertThat(user.getProfileImage()).isEqualTo("profiles/new.png");
        verify(storage).delete("profiles/old.png");
    }

    private UploadSession pending(UploadPurpose purpose, String key) {
        return UploadSession.builder()
                .id("upload-1")
                .ownerId("user-1")
                .purpose(purpose)
                .key(key)
                .originalName("photo.png")
                .contentType("image/png")
                .expectedSize(12)
                .status(UploadStatus.PENDING)
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
    }
}
