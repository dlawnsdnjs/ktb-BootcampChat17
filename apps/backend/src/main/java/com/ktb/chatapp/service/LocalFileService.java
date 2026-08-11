package com.ktb.chatapp.service;

import com.ktb.chatapp.repository.FileRepository;
import com.ktb.chatapp.storage.StoragePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 로컬 파일 시스템 배포에서 선택되는 {@link FileService} 구현체. */
@Service
@ConditionalOnProperty(name = "file.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileService extends AbstractStorageFileService {

    public LocalFileService(StoragePort storagePort, FileRepository fileRepository) {
        super(storagePort, fileRepository);
    }
}
