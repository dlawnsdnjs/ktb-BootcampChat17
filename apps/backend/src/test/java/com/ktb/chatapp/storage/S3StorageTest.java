package com.ktb.chatapp.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3Storage 단위 테스트")
class S3StorageTest {

    private static final String BUCKET = "chat-files-test";
    private static final String KEY = "chat/photo.png";

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3Storage storage;

    @BeforeEach
    void setUp() {
        storage = new S3Storage(s3Client, s3Presigner, BUCKET);
    }

    @Test
    @DisplayName("업로드 시 bucket, key, content type, 크기를 보존한다")
    void put_preservesObjectMetadata() {
        byte[] bytes = "image-bytes".getBytes(StandardCharsets.UTF_8);

        StoredObject stored = storage.put(
                new ByteArrayInputStream(bytes), KEY, "image/png", bytes.length);

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(request.capture(), body.capture());
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key()).isEqualTo(KEY);
        assertThat(request.getValue().contentType()).isEqualTo("image/png");
        assertThat(request.getValue().contentLength()).isEqualTo(bytes.length);
        assertThat(body.getValue().contentLength()).isEqualTo(bytes.length);
        assertThat(stored).isEqualTo(new StoredObject(KEY, bytes.length));
    }

    @Test
    @DisplayName("존재하는 객체는 길이를 보존한 스트림 Resource로 연다")
    void open_existingObject_returnsStreamingResource() throws Exception {
        byte[] bytes = "image-bytes".getBytes(StandardCharsets.UTF_8);
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().contentLength((long) bytes.length).build());
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(new ResponseInputStream<>(
                        GetObjectResponse.builder().contentLength((long) bytes.length).build(),
                        new ByteArrayInputStream(bytes)));

        Optional<Resource> resource = storage.open(KEY);

        assertThat(resource).isPresent();
        assertThat(resource.orElseThrow().contentLength()).isEqualTo(bytes.length);
        assertThat(resource.orElseThrow().getInputStream().readAllBytes()).isEqualTo(bytes);
        ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key()).isEqualTo(KEY);
    }

    @Test
    @DisplayName("없는 객체는 Optional.empty를 반환한다")
    void open_missingObject_returnsEmpty() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("not found").build());

        assertThat(storage.open(KEY)).isEmpty();
    }

    @Test
    @DisplayName("S3 오류 메시지는 외부 예외에 버킷 정보를 포함하지 않는다")
    void put_s3Failure_isSanitized() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().statusCode(500).message(BUCKET).build());

        assertThatThrownBy(() -> storage.put(
                new ByteArrayInputStream(new byte[] {1}), KEY, "image/png", 1))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("파일 저장소 저장에 실패했습니다.")
                .hasMessageNotContaining(BUCKET)
                .hasNoCause();
    }

    @Test
    @DisplayName("삭제 요청은 bucket과 key를 사용한다")
    void delete_usesBucketAndKey() {
        storage.delete(KEY);

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());
        assertThat(request.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().key()).isEqualTo(KEY);
    }

    @Test
    @DisplayName("사전 서명 URL에 TTL과 Content-Disposition을 전달한다")
    void offloadUrl_preservesTtlAndDisposition() throws Exception {
        Duration ttl = Duration.ofMinutes(5);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("여행 사진.png", StandardCharsets.UTF_8)
                .build();
        PresignedGetObjectRequest presigned = org.mockito.Mockito.mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://signed.example.test/object?sig=test"));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        URI url = storage.offloadUrl(KEY, ttl, disposition).orElseThrow();

        assertThat(url).isEqualTo(URI.create("https://signed.example.test/object?sig=test"));
        ArgumentCaptor<GetObjectPresignRequest> request =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(request.capture());
        assertThat(request.getValue().signatureDuration()).isEqualTo(ttl);
        assertThat(request.getValue().getObjectRequest().bucket()).isEqualTo(BUCKET);
        assertThat(request.getValue().getObjectRequest().key()).isEqualTo(KEY);
        assertThat(request.getValue().getObjectRequest().responseContentDisposition())
                .isEqualTo(disposition.toString());
    }

    @Test
    @DisplayName("경로 이동이 가능한 key는 S3 호출 전에 거부한다")
    void invalidKey_isRejectedBeforeS3Call() {
        assertThatThrownBy(() -> storage.open("profiles/../chat/secret.png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("파일 키가 올바르지 않습니다.");
    }
}
