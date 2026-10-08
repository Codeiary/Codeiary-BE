package com.codeiary.domain.images.service;

import com.codeiary.domain.images.dto.request.ImagePresignRequest;
import com.codeiary.domain.images.dto.response.ImageUploadResponse;
import com.codeiary.domain.images.exception.ImageErrorCode;
import com.codeiary.global.config.MediaStorageProperties;
import com.codeiary.global.exception.CommonErrorCode;
import com.codeiary.global.exception.RestApiException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class ImageService {

    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Duration URL_TTL = Duration.ofMinutes(5);

    private final S3Presigner presigner;
    private final MediaStorageProperties properties;

    public ImageService(S3Presigner presigner, MediaStorageProperties properties) {
        this.presigner = presigner;
        this.properties = properties;
    }

    public ImageUploadResponse presign(Long userId, ImagePresignRequest request) {
        if (userId == null || userId <= 0) {
            throw new RestApiException(CommonErrorCode.INVALID_PARAMETER);
        }
        if (request == null || request.contentLength() == null || request.contentLength() <= 0
                || request.contentType() == null) {
            throw new RestApiException(ImageErrorCode.INVALID_IMAGE);
        }
        if (request.contentLength() > MAX_BYTES) {
            throw new RestApiException(CommonErrorCode.PAYLOAD_TOO_LARGE);
        }
        String contentType = request.contentType().trim().toLowerCase(Locale.ROOT);
        String extension = switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> throw new RestApiException(ImageErrorCode.INVALID_IMAGE);
        };
        String publicBaseUrl = publicBaseUrl();
        String key = "images/" + userId + "/" + UUID.randomUUID() + "." + extension;
        var putObject = PutObjectRequest.builder()
                .bucket(properties.bucket().trim())
                .key(key)
                .contentType(contentType)
                .contentLength(request.contentLength())
                .cacheControl("public,max-age=31536000,immutable")
                .serverSideEncryption(ServerSideEncryption.AES256)
                .ifNoneMatch("*")
                .build();
        try {
            var signed = presigner.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(URL_TTL)
                    .putObjectRequest(putObject)
                    .build());
            var headers = new LinkedHashMap<String, String>();
            signed.signedHeaders().forEach((name, values) -> {
                if (!"host".equalsIgnoreCase(name) && !"content-length".equalsIgnoreCase(name)) {
                    headers.put(name, String.join(",", values));
                }
            });
            return new ImageUploadResponse(signed.url().toString(), publicBaseUrl + "/" + key,
                    headers, signed.expiration());
        } catch (SdkException exception) {
            throw new RestApiException(ImageErrorCode.IMAGE_UPLOAD_FAILED, exception);
        }
    }

    private String publicBaseUrl() {
        if (!StringUtils.hasText(properties.bucket()) || !StringUtils.hasText(properties.publicBaseUrl())) {
            throw new RestApiException(ImageErrorCode.IMAGE_UPLOAD_UNAVAILABLE);
        }
        try {
            URI base = URI.create(properties.publicBaseUrl().trim());
            if (!"https".equalsIgnoreCase(base.getScheme()) || base.getHost() == null
                    || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
                throw new IllegalArgumentException("A public HTTPS media URL is required");
            }
            return base.toString().replaceAll("/+$", "");
        } catch (IllegalArgumentException exception) {
            throw new RestApiException(ImageErrorCode.IMAGE_UPLOAD_UNAVAILABLE, exception);
        }
    }
}
