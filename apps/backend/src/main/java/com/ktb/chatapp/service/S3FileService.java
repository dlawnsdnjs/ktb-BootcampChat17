package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.PresignUploadRequest;
import com.ktb.chatapp.dto.PresignUploadResponse;
import com.ktb.chatapp.dto.ProfileImageResponse;
import com.ktb.chatapp.model.File;
import com.ktb.chatapp.repository.FileRepository;
import com.ktb.chatapp.storage.StoragePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * S3 배포에서 선택되는 {@link FileService} 구현체.
 *
 * <p>브라우저 직접 업로드 요청은 Presigned URL 워크플로에 위임한다. S3 모드에서 Backend가
 * 파일 바이트를 받는 multipart 경로는 허용하지 않는다.
 */
@Service
@ConditionalOnProperty(name = "file.storage.type", havingValue = "s3")
public class S3FileService extends AbstractStorageFileService {

    private DirectUploadService directUploadService;

    public S3FileService(StoragePort storagePort, FileRepository fileRepository) {
        super(storagePort, fileRepository);
    }

    /**
     * 기존 생성자 기반 단위 테스트와 가벼운 컨텍스트 테스트를 유지하면서, 실제 애플리케이션에서는
     * Presigned URL 워크플로를 주입한다.
     */
    @Autowired(required = false)
    void configureDirectUploadService(DirectUploadService directUploadService) {
        this.directUploadService = directUploadService;
    }

    @Override
    public FileUploadResult uploadFile(MultipartFile file, String uploaderId) {
        throw multipartNotSupported();
    }

    @Override
    public String storeFile(MultipartFile file, String subDirectory) {
        throw multipartNotSupported();
    }

    @Override
    public PresignUploadResponse presignUpload(String email, PresignUploadRequest request) {
        return directUploadWorkflow().presign(email, request);
    }

    @Override
    public File completeChatUpload(String email, String uploadId) {
        return directUploadWorkflow().completeChat(email, uploadId);
    }

    @Override
    public ProfileImageResponse completeProfileUpload(String email, String uploadId) {
        return directUploadWorkflow().completeProfile(email, uploadId);
    }

    private DirectUploadService directUploadWorkflow() {
        if (directUploadService == null) {
            throw new DirectUploadException(
                    HttpStatus.CONFLICT, "S3 직접 업로드 서비스가 구성되지 않았습니다.");
        }
        return directUploadService;
    }

    private DirectUploadException multipartNotSupported() {
        return new DirectUploadException(
                HttpStatus.CONFLICT, "S3 모드에서는 Presigned URL 직접 업로드만 지원합니다.");
    }
}
