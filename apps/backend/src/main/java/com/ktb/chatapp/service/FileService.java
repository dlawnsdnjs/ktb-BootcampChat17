package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.PresignUploadRequest;
import com.ktb.chatapp.dto.PresignUploadResponse;
import com.ktb.chatapp.dto.ProfileImageResponse;
import com.ktb.chatapp.model.File;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;

public interface FileService {

    FileUploadResult uploadFile(MultipartFile file, String uploaderId);

    /**
     * 파일을 저장하고 <b>스토리지 key</b>({@code <subDirectory>/<name>})를 반환한다. URL 조립은 응답
     * 경계의 몫이므로 여기서는 하지 않는다.
     */
    String storeFile(MultipartFile file, String subDirectory);

    boolean deleteFile(String fileId, String requesterId);

    /**
     * 브라우저가 스토리지에 직접 업로드할 수 있는 URL을 발급한다.
     *
     * <p>로컬 저장소는 직접 업로드를 지원하지 않으며, S3 구현체만 이 계약을 재정의한다.
     */
    default PresignUploadResponse presignUpload(String email, PresignUploadRequest request) {
        throw directUploadNotSupported();
    }

    /** S3 직접 업로드가 끝난 채팅 첨부를 검증하고 메타데이터를 저장한다. */
    default File completeChatUpload(String email, String uploadId) {
        throw directUploadNotSupported();
    }

    /** S3 직접 업로드가 끝난 프로필 이미지를 검증하고 사용자에게 연결한다. */
    default ProfileImageResponse completeProfileUpload(String email, String uploadId) {
        throw directUploadNotSupported();
    }

    private DirectUploadException directUploadNotSupported() {
        return new DirectUploadException(HttpStatus.CONFLICT, "현재 파일 저장소는 직접 업로드를 지원하지 않습니다.");
    }
}
