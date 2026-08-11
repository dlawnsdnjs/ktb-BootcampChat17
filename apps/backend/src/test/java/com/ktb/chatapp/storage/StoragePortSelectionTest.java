package com.ktb.chatapp.storage;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("file.storage.type 스위치 단위 테스트")
class StoragePortSelectionTest {

    @TempDir
    private Path uploadDir;

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(
                    LocalStorage.class, S3Storage.class, S3StorageConfiguration.class);

    @Test
    @DisplayName("프로퍼티 미설정 시 LocalStorage 빈이 등록된다")
    void localStorageIsRegisteredWhenPropertyMissing() {
        contextRunner
                .withPropertyValues("file.upload-dir=" + uploadDir)
                .run(context -> {
                    assertThat(context).hasSingleBean(StoragePort.class);
                    assertThat(context).hasSingleBean(LocalStorage.class);
                    assertThat(context).doesNotHaveBean(S3Storage.class);
                });
    }

    @Test
    @DisplayName("file.storage.type=local이면 LocalStorage 빈이 등록된다")
    void localStorageIsRegisteredWhenPropertyIsLocal() {
        contextRunner
                .withPropertyValues("file.storage.type=local", "file.upload-dir=" + uploadDir)
                .run(context -> {
                    assertThat(context).hasSingleBean(StoragePort.class);
                    assertThat(context).hasSingleBean(LocalStorage.class);
                    assertThat(context).doesNotHaveBean(S3Storage.class);
                });
    }

    @Test
    @DisplayName("file.storage.type=s3이면 S3Storage와 S3 클라이언트만 등록된다")
    void s3StorageIsRegisteredWhenConfigured() {
        contextRunner
                .withPropertyValues(
                        "file.storage.type=s3",
                        "file.storage.s3.bucket=test-bucket",
                        "file.storage.s3.region=ap-northeast-2")
                .run(context -> {
                    assertThat(context).hasSingleBean(StoragePort.class);
                    assertThat(context).hasSingleBean(S3Storage.class);
                    assertThat(context).doesNotHaveBean(LocalStorage.class);
                    assertThat(context).hasSingleBean(S3Client.class);
                    assertThat(context).hasSingleBean(S3Presigner.class);
                    assertThat(context.getBean(AwsCredentialsProvider.class))
                            .isInstanceOf(DefaultCredentialsProvider.class);
                });
    }

    @Test
    @DisplayName("Configured local AWS keys use a static credentials provider")
    void s3StorageUsesConfiguredLocalCredentials() {
        contextRunner
                .withPropertyValues(
                        "file.storage.type=s3",
                        "file.storage.s3.bucket=test-bucket",
                        "file.storage.s3.region=ap-northeast-2",
                        "file.storage.s3.access-key=test-access-key",
                        "file.storage.s3.secret-key=test-secret-key")
                .run(context -> {
                    AwsCredentialsProvider provider = context.getBean(AwsCredentialsProvider.class);

                    assertThat(provider.resolveCredentials().accessKeyId())
                            .isEqualTo("test-access-key");
                    assertThat(provider.resolveCredentials().secretAccessKey())
                            .isEqualTo("test-secret-key");
                });
    }

    @Test
    @DisplayName("Only one configured local AWS key fails context startup")
    void s3StorageRejectsIncompleteLocalCredentials() {
        contextRunner
                .withPropertyValues(
                        "file.storage.type=s3",
                        "file.storage.s3.bucket=test-bucket",
                        "file.storage.s3.region=ap-northeast-2",
                        "file.storage.s3.access-key=test-access-key")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("S3 bucket 설정이 없으면 컨텍스트 시작이 실패한다")
    void s3StorageFailsWithoutBucket() {
        contextRunner
                .withPropertyValues(
                        "file.storage.type=s3",
                        "file.storage.s3.region=ap-northeast-2")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("S3 region 설정이 없으면 컨텍스트 시작이 실패한다")
    void s3StorageFailsWithoutRegion() {
        contextRunner
                .withPropertyValues(
                        "file.storage.type=s3",
                        "file.storage.s3.bucket=test-bucket")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("알 수 없는 storage type에는 StoragePort가 등록되지 않는다")
    void unknownStorageTypeDoesNotRegisterStorage() {
        contextRunner
                .withPropertyValues("file.storage.type=unknown")
                .run(context -> assertThat(context).doesNotHaveBean(StoragePort.class));
    }
}
