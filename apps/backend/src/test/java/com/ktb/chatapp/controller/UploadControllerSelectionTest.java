package com.ktb.chatapp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.service.FileService;
import com.ktb.chatapp.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@DisplayName("file.storage.type에 따른 업로드 Controller 선택")
class UploadControllerSelectionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(FileService.class, () -> mock(FileService.class))
            .withBean(UserService.class, () -> mock(UserService.class))
            .withBean(UserRepository.class, () -> mock(UserRepository.class))
            .withUserConfiguration(
                    LocalFileUploadController.class,
                    LocalProfileImageUploadController.class,
                    UploadController.class,
                    DirectUploadCompletionController.class);

    @Test
    @DisplayName("local 모드에서는 multipart Controller만 등록한다")
    void localModeRegistersOnlyMultipartControllers() {
        contextRunner.withPropertyValues("file.storage.type=local").run(context -> {
            assertThat(context).hasSingleBean(LocalFileUploadController.class);
            assertThat(context).hasSingleBean(LocalProfileImageUploadController.class);
            assertThat(context).doesNotHaveBean(UploadController.class);
            assertThat(context).doesNotHaveBean(DirectUploadCompletionController.class);
        });
    }

    @Test
    @DisplayName("s3 모드에서는 Presigned URL Controller만 등록한다")
    void s3ModeRegistersOnlyDirectUploadControllers() {
        contextRunner.withPropertyValues("file.storage.type=s3").run(context -> {
            assertThat(context).doesNotHaveBean(LocalFileUploadController.class);
            assertThat(context).doesNotHaveBean(LocalProfileImageUploadController.class);
            assertThat(context).hasSingleBean(UploadController.class);
            assertThat(context).hasSingleBean(DirectUploadCompletionController.class);
        });
    }
}
