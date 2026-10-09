package com.codeiary.domain.image.validation;

import com.codeiary.global.exception.CommonErrorCode;
import com.codeiary.global.exception.RestApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;

class ImageUrlValidatorTest {

    private ImageUrlValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ImageUrlValidator();
        ReflectionTestUtils.setField(validator, "bucket", "codeiary-local");
        ReflectionTestUtils.setField(validator, "endpoint", "");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://img.example.com/photo.jpg",
            "http://localhost:9090/codeiary-local/images/1/photo.jpg"})
    @DisplayName("HTTPS 주소와 설정된 로컬 저장소 주소를 허용할 수 있다.")
    void allowImageUrl(String url) {
        // given
        ReflectionTestUtils.setField(validator, "endpoint", "http://localhost:9090");

        // when & then
        assertThatCode(() -> validator.validate(url)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:9090/codeiary-local/photo.jpg", "http://img.example.com/photo.jpg",
            "https://user:pass@img.example.com/photo.jpg", "not a URL"})
    @DisplayName("운영에서 허용하지 않는 이미지 주소를 거절할 수 있다.")
    void rejectInvalidImageUrl(String url) {
        // when
        Throwable error = catchThrowable(() -> validator.validate(url));

        // then
        assertThat(error).isInstanceOfSatisfying(RestApiException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:9091/codeiary-local/photo.jpg",
            "http://localhost:9090/other-bucket/photo.jpg", "http://localhost:9090/codeiary-local-other/photo.jpg",
            "http://example.com:9090/codeiary-local/photo.jpg"})
    @DisplayName("로컬에서도 설정된 저장소 밖의 HTTP 주소를 거절할 수 있다.")
    void rejectOtherLocalStorage(String url) {
        // given
        ReflectionTestUtils.setField(validator, "endpoint", "http://localhost:9090");

        // when
        Throwable error = catchThrowable(() -> validator.validate(url));

        // then
        assertThat(error).isInstanceOfSatisfying(RestApiException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_PARAMETER));
    }
}
