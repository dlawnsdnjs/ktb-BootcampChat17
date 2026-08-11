package com.ktb.chatapp.dto;

import com.ktb.chatapp.model.UploadPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PresignUploadRequest(
        @NotNull UploadPurpose purpose,
        @NotBlank String originalName,
        @NotBlank String contentType,
        @Positive long size) {
}
