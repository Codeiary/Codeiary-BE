package com.codeiary.domain.image.controller;

import com.codeiary.domain.image.dto.request.ImagePresignRequest;
import com.codeiary.domain.image.dto.response.ImageUploadResponse;
import com.codeiary.domain.image.service.ImageService;
import com.codeiary.domain.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@Tag(name = "이미지")
@SecurityRequirement(name = "cookieAuth")
public class ImageController {
    private final ImageService imageService;

    @PostMapping(value = "/presigned-url", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "이미지 업로드 URL 발급", description = "10MiB 이하 JPG·PNG의 S3 PUT URL을 발급합니다. 유효시간은 5분이며 반환된 headers와 파일 본문으로 S3에 직접 업로드한 후 imageUrl을 사용합니다.")
    public ResponseEntity<ImageUploadResponse> presign(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ImagePresignRequest request
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(imageService.presign(user.getId(), request));
    }
}
