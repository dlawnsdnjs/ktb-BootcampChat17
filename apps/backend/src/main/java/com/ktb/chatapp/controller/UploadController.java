package com.ktb.chatapp.controller;

import com.ktb.chatapp.dto.PresignUploadRequest;
import com.ktb.chatapp.dto.StandardResponse;
import com.ktb.chatapp.service.DirectUploadException;
import com.ktb.chatapp.service.DirectUploadService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/uploads")
public class UploadController {
    private final DirectUploadService directUploadService;

    @PostMapping("/presign")
    public ResponseEntity<?> presign(
            Principal principal, @Valid @RequestBody PresignUploadRequest request) {
        try {
            return ResponseEntity.ok(directUploadService.presign(principal.getName(), request));
        } catch (DirectUploadException ex) {
            return ResponseEntity.status(ex.getStatus()).body(StandardResponse.error(ex.getMessage()));
        } catch (UsernameNotFoundException ex) {
            return ResponseEntity.status(404).body(StandardResponse.error(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(StandardResponse.error(ex.getMessage()));
        }
    }
}
