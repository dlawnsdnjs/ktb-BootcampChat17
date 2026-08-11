package com.ktb.chatapp.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectTaggingRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class S3DirectUploadTest {
    private final S3Client s3Client = mock(S3Client.class);
    private final S3Presigner presigner = mock(S3Presigner.class);
    private final S3Storage storage = new S3Storage(s3Client, presigner, "bucket");

    @Test
    void presignUploadPreservesKeyContentTypeAndTtl() throws Exception {
        PresignedPutObjectRequest signed = mock(PresignedPutObjectRequest.class);
        when(signed.url()).thenReturn(new URL("https://signed.example.test/file"));
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(signed);

        PresignedUpload result = storage.presignUpload(
                "chat/file.png", "image/png", Duration.ofMinutes(5)).orElseThrow();

        ArgumentCaptor<PutObjectPresignRequest> captor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(presigner).presignPutObject(captor.capture());
        assertThat(captor.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(captor.getValue().putObjectRequest().bucket()).isEqualTo("bucket");
        assertThat(captor.getValue().putObjectRequest().key()).isEqualTo("chat/file.png");
        assertThat(captor.getValue().putObjectRequest().contentType()).isEqualTo("image/png");
        assertThat(result.requiredHeaders()).containsEntry("Content-Type", "image/png");
        assertThat(result.requiredHeaders()).containsEntry("x-amz-tagging", "upload-state=pending");
    }

    @Test
    void realPresignerCreatesUploadUrlWithoutCallingS3() {
        try (S3Presigner realPresigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                "test-access-key-id", "test-secret-access-key")))
                .build()) {
            S3Storage realStorage = new S3Storage(s3Client, realPresigner, "test-bucket");

            PresignedUpload result = realStorage.presignUpload(
                    "chat/file.png", "image/png", Duration.ofMinutes(5)).orElseThrow();

            assertThat(result.url().getScheme()).isEqualTo("https");
            assertThat(result.url().getQuery()).contains("X-Amz-Signature=");
            assertThat(result.requiredHeaders())
                    .containsEntry("x-amz-tagging", "upload-state=pending");
        }
    }

    @Test
    void metadataReturnsSizeAndContentType() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder().contentLength(42L).contentType("image/png").build());

        assertThat(storage.metadata("chat/file.png")).contains(
                new StoredObjectMetadata(42L, "image/png"));
    }

    @Test
    void completionReplacesPendingTag() {
        assertThat(storage.markUploadCompleted("chat/file.png")).isTrue();

        ArgumentCaptor<PutObjectTaggingRequest> captor =
                ArgumentCaptor.forClass(PutObjectTaggingRequest.class);
        verify(s3Client).putObjectTagging(captor.capture());
        assertThat(captor.getValue().tagging().tagSet())
                .anySatisfy(tag -> {
                    assertThat(tag.key()).isEqualTo("upload-state");
                    assertThat(tag.value()).isEqualTo("completed");
                });
    }
}
