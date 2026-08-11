package com.ktb.chatapp.controller;

import com.ktb.chatapp.dto.StandardResponse;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.service.FileService;
import com.ktb.chatapp.service.FileUploadResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
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

/** 로컬 저장소에서만 제공하는 Backend 경유 업로드 호환 API. */
@Tag(name = "파일 (Files)", description = "로컬 파일 업로드 API")
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/files")
@ConditionalOnProperty(name = "file.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileUploadController {

    private final FileService fileService;
    private final UserRepository userRepository;

    @Operation(summary = "로컬 파일 업로드", description = "로컬 저장소 모드에서만 파일을 업로드합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "파일 업로드 성공"),
        @ApiResponse(responseCode = "400", description = "잘못된 파일",
            content = @Content(schema = @Schema(implementation = StandardResponse.class))),
        @ApiResponse(responseCode = "401", description = "인증 실패",
            content = @Content(schema = @Schema(implementation = StandardResponse.class))),
        @ApiResponse(responseCode = "413", description = "파일 크기 초과",
            content = @Content(schema = @Schema(implementation = StandardResponse.class))),
        @ApiResponse(responseCode = "500", description = "서버 내부 오류",
            content = @Content(schema = @Schema(implementation = StandardResponse.class)))
    })
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @Parameter(description = "업로드할 파일") @RequestParam("file") MultipartFile file,
            Principal principal) {
        try {
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException(
                            "User not found: " + principal.getName()));

            FileUploadResult result = fileService.uploadFile(file, user.getId());
            if (!result.isSuccess()) {
                return ResponseEntity.internalServerError()
                        .body(StandardResponse.error("파일 업로드에 실패했습니다."));
            }

            Map<String, Object> fileData = new HashMap<>();
            fileData.put("_id", result.getFile().getId());
            fileData.put("filename", result.getFile().getFilename());
            fileData.put("originalname", result.getFile().getOriginalname());
            fileData.put("mimetype", result.getFile().getMimetype());
            fileData.put("size", result.getFile().getSize());
            fileData.put("uploadDate", result.getFile().getUploadDate());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "파일 업로드 성공");
            response.put("file", fileData);
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("로컬 파일 업로드 중 에러 발생", ex);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "파일 업로드 중 오류가 발생했습니다.");
            response.put("error", ex.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
