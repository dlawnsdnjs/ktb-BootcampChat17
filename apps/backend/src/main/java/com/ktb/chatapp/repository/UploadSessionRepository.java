package com.ktb.chatapp.repository;

import com.ktb.chatapp.model.UploadSession;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UploadSessionRepository extends MongoRepository<UploadSession, String> {
}
