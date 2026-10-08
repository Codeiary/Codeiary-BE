package com.codeiary.global.config;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import static org.assertj.core.api.Assertions.assertThat;

class S3PresignerConfigTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("설정값으로 로컬과 AWS의 업로드 주소를 발급할 수 있다.")
    void configurePresigner(boolean local) {
        // given
        var runner = new ApplicationContextRunner().withUserConfiguration(S3PresignerConfig.class)
                .withPropertyValues("cloud.aws.s3.region=ap-northeast-2",
                        "cloud.aws.s3.access-key=test-access-key",
                        "cloud.aws.s3.secret-key=test-secret-key");
        if (local) runner = runner.withPropertyValues("cloud.aws.s3.endpoint=http://localhost:9090");

        // when
        runner.run(context -> {
            // then
            assertThat(context).hasSingleBean(S3Presigner.class);
            var request = PutObjectPresignRequest.builder().signatureDuration(Duration.ofMinutes(5))
                    .putObjectRequest(PutObjectRequest.builder().bucket("test-media").key("photo.jpg").build())
                    .build();
            String url = context.getBean(S3Presigner.class).presignPutObject(request).url().toString();
            assertThat(url).startsWith(local ? "http://localhost:9090/test-media/photo.jpg?"
                    : "https://test-media.s3.ap-northeast-2.amazonaws.com/photo.jpg?")
                    .contains("X-Amz-Credential=test-access-key%2F");
        });
    }
}
