package com.ktb.chatapp.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
@DisplayName("LocalFileService 저장 정합성")
class LocalFileServiceTest {

    @Mock
    private StoragePort storagePort;

    @Mock
    private FileRepository fileRepository;

    @Test
    @DisplayName("객체 업로드 후 DB 저장이 실패하면 업로드한 key를 보상 삭제한다")
    void uploadFile_repositoryFailure_deletesUploadedObject() {
        LocalFileService service = new LocalFileService(storagePort, fileRepository);
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[] {1, 2, 3});
        when(storagePort.put(any(), any(), any(), anyLong()))
                .thenAnswer(invocation -> new StoredObject(invocation.getArgument(1), 3));
        when(fileRepository.save(any())).thenThrow(new RuntimeException("database unavailable"));

        assertThatThrownBy(() -> service.uploadFile(file, "user-1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("파일 업로드에 실패했습니다");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storagePort).delete(key.capture());
        org.assertj.core.api.Assertions.assertThat(key.getValue()).startsWith("chat/");
    }
}
