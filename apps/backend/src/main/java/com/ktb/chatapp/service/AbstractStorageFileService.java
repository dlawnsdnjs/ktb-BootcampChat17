package com.ktb.chatapp.service;

import com.ktb.chatapp.model.File;
import com.ktb.chatapp.repository.FileRepository;
import com.ktb.chatapp.storage.StorageKey;
import com.ktb.chatapp.storage.StoragePort;
import com.ktb.chatapp.util.FileUtil;
import java.io.IOException;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 스토리지 종류와 무관한 파일 메타데이터 처리 규칙을 공유한다.
 *
 * <p>로컬과 S3 구현체는 빈 선택 경계만 분리하고, 파일명 정규화·key 규약·DB 보상 삭제는 반드시 같은
 * 동작을 유지한다.
 */
@Slf4j
abstract class AbstractStorageFileService implements FileService {

    private final StoragePort storagePort;
    private final FileRepository fileRepository;

    protected AbstractStorageFileService(StoragePort storagePort, FileRepository fileRepository) {
        this.storagePort = storagePort;
        this.fileRepository = fileRepository;
    }

    @Override
    public FileUploadResult uploadFile(MultipartFile file, String uploaderId) {
        try {
            FileUtil.validateFile(file);

            String originalFilename = cleanedOriginalFilename(file);
            String safeFileName = FileUtil.generateSafeFileName(originalFilename);
            String key = StorageKey.chat(safeFileName);
            storagePort.put(file.getInputStream(), key, file.getContentType(), file.getSize());

            File fileEntity = File.builder()
                    .filename(safeFileName)
                    .originalname(FileUtil.normalizeOriginalFilename(originalFilename))
                    .mimetype(file.getContentType())
                    .size(file.getSize())
                    .path(key)
                    .user(uploaderId)
                    .uploadDate(LocalDateTime.now())
                    .build();

            File savedFile;
            try {
                savedFile = fileRepository.save(fileEntity);
            } catch (RuntimeException ex) {
                deleteStoredObjectQuietly(key);
                throw ex;
            }

            log.info("파일 저장 완료: key={}", key);
            return FileUploadResult.builder()
                    .success(true)
                    .file(savedFile)
                    .build();
        } catch (Exception ex) {
            log.error("파일 업로드 처리 실패: {}", ex.getMessage(), ex);
            throw new RuntimeException("파일 업로드에 실패했습니다: " + ex.getMessage(), ex);
        }
    }

    @Override
    public String storeFile(MultipartFile file, String subDirectory) {
        try {
            FileUtil.validateFile(file);

            String originalFilename = cleanedOriginalFilename(file);
            String safeFileName = FileUtil.generateSafeFileName(originalFilename);
            String key = hasText(subDirectory)
                    ? subDirectory.trim() + "/" + safeFileName
                    : safeFileName;

            storagePort.put(file.getInputStream(), key, file.getContentType(), file.getSize());
            log.info("파일 저장 완료: key={}", key);
            return key;
        } catch (IOException ex) {
            log.error("파일 저장 실패: {}", ex.getMessage(), ex);
            throw new RuntimeException("파일 저장에 실패했습니다: " + ex.getMessage(), ex);
        }
    }

    @Override
    public boolean deleteFile(String fileId, String requesterId) {
        try {
            File fileEntity = fileRepository.findById(fileId)
                    .orElseThrow(() -> new RuntimeException("파일을 찾을 수 없습니다."));

            if (!fileEntity.getUser().equals(requesterId)) {
                throw new RuntimeException("파일을 삭제할 권한이 없습니다.");
            }

            storagePort.delete(fileEntity.getPath());
            fileRepository.delete(fileEntity);

            log.info("파일 삭제 완료: {} (사용자: {})", fileId, requesterId);
            return true;
        } catch (Exception ex) {
            log.error("파일 삭제 실패: {}", ex.getMessage(), ex);
            throw new RuntimeException("파일 삭제 중 오류가 발생했습니다.", ex);
        }
    }

    private String cleanedOriginalFilename(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        return StringUtils.cleanPath(originalFilename != null ? originalFilename : "file");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void deleteStoredObjectQuietly(String key) {
        try {
            storagePort.delete(key);
        } catch (RuntimeException cleanupFailure) {
            log.warn("DB 저장 실패 후 업로드 객체 정리에 실패했습니다: key={}", key);
        }
    }
}
