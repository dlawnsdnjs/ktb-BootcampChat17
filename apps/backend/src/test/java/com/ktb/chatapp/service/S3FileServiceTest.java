package com.ktb.chatapp.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ktb.chatapp.repository.FileRepository;
import com.ktb.chatapp.storage.StoragePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3FileService Presigned URL 전용 경로")
class S3FileServiceTest {

    @Mock
    private StoragePort storagePort;

    @Mock
    private FileRepository fileRepository;

    @Test
    @DisplayName("S3 모드에서 multipart 채팅 업로드를 거부한다")
    void rejectsMultipartChatUpload() {
        S3FileService service = new S3FileService(storagePort, fileRepository);
        MockMultipartFile upload = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.uploadFile(upload, "user-1"))
                .isInstanceOf(DirectUploadException.class)
                .hasMessageContaining("Presigned URL");

        verifyNoInteractions(storagePort, fileRepository);
    }

    @Test
    @DisplayName("S3 모드에서 multipart 프로필 업로드를 거부한다")
    void rejectsMultipartProfileUpload() {
        S3FileService service = new S3FileService(storagePort, fileRepository);
        MockMultipartFile upload = new MockMultipartFile(
                "profileImage", "photo.png", "image/png", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.storeFile(upload, "profiles"))
                .isInstanceOf(DirectUploadException.class)
                .hasMessageContaining("Presigned URL");

        verifyNoInteractions(storagePort, fileRepository);
    }
}
