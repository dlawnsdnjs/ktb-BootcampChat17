package com.ktb.chatapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ktb.chatapp.repository.FileRepository;
import com.ktb.chatapp.storage.StoragePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("file.storage.type에 따른 FileService 선택")
class FileServiceSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(StoragePort.class, () -> mock(StoragePort.class))
            .withBean(FileRepository.class, () -> mock(FileRepository.class))
            .withUserConfiguration(LocalFileService.class, S3FileService.class);

    @Test
    @DisplayName("설정이 없으면 LocalFileService만 선택된다")
    void defaultsToLocalFileService() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FileService.class);
            assertThat(context).hasSingleBean(LocalFileService.class);
            assertThat(context).doesNotHaveBean(S3FileService.class);
        });
    }

    @Test
    @DisplayName("local 설정이면 LocalFileService만 선택된다")
    void selectsLocalFileService() {
        contextRunner.withPropertyValues("file.storage.type=local").run(context -> {
            assertThat(context).hasSingleBean(FileService.class);
            assertThat(context).hasSingleBean(LocalFileService.class);
            assertThat(context).doesNotHaveBean(S3FileService.class);
        });
    }

    @Test
    @DisplayName("s3 설정이면 S3FileService만 선택된다")
    void selectsS3FileService() {
        contextRunner.withPropertyValues("file.storage.type=s3").run(context -> {
            assertThat(context).hasSingleBean(FileService.class);
            assertThat(context).hasSingleBean(S3FileService.class);
            assertThat(context).doesNotHaveBean(LocalFileService.class);
        });
    }

    @Test
    @DisplayName("알 수 없는 설정이면 FileService를 선택하지 않는다")
    void rejectsUnknownStorageType() {
        contextRunner.withPropertyValues("file.storage.type=unknown")
                .run(context -> assertThat(context).doesNotHaveBean(FileService.class));
    }
}
