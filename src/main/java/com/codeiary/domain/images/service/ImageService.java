package com.codeiary.domain.images.service;

import com.codeiary.domain.images.dto.request.ImagePresignRequest;
import com.codeiary.domain.images.dto.response.ImageUploadResponse;
import com.codeiary.domain.images.exception.ImageErrorCode;
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
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class ImageService {

    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final Duration URL_TTL = Duration.ofMinutes(5);

    private final S3Presigner presigner;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    @Value("${cloud.aws.s3.public-base-url}")
    private String imageBaseUrl;

    @Value("${cloud.aws.s3.endpoint:}")
    private String endpoint;

    public ImageUploadResponse presign(Long userId, ImagePresignRequest request) {
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
                .bucket(bucket.trim())
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

    public void validateImageUrl(String value) {
        if (!allowsImageUrl(value)) {
            throw new RestApiException(CommonErrorCode.INVALID_PARAMETER);
        }
    }

    private boolean allowsImageUrl(String value) {
        try {
            URI url = URI.create(value);
            if (url.getHost() == null || url.getUserInfo() != null) {
                return false;
            }
            if ("https".equalsIgnoreCase(url.getScheme())) {
                return true;
            }
            URI localEndpoint = StringUtils.hasText(endpoint) ? URI.create(endpoint) : null;
            return localEndpoint != null && "http".equals(localEndpoint.getScheme())
                    && "localhost".equals(localEndpoint.getHost())
                    && "http".equals(url.getScheme()) && "localhost".equals(url.getHost())
                    && url.getPort() == localEndpoint.getPort()
                    && (url.getPath().equals("/" + bucket) || url.getPath().startsWith("/" + bucket + "/"));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String publicBaseUrl() {
        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(imageBaseUrl)) {
            throw new RestApiException(ImageErrorCode.IMAGE_UPLOAD_UNAVAILABLE);
        }
        try {
            URI base = URI.create(imageBaseUrl.trim());
            if (!allowsImageUrl(base.toString())
                    || base.getQuery() != null || base.getFragment() != null) {
                throw new IllegalArgumentException("A valid public media URL is required");
            }
            return base.toString().replaceAll("/+$", "");
        } catch (IllegalArgumentException exception) {
            throw new RestApiException(ImageErrorCode.IMAGE_UPLOAD_UNAVAILABLE, exception);
        }
    }
}
