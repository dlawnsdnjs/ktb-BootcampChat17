package com.ktb.chatapp.storage;

import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.AbstractResource;
import org.springframework.http.ContentDisposition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "file.storage.type", havingValue = "s3")
public class S3Storage implements StoragePort {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;

    public S3Storage(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${file.storage.s3.bucket:}") String bucket) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalStateException("S3 저장소를 사용하려면 S3_BUCKET_NAME 설정이 필요합니다.");
        }
        this.bucket = bucket.trim();
    }

    @Override
    public StoredObject put(InputStream content, String key, String contentType, long size) {
        validateKey(key);
        if (content == null || size < 0) {
            throw new IllegalArgumentException("저장할 파일 정보가 올바르지 않습니다.");
        }

        PutObjectRequest.Builder request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentLength(size);
        if (contentType != null && !contentType.isBlank()) {
            request.contentType(contentType);
        }

        try {
            s3Client.putObject(request.build(), RequestBody.fromInputStream(content, size));
            return new StoredObject(key, size);
        } catch (RuntimeException ex) {
            throw storageFailure("저장");
        }
    }

    @Override
    public Optional<Resource> open(String key) {
        validateKey(key);
        try {
            HeadObjectResponse metadata = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            return Optional.of(new S3ObjectResource(key, metadata.contentLength()));
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return Optional.empty();
            }
            throw storageFailure("조회");
        } catch (RuntimeException ex) {
            throw storageFailure("조회");
        }
    }

    @Override
    public void delete(String key) {
        validateKey(key);
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (RuntimeException ex) {
            throw storageFailure("삭제");
        }
    }

    @Override
    public Optional<URI> offloadUrl(String key, Duration ttl, ContentDisposition disposition) {
        validateKey(key);
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("사전 서명 URL의 유효 시간이 올바르지 않습니다.");
        }

        GetObjectRequest.Builder objectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key);
        if (disposition != null) {
            objectRequest.responseContentDisposition(disposition.toString());
        }

        try {
            URI uri = s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(ttl)
                            .getObjectRequest(objectRequest.build())
                            .build())
                    .url()
                    .toURI();
            return Optional.of(uri);
        } catch (Exception ex) {
            throw storageFailure("접근 URL 발급");
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.startsWith("/") || key.contains("\\")) {
            throw new IllegalArgumentException("파일 키가 올바르지 않습니다.");
        }
        for (String segment : key.split("/")) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("파일 키가 올바르지 않습니다.");
            }
        }
    }

    private RuntimeException storageFailure(String operation) {
        return new RuntimeException("파일 저장소 " + operation + "에 실패했습니다.");
    }

    private final class S3ObjectResource extends AbstractResource {

        private final String key;
        private final long contentLength;

        private S3ObjectResource(String key, long contentLength) {
            this.key = key;
            this.contentLength = contentLength;
        }

        @Override
        public String getDescription() {
            return "S3 stored object";
        }

        @Override
        public long contentLength() {
            return contentLength;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            try {
                return s3Client.getObject(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build());
            } catch (RuntimeException ex) {
                throw new IOException("파일 저장소에서 객체를 읽지 못했습니다.");
            }
        }
    }
}
