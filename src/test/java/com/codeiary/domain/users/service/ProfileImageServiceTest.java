package com.codeiary.domain.users.service;

import com.codeiary.domain.users.exception.UserErrorCode;
import com.codeiary.domain.users.fixture.ProfileImageFixture;
import com.codeiary.domain.users.fixture.UserFixture;
import com.codeiary.global.config.MediaStorageProperties;
import com.codeiary.global.exception.CommonErrorCode;
import com.codeiary.global.exception.ErrorCode;
import com.codeiary.global.exception.RestApiException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class ProfileImageServiceTest {

    @Mock
    private S3Client s3;

    private ProfileImageService service;

    @BeforeEach
    void setUp() {
        service = new ProfileImageService(s3,
                new MediaStorageProperties("private-media", "https://img.example.com/", "ap-northeast-2"));
    }

    @Test
    @DisplayName("JPEG 메타데이터를 지우고 사용자 경로에 업로드할 수 있다.")
    void uploadSanitizedJpeg() throws Exception {
        // given
        String metadata = "private-camera-location";
        var file = ProfileImageFixture.jpegWithComment(metadata);

        // when
        var response = service.upload(UserFixture.ID, file);

        // then
        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        then(s3).should().putObject(request.capture(), body.capture());
        var upload = request.getValue();
        String prefix = "profiles/" + UserFixture.ID + "/";
        assertThat(upload.bucket()).isEqualTo("private-media");
        assertThat(upload.key()).startsWith(prefix).endsWith(".jpg");
        String filename = upload.key().substring(prefix.length(), upload.key().length() - 4);
        assertThat(UUID.fromString(filename).toString()).isEqualTo(filename);
        assertThat(upload.contentType()).isEqualTo("image/jpeg");
        assertThat(upload.serverSideEncryption()).isEqualTo(ServerSideEncryption.AES256);
        assertThat(upload.cacheControl()).isEqualTo("public, max-age=31536000, immutable");
        assertThat(upload.acl()).isNull();
        assertThat(response.profileImageUrl()).isEqualTo("https://img.example.com/" + upload.key());
        byte[] stored;
        try (var input = body.getValue().contentStreamProvider().newStream()) {
            stored = input.readAllBytes();
        }
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain(metadata);
        var image = ImageIO.read(new ByteArrayInputStream(stored));
        assertThat(image.getWidth()).isEqualTo(16);
        assertThat(image.getHeight()).isEqualTo(12);
    }

    @ParameterizedTest
    @MethodSource("invalidImages")
    @DisplayName("빈 파일과 위장 형식 및 허용 크기를 넘는 이미지를 거절할 수 있다.")
    void rejectInvalidImage(MultipartFile file) {
        // when
        Throwable error = catchThrowable(() -> service.upload(UserFixture.ID, file));

        // then
        assertError(error, UserErrorCode.INVALID_PROFILE_IMAGE);
        then(s3).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("1MiB를 넘는 업로드를 거절할 수 있다.")
    void rejectLargeFile() {
        // given
        var file = new MockMultipartFile("profileImage", "large.jpg", "image/jpeg",
                new byte[1024 * 1024 + 1]);

        // when
        Throwable error = catchThrowable(() -> service.upload(UserFixture.ID, file));

        // then
        assertError(error, CommonErrorCode.PAYLOAD_TOO_LARGE);
        then(s3).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @MethodSource("unavailableStorage")
    @DisplayName("저장소와 HTTPS 공개 주소가 준비되지 않은 업로드를 거절할 수 있다.")
    void rejectUnavailableStorage(String bucket, String publicBaseUrl) throws Exception {
        // given
        service = new ProfileImageService(s3, new MediaStorageProperties(bucket, publicBaseUrl, "ap-northeast-2"));
        var file = ProfileImageFixture.jpeg();

        // when
        Throwable error = catchThrowable(() -> service.upload(UserFixture.ID, file));

        // then
        assertError(error, UserErrorCode.PROFILE_IMAGE_UPLOAD_UNAVAILABLE);
        then(s3).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("S3 업로드 실패를 일시적인 서비스 오류로 반환할 수 있다.")
    void handleStorageFailure() throws Exception {
        // given
        var file = ProfileImageFixture.jpeg();
        given(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .willThrow(SdkClientException.create("storage unavailable"));

        // when
        Throwable error = catchThrowable(() -> service.upload(UserFixture.ID, file));

        // then
        assertError(error, UserErrorCode.PROFILE_IMAGE_UPLOAD_FAILED);
    }

    @Test
    @DisplayName("업로드 파일 읽기 실패를 일시적인 서비스 오류로 반환할 수 있다.")
    void handleFileReadFailure() throws Exception {
        // given
        MultipartFile file = mock(MultipartFile.class);
        given(file.getContentType()).willReturn("image/jpeg");
        given(file.getInputStream()).willThrow(new IOException("temporary file unavailable"));

        // when
        Throwable error = catchThrowable(() -> service.upload(UserFixture.ID, file));

        // then
        assertError(error, UserErrorCode.PROFILE_IMAGE_UPLOAD_FAILED);
        then(s3).shouldHaveNoInteractions();
    }

    private static Stream<MultipartFile> invalidImages() throws IOException {
        return Stream.of(
                null,
                new MockMultipartFile("profileImage", "empty.jpg", "image/jpeg", new byte[0]),
                new MockMultipartFile("profileImage", "profile.png", "image/png",
                        ProfileImageFixture.imageBytes("jpeg", 16, 12)),
                new MockMultipartFile("profileImage", "spoof.jpg", "image/jpeg",
                        ProfileImageFixture.imageBytes("png", 16, 12)),
                new MockMultipartFile("profileImage", "broken.jpg", "image/jpeg", new byte[]{1, 2, 3}),
                new MockMultipartFile("profileImage", "wide.jpg", "image/jpeg",
                        ProfileImageFixture.imageBytes("jpeg", 1025, 1)));
    }

    private static Stream<Arguments> unavailableStorage() {
        return Stream.of(
                Arguments.of("", "https://img.example.com"),
                Arguments.of("private-media", null),
                Arguments.of("private-media", "http://img.example.com"),
                Arguments.of("private-media", "https://img.example.com?destination=other"));
    }

    private void assertError(Throwable error, ErrorCode code) {
        assertThat(error).isInstanceOfSatisfying(RestApiException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
