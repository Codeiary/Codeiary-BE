package com.codeiary.domain.images.service;

import com.codeiary.domain.images.dto.request.ImagePresignRequest;
import com.codeiary.domain.images.dto.response.ImageUploadResponse;
import com.codeiary.domain.images.exception.ImageErrorCode;
import com.codeiary.domain.images.validation.ImageUrlValidator;
import com.codeiary.global.exception.CommonErrorCode;
import com.codeiary.global.exception.RestApiException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class ImageService {

    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Duration URL_TTL = Duration.ofMinutes(5);

    private final S3Presigner presigner;
    private final ImageUrlValidator imageUrls;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.s3.public-base-url}")
    private String imageBaseUrl;

    public ImageUploadResponse presign(Long userId, ImagePresignRequest request) {
        String contentType = request.contentType().trim().toLowerCase(Locale.ROOT);
        String extension = validateImage(contentType, request.contentLength());
        String publicBaseUrl = publicBaseUrl();
        String key = "images/" + userId + "/" + UUID.randomUUID() + "." + extension;
        try {
            var signed = presigner.presignPutObject(uploadRequest(key, contentType, request.contentLength()));
            return uploadResponse(signed, publicBaseUrl + "/" + key);
        } catch (SdkException exception) {
            throw new RestApiException(ImageErrorCode.IMAGE_UPLOAD_FAILED, exception);
        }
    }

    private String validateImage(String contentType, long contentLength) {
        if (contentLength > MAX_BYTES) {
            throw new RestApiException(CommonErrorCode.PAYLOAD_TOO_LARGE);
        }
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> throw new RestApiException(ImageErrorCode.INVALID_IMAGE);
        };
    }

    private PutObjectPresignRequest uploadRequest(String key, String contentType, long contentLength) {
        var putObject = PutObjectRequest.builder()
                .bucket(bucket.trim())
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .cacheControl("public,max-age=31536000,immutable")
                .serverSideEncryption(ServerSideEncryption.AES256)
                .ifNoneMatch("*")
                .build();
        return PutObjectPresignRequest.builder()
                .signatureDuration(URL_TTL)
                .putObjectRequest(putObject)
                .build();
    }

    private ImageUploadResponse uploadResponse(PresignedPutObjectRequest signed, String imageUrl) {
        var headers = new LinkedHashMap<String, String>();
        signed.signedHeaders().forEach((name, values) -> {
            if (!"host".equalsIgnoreCase(name) && !"content-length".equalsIgnoreCase(name)) {
                headers.put(name, String.join(",", values));
            }
        });
        return new ImageUploadResponse(signed.url().toString(), imageUrl, headers, signed.expiration());
    }

    private String publicBaseUrl() {
        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(imageBaseUrl)) {
            throw new RestApiException(ImageErrorCode.IMAGE_UPLOAD_UNAVAILABLE);
        }
        URI base;
        try {
            base = URI.create(imageBaseUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new RestApiException(ImageErrorCode.IMAGE_UPLOAD_UNAVAILABLE, exception);
        }
        if (!imageUrls.isAllowed(base.toString())
                || base.getQuery() != null || base.getFragment() != null) {
            throw new RestApiException(ImageErrorCode.IMAGE_UPLOAD_UNAVAILABLE);
        }
        return base.toString().replaceAll("/+$", "");
    }
}
