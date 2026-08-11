package com.ktb.chatapp.controller;

import com.ktb.chatapp.dto.CompleteUploadRequest;
import com.ktb.chatapp.dto.StandardResponse;
import com.ktb.chatapp.model.File;
import com.ktb.chatapp.service.DirectUploadException;
import com.ktb.chatapp.service.FileService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DirectUploadCompletionController {
    private final FileService fileService;

    @PostMapping("/api/files/upload/complete")
    public ResponseEntity<?> completeChat(
            @Valid @RequestBody CompleteUploadRequest request, Principal principal) {
        try {
            return chatSuccess(fileService.completeChatUpload(principal.getName(), request.uploadId()));
        } catch (DirectUploadException ex) {
            return ResponseEntity.status(ex.getStatus()).body(StandardResponse.error(ex.getMessage()));
        } catch (UsernameNotFoundException ex) {
            return ResponseEntity.status(404).body(StandardResponse.error(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(StandardResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/api/users/profile-image/complete")
    public ResponseEntity<?> completeProfile(
            @Valid @RequestBody CompleteUploadRequest request, Principal principal) {
        try {
            return ResponseEntity.ok(
                    fileService.completeProfileUpload(principal.getName(), request.uploadId()));
        } catch (DirectUploadException ex) {
            return ResponseEntity.status(ex.getStatus()).body(StandardResponse.error(ex.getMessage()));
        } catch (UsernameNotFoundException ex) {
            return ResponseEntity.status(404).body(StandardResponse.error(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(StandardResponse.error(ex.getMessage()));
        }
    }

    private ResponseEntity<?> chatSuccess(File file) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "파일 업로드 성공");
        Map<String, Object> fileData = new HashMap<>();
        fileData.put("_id", file.getId());
        fileData.put("filename", file.getFilename());
        fileData.put("originalname", file.getOriginalname());
        fileData.put("mimetype", file.getMimetype());
        fileData.put("size", file.getSize());
        fileData.put("uploadDate", file.getUploadDate());
        response.put("file", fileData);
        return ResponseEntity.ok(response);
    }
}
