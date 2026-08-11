package com.ktb.chatapp.service;

import com.ktb.chatapp.dto.PresignUploadRequest;
import com.ktb.chatapp.dto.PresignUploadResponse;
import com.ktb.chatapp.dto.ProfileImageResponse;
import com.ktb.chatapp.model.File;
import com.ktb.chatapp.model.UploadPurpose;
import com.ktb.chatapp.model.UploadSession;
import com.ktb.chatapp.model.UploadStatus;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.FileRepository;
import com.ktb.chatapp.repository.UploadSessionRepository;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.storage.PresignedUpload;
import com.ktb.chatapp.storage.StorageKey;
import com.ktb.chatapp.storage.StoragePort;
import com.ktb.chatapp.storage.StoredObjectMetadata;
import com.ktb.chatapp.util.FileUtil;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectUploadService {
    static final Duration UPLOAD_URL_TTL = Duration.ofMinutes(5);
    static final Duration SESSION_TTL = Duration.ofHours(1);

    private final StoragePort storagePort;
    private final UploadSessionRepository uploadSessionRepository;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    public PresignUploadResponse presign(String email, PresignUploadRequest request) {
        User owner = userByEmail(email);
        validateRequest(request);

        String originalName = StringUtils.cleanPath(request.originalName());
        String safeName = FileUtil.generateSafeFileName(originalName);
        String key = request.purpose() == UploadPurpose.PROFILE_IMAGE
                ? StorageKey.profile(safeName)
                : StorageKey.chat(safeName);
        Instant now = Instant.now();
        UploadSession session = uploadSessionRepository.save(UploadSession.builder()
                .ownerId(owner.getId())
                .purpose(request.purpose())
                .key(key)
                .originalName(FileUtil.normalizeOriginalFilename(originalName))
                .contentType(request.contentType())
                .expectedSize(request.size())
                .status(UploadStatus.PENDING)
                .createdAt(now)
                .expiresAt(now.plus(SESSION_TTL))
                .build());

        try {
            PresignedUpload upload = storagePort
                    .presignUpload(key, request.contentType(), UPLOAD_URL_TTL)
                    .orElseThrow(() -> new DirectUploadException(
                            HttpStatus.CONFLICT, "현재 저장소는 직접 업로드를 지원하지 않습니다."));
            return new PresignUploadResponse(
                    session.getId(), upload.url().toString(), upload.expiresAt(), upload.requiredHeaders());
        } catch (RuntimeException ex) {
            try {
                uploadSessionRepository.deleteById(session.getId());
            } catch (RuntimeException cleanupFailure) {
                log.warn("URL 발급 실패 후 업로드 세션 정리 실패: uploadId={}", session.getId());
            }
            throw ex;
        }
    }

    public File completeChat(String email, String uploadId) {
        User owner = userByEmail(email);
        UploadSession session = claim(uploadId, owner.getId(), UploadPurpose.CHAT_ATTACHMENT);
        if (session.getStatus() == UploadStatus.COMPLETED) {
            return fileRepository.findById(session.getResultFileId())
                    .orElseThrow(() -> new DirectUploadException(
                            HttpStatus.CONFLICT, "완료된 업로드의 파일 정보를 찾을 수 없습니다."));
        }

        File savedFile = null;
        try {
            verifyStoredObject(session);
            savedFile = fileRepository.save(File.builder()
                    .filename(StorageKey.nameOf(session.getKey()))
                    .originalname(session.getOriginalName())
                    .mimetype(session.getContentType())
                    .size(session.getExpectedSize())
                    .path(session.getKey())
                    .user(owner.getId())
                    .uploadDate(LocalDateTime.now())
                    .build());
            session.setStatus(UploadStatus.COMPLETED);
            session.setResultFileId(savedFile.getId());
            uploadSessionRepository.save(session);
            return savedFile;
        } catch (RuntimeException ex) {
            if (savedFile != null) {
                try {
                    fileRepository.delete(savedFile);
                } catch (RuntimeException cleanupFailure) {
                    log.warn("업로드 완료 실패 후 파일 메타데이터 정리 실패: fileId={}", savedFile.getId());
                }
            }
            failAndDelete(session);
            throw ex;
        }
    }

    public ProfileImageResponse completeProfile(String email, String uploadId) {
        User owner = userByEmail(email);
        UploadSession session = claim(uploadId, owner.getId(), UploadPurpose.PROFILE_IMAGE);
        if (session.getStatus() == UploadStatus.COMPLETED) {
            return new ProfileImageResponse(true, "프로필 이미지가 업데이트되었습니다.",
                    session.getResultImageUrl());
        }

        String oldKey = owner.getProfileImage();
        LocalDateTime oldUpdatedAt = owner.getUpdatedAt();
        boolean userSaved = false;
        try {
            verifyStoredObject(session);
            owner.setProfileImage(session.getKey());
            owner.setUpdatedAt(LocalDateTime.now());
            userRepository.save(owner);
            userSaved = true;
            ProfileImageResponse response = ProfileImageResponse.updated(session.getKey());
            session.setStatus(UploadStatus.COMPLETED);
            session.setResultImageUrl(response.getImageUrl());
            uploadSessionRepository.save(session);
            if (oldKey != null && !oldKey.isBlank() && !oldKey.equals(session.getKey())) {
                deleteQuietly(oldKey);
            }
            return response;
        } catch (RuntimeException ex) {
            owner.setProfileImage(oldKey);
            owner.setUpdatedAt(oldUpdatedAt);
            if (userSaved) {
                try {
                    userRepository.save(owner);
                } catch (RuntimeException rollbackFailure) {
                    log.error("프로필 이미지 DB 롤백 실패: userId={}", owner.getId());
                }
            }
            failAndDelete(session);
            throw ex;
        }
    }

    private UploadSession claim(String uploadId, String ownerId, UploadPurpose purpose) {
        Instant now = Instant.now();
        Query query = Query.query(Criteria.where("id").is(uploadId)
                .and("ownerId").is(ownerId)
                .and("purpose").is(purpose)
                .and("status").is(UploadStatus.PENDING)
                .and("expiresAt").gt(now));
        UploadSession claimed = mongoTemplate.findAndModify(
                query,
                new Update().set("status", UploadStatus.COMPLETING),
                FindAndModifyOptions.options().returnNew(true),
                UploadSession.class);
        if (claimed != null) {
            return claimed;
        }

        UploadSession existing = uploadSessionRepository.findById(uploadId)
                .orElseThrow(() -> new DirectUploadException(HttpStatus.NOT_FOUND, "업로드 세션을 찾을 수 없습니다."));
        if (!ownerId.equals(existing.getOwnerId())) {
            throw new DirectUploadException(HttpStatus.FORBIDDEN, "업로드 세션에 접근할 권한이 없습니다.");
        }
        if (existing.getPurpose() != purpose) {
            throw new DirectUploadException(HttpStatus.BAD_REQUEST, "업로드 목적이 올바르지 않습니다.");
        }
        if (existing.getExpiresAt().isBefore(now)) {
            throw new DirectUploadException(HttpStatus.GONE, "업로드 세션이 만료되었습니다.");
        }
        if (existing.getStatus() == UploadStatus.COMPLETED) {
            return existing;
        }
        throw new DirectUploadException(HttpStatus.CONFLICT, "업로드 완료 처리가 이미 진행 중입니다.");
    }

    private void verifyStoredObject(UploadSession session) {
        StoredObjectMetadata metadata = storagePort.metadata(session.getKey())
                .orElseThrow(() -> new DirectUploadException(HttpStatus.BAD_REQUEST, "업로드된 객체를 찾을 수 없습니다."));
        if (metadata.size() != session.getExpectedSize()) {
            throw new DirectUploadException(HttpStatus.BAD_REQUEST, "업로드된 파일 크기가 요청과 다릅니다.");
        }
        if (!session.getContentType().equalsIgnoreCase(metadata.contentType())) {
            throw new DirectUploadException(HttpStatus.BAD_REQUEST, "업로드된 Content-Type이 요청과 다릅니다.");
        }
    }

    private void validateRequest(PresignUploadRequest request) {
        try {
            FileUtil.validateFileMetadata(request.originalName(), request.contentType(), request.size());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(ex.getMessage());
        }
        if (request.purpose() == UploadPurpose.PROFILE_IMAGE
                && !FileUtil.isImageContentType(request.contentType())) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }
    }

    private User userByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
    }

    private void failAndDelete(UploadSession session) {
        session.setStatus(UploadStatus.FAILED);
        try {
            uploadSessionRepository.save(session);
        } catch (RuntimeException saveFailure) {
            log.warn("업로드 실패 상태 저장 실패: uploadId={}", session.getId());
        }
        deleteQuietly(session.getKey());
    }

    private void deleteQuietly(String key) {
        try {
            storagePort.delete(key);
        } catch (RuntimeException cleanupFailure) {
            log.warn("업로드 객체 정리 실패: key={}", key);
        }
    }
}
