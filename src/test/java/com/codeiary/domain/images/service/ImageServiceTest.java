package com.codeiary.domain.images.service;

import com.codeiary.domain.images.dto.request.ImagePresignRequest;
import com.codeiary.domain.images.exception.ImageErrorCode;
import com.codeiary.domain.users.fixture.UserFixture;
import com.codeiary.global.exception.CommonErrorCode;
import com.codeiary.global.exception.ErrorCode;
import com.codeiary.global.exception.RestApiException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.spy;

class ImageServiceTest {

    private S3Presigner presigner;
    private ImageService service;

    @BeforeEach
    void setUp() {
        presigner = spy(S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                .build());
        service = imageService("private-media", "https://img.example.com/", "");
    }

    @AfterEach
    void tearDown() {
        presigner.close();
    }

    @ParameterizedTest
    @CsvSource({"image/jpeg, jpg, 1", "image/png, png, 10485760"})
    @DisplayName("파일 형식과 정확한 용량을 서명한 5분 업로드 주소를 발급할 수 있다.")
    void presignUpload(String contentType, String extension, long length) {
        // given
        var request = new ImagePresignRequest(contentType, length);
        Instant before = Instant.now();

        // when
        var response = service.presign(UserFixture.ID, request);

        // then
        Instant after = Instant.now();
        var signedRequest = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        then(presigner).should().presignPutObject(signedRequest.capture());
        var put = signedRequest.getValue().putObjectRequest();
        assertThat(signedRequest.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(put.bucket()).isEqualTo("private-media");
        assertThat(put.contentLength()).isEqualTo(length);
        assertThat(put.contentType()).isEqualTo(contentType);
        assertThat(put.serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);
        assertThat(put.ifNoneMatch()).isEqualTo("*");
        assertThat(put.acl()).isNull();
        String prefix = "images/" + UserFixture.ID + "/";
        assertThat(put.key()).startsWith(prefix).endsWith("." + extension);
        String filename = put.key().substring(prefix.length(), put.key().length() - 4);
        assertThat(UUID.fromString(filename).toString()).isEqualTo(filename);
        URI uploadUrl = URI.create(response.uploadUrl());
        assertThat(uploadUrl.getScheme()).isEqualTo("https");
        assertThat(uploadUrl.getHost()).isEqualTo("private-media.s3.ap-northeast-2.amazonaws.com");
        assertThat(uploadUrl.getPath()).isEqualTo("/" + put.key());
        String query = URLDecoder.decode(uploadUrl.getRawQuery(), StandardCharsets.UTF_8);
        assertThat(query).contains("X-Amz-Expires=300", "content-length", "content-type", "if-none-match");
        assertThat(response.headers())
                .containsEntry("content-type", contentType)
                .containsEntry("cache-control", "public,max-age=31536000,immutable")
                .containsEntry("x-amz-server-side-encryption", "AES256")
                .containsEntry("if-none-match", "*")
                .doesNotContainKeys("host", "content-length");
        assertThat(response.imageUrl()).isEqualTo("https://img.example.com/" + put.key());
        assertThat(response.expiresAt()).isBetween(before.plusSeconds(299), after.plusSeconds(301));
    }

    @Test
    @DisplayName("로컬 저장소의 이미지 주소를 반환할 수 있다.")
    void allowLocalImageUrl() {
        // given
        service = imageService("codeiary-local",
                "http://localhost:9090/codeiary-local", "http://localhost:9090");

        // when
        var response = service.presign(UserFixture.ID, new ImagePresignRequest("image/png", 10L));

        // then
        assertThat(response.imageUrl()).startsWith("http://localhost:9090/codeiary-local/images/");
    }

    @Test
    @DisplayName("같은 사용자의 요청마다 다른 이미지 경로를 발급할 수 있다.")
    void createUniqueKeys() {
        // given
        var request = new ImagePresignRequest("image/png", 100L);

        // when
        var first = service.presign(UserFixture.ID, request);
        var second = service.presign(UserFixture.ID, request);

        // then
        assertThat(first.imageUrl()).isNotEqualTo(second.imageUrl());
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/gif", "image/webp"})
    @DisplayName("허용하지 않는 이미지 형식을 거절할 수 있다.")
    void rejectUnsupportedImage(String contentType) {
        // given
        var request = new ImagePresignRequest(contentType, 1L);

        // when
        Throwable error = catchThrowable(() -> service.presign(UserFixture.ID, request));

        // then
        assertError(error, ImageErrorCode.INVALID_IMAGE);
        then(presigner).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("10MiB를 넘는 용량은 서명하지 않고 413 오류로 거절할 수 있다.")
    void rejectLargeFile() {
        // given
        var request = new ImagePresignRequest("image/jpeg", 10L * 1024 * 1024 + 1);

        // when
        Throwable error = catchThrowable(() -> service.presign(UserFixture.ID, request));

        // then
        assertError(error, CommonErrorCode.PAYLOAD_TOO_LARGE);
        then(presigner).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @MethodSource("unavailableStorage")
    @DisplayName("저장소와 HTTPS 공개 주소가 준비되지 않은 요청을 거절할 수 있다.")
    void rejectUnavailableStorage(String bucket, String publicBaseUrl) {
        // given
        service = imageService(bucket, publicBaseUrl, "");

        // when
        Throwable error = catchThrowable(() -> service.presign(UserFixture.ID, new ImagePresignRequest("image/png", 1L)));

        // then
        assertError(error, ImageErrorCode.IMAGE_UPLOAD_UNAVAILABLE);
        then(presigner).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("자격 증명을 읽지 못해 서명에 실패하면 서비스 오류로 처리할 수 있다.")
    void handlePresignFailure() {
        // given
        willThrow(SdkClientException.create("credentials unavailable"))
                .given(presigner).presignPutObject(any(PutObjectPresignRequest.class));

        // when
        Throwable error = catchThrowable(() -> service.presign(UserFixture.ID, new ImagePresignRequest("image/png", 1L)));

        // then
        assertError(error, ImageErrorCode.IMAGE_UPLOAD_FAILED);
    }

    @Test
    @DisplayName("HTTPS 주소와 로컬 저장소 이미지 주소를 허용할 수 있다.")
    void allowProfileImageUrls() {
        // given
        service = imageService("codeiary-local",
                "http://localhost:9090/codeiary-local", "http://localhost:9090");

        // when & then
        assertThatCode(() -> service.validateImageUrl("https://img.example.com/photo.jpg")).doesNotThrowAnyException();
        assertThatCode(() -> service.validateImageUrl("http://localhost:9090/codeiary-local/images/1/photo.jpg"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:9090/codeiary-local/photo.jpg", "http://img.example.com/photo.jpg",
            "https://user:pass@img.example.com/photo.jpg", "not a URL"})
    @DisplayName("운영에서 허용하지 않는 프로필 이미지 주소를 거절할 수 있다.")
    void rejectInvalidProfileImageUrl(String url) {
        // when
        var error = catchThrowable(() -> service.validateImageUrl(url));

        // then
        assertError(error, CommonErrorCode.INVALID_PARAMETER);
    }

    private static Stream<Arguments> unavailableStorage() {
        return Stream.of(
                Arguments.of("codeiary-local", "http://localhost:9090/codeiary-local"),
                Arguments.of("", "https://img.example.com"),
                Arguments.of("private-media", null),
                Arguments.of("private-media", "http://img.example.com"),
                Arguments.of("private-media", "https://user:pass@img.example.com"),
                Arguments.of("private-media", "https://img.example.com?destination=other"),
                Arguments.of("private-media", "https://img.example.com#fragment"));
    }

    private ImageService imageService(String bucket, String publicBaseUrl, String endpoint) {
        var imageService = new ImageService(presigner);
        ReflectionTestUtils.setField(imageService, "bucket", bucket);
        ReflectionTestUtils.setField(imageService, "imageBaseUrl", publicBaseUrl);
        ReflectionTestUtils.setField(imageService, "endpoint", endpoint);
        return imageService;
    }

    private void assertError(Throwable error, ErrorCode code) {
        assertThat(error).isInstanceOfSatisfying(RestApiException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
