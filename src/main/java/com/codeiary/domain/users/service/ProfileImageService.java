package com.codeiary.domain.users.service;

import com.codeiary.domain.users.dto.response.ProfileImageResponse;
import com.codeiary.domain.users.exception.UserErrorCode;
import com.codeiary.global.config.MediaStorageProperties;
import com.codeiary.global.exception.CommonErrorCode;
import com.codeiary.global.exception.RestApiException;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

@Service
public class ProfileImageService {

    private static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_DIMENSION = 1024;

    private final S3Client s3;
    private final MediaStorageProperties properties;

    public ProfileImageService(S3Client s3, MediaStorageProperties properties) {
        this.s3 = s3;
        this.properties = properties;
    }

    public ProfileImageResponse upload(Long userId, MultipartFile profileImage) {
        if (userId == null || userId <= 0) {
            throw new RestApiException(CommonErrorCode.INVALID_PARAMETER);
        }
        if (profileImage == null || profileImage.isEmpty()
                || !"image/jpeg".equalsIgnoreCase(profileImage.getContentType())) {
            throw new RestApiException(UserErrorCode.INVALID_PROFILE_IMAGE);
        }
        requireAllowedSize(profileImage.getSize());
        String publicBaseUrl = publicBaseUrl();
        try {
            byte[] original;
            try (var input = profileImage.getInputStream()) {
                original = input.readNBytes(MAX_BYTES + 1);
            }
            requireAllowedSize(original.length);
            BufferedImage decoded = decodeJpeg(original);
            BufferedImage sanitized = new BufferedImage(
                    decoded.getWidth(), decoded.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = sanitized.createGraphics();
            try {
                graphics.drawImage(decoded, 0, 0, null);
            } finally {
                graphics.dispose();
            }
            var output = new ByteArrayOutputStream();
            if (!ImageIO.write(sanitized, "jpeg", output)) {
                throw new RestApiException(UserErrorCode.PROFILE_IMAGE_UPLOAD_FAILED);
            }
            byte[] jpeg = output.toByteArray();
            requireAllowedSize(jpeg.length);
            String key = "profiles/" + userId + "/" + UUID.randomUUID() + ".jpg";
            s3.putObject(PutObjectRequest.builder()
                            .bucket(properties.bucket().trim())
                            .key(key)
                            .contentType("image/jpeg")
                            .cacheControl("public, max-age=31536000, immutable")
                            .serverSideEncryption(ServerSideEncryption.AES256)
                            .build(),
                    RequestBody.fromBytes(jpeg));
            return new ProfileImageResponse(publicBaseUrl + "/" + key);
        } catch (IOException | SdkException exception) {
            throw new RestApiException(UserErrorCode.PROFILE_IMAGE_UPLOAD_FAILED, exception);
        }
    }

    private BufferedImage decodeJpeg(byte[] bytes) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new RestApiException(UserErrorCode.INVALID_PROFILE_IMAGE);
            }
            ImageReader reader = readers.next();
            try {
                if (!"JPEG".equalsIgnoreCase(reader.getFormatName())) {
                    throw new RestApiException(UserErrorCode.INVALID_PROFILE_IMAGE);
                }
                reader.setInput(input, true, true);
                // Read dimensions before decoding to bound pixel allocation.
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw new RestApiException(UserErrorCode.INVALID_PROFILE_IMAGE);
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new RestApiException(UserErrorCode.INVALID_PROFILE_IMAGE);
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new RestApiException(UserErrorCode.INVALID_PROFILE_IMAGE, exception);
        }
    }

    private void requireAllowedSize(long size) {
        if (size > MAX_BYTES) {
            throw new RestApiException(CommonErrorCode.PAYLOAD_TOO_LARGE);
        }
    }

    private String publicBaseUrl() {
        if (!StringUtils.hasText(properties.bucket()) || !StringUtils.hasText(properties.publicBaseUrl())) {
            throw new RestApiException(UserErrorCode.PROFILE_IMAGE_UPLOAD_UNAVAILABLE);
        }
        try {
            URI base = URI.create(properties.publicBaseUrl().trim());
            if (!"https".equalsIgnoreCase(base.getScheme()) || base.getHost() == null
                    || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
                throw new IllegalArgumentException("A public HTTPS media URL is required");
            }
            return base.toString().replaceAll("/+$", "");
        } catch (IllegalArgumentException exception) {
            throw new RestApiException(UserErrorCode.PROFILE_IMAGE_UPLOAD_UNAVAILABLE, exception);
        }
    }
}
