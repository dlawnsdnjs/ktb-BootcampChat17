package com.ktb.chatapp.controller;

import com.ktb.chatapp.dto.ProfileImageResponse;
import com.ktb.chatapp.dto.StandardResponse;
import com.ktb.chatapp.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 로컬 저장소에서만 제공하는 Backend 경유 프로필 이미지 업로드 API. */
@Tag(name = "사용자 (Users)", description = "로컬 프로필 이미지 업로드 API")
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users")
@ConditionalOnProperty(name = "file.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalProfileImageUploadController {

    private final UserService userService;

    @Operation(summary = "로컬 프로필 이미지 업로드", description = "로컬 저장소 모드에서만 이미지를 업로드합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "이미지 업로드 성공",
            content = @Content(schema = @Schema(implementation = ProfileImageResponse.class))),
        @ApiResponse(responseCode = "400", description = "잘못된 파일 형식",
            content = @Content(schema = @Schema(implementation = StandardResponse.class),
                examples = @ExampleObject(value = "{\"success\":false,\"message\":\"지원하지 않는 파일 형식입니다.\"}"))),
        @ApiResponse(responseCode = "401", description = "인증 실패",
            content = @Content(schema = @Schema(implementation = StandardResponse.class))),
        @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음",
            content = @Content(schema = @Schema(implementation = StandardResponse.class))),
        @ApiResponse(responseCode = "413", description = "파일 크기 초과",
            content = @Content(schema = @Schema(implementation = StandardResponse.class))),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류",
            content = @Content(schema = @Schema(implementation = StandardResponse.class)))
    })
    @PostMapping("/profile-image")
    public ResponseEntity<?> uploadProfileImage(
            Principal principal, @RequestParam("profileImage") MultipartFile file) {
        try {
            return ResponseEntity.ok(userService.uploadProfileImage(principal.getName(), file));
        } catch (UsernameNotFoundException ex) {
            log.error("프로필 이미지 업로드 실패 - 사용자 없음: {}", ex.getMessage());
            return ResponseEntity.status(404)
                    .body(StandardResponse.error("사용자를 찾을 수 없습니다."));
        } catch (IllegalArgumentException ex) {
            log.error("프로필 이미지 업로드 실패 - 잘못된 입력: {}", ex.getMessage());
            return ResponseEntity.badRequest().body(StandardResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            log.error("프로필 이미지 업로드 중 오류 발생: {}", ex.getMessage(), ex);
            return ResponseEntity.internalServerError()
                    .body(StandardResponse.error("이미지 업로드 중 오류가 발생했습니다."));
        }
    }
}
