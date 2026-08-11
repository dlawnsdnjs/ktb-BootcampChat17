package com.ktb.chatapp.dto;

import java.time.Instant;
import java.util.Map;

public record PresignUploadResponse(
        String uploadId,
        String uploadUrl,
        Instant expiresAt,
        Map<String, String> requiredHeaders) {
}
