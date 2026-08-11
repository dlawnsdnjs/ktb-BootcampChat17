package com.ktb.chatapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ktb.chatapp.model.File;
import com.ktb.chatapp.repository.FileRepository;
import com.ktb.chatapp.storage.StoragePort;
import com.ktb.chatapp.storage.StoredObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3FileService multipart 호환 경로")
class S3FileServiceTest {

    @Mock
    private StoragePort storagePort;

    @Mock
    private FileRepository fileRepository;

    @Test
    @DisplayName("기존 multipart 채팅 업로드도 S3 key만 DB에 저장한다")
    void uploadFileStoresObjectKey() {
        S3FileService service = new S3FileService(storagePort, fileRepository);
        MockMultipartFile upload = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[] {1, 2, 3});
        when(storagePort.put(any(), any(), any(), anyLong()))
                .thenAnswer(invocation -> new StoredObject(invocation.getArgument(1), 3));
        when(fileRepository.save(any(File.class))).thenAnswer(invocation -> {
            File file = invocation.getArgument(0);
            file.setId("file-1");
            return file;
        });

        FileUploadResult result = service.uploadFile(upload, "user-1");

        ArgumentCaptor<File> saved = ArgumentCaptor.forClass(File.class);
        verify(fileRepository).save(saved.capture());
        assertThat(result.isSuccess()).isTrue();
        assertThat(saved.getValue().getPath()).startsWith("chat/");
        assertThat(saved.getValue().getPath()).endsWith(saved.getValue().getFilename());
        assertThat(saved.getValue().getPath()).doesNotStartWith("http");
    }
}
