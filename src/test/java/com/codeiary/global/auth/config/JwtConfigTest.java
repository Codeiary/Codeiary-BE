package com.codeiary.global.auth.config;

import com.codeiary.global.auth.fixture.JwtFixture;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class JwtConfigTest {

    @Test
    @DisplayName("운영 환경에서 JWT 키를 필수로 설정할 수 있다.")
    void requireProductionKey() {
        // given
        var config = new JwtConfig();
        var properties = JwtFixture.properties();
        var production = JwtFixture.environment("prod");
        var mixedProfiles = JwtFixture.environment("prod", "local");

        // when
        Throwable productionError = catchThrowable(() -> config.jwtSigningKey(properties, production));
        Throwable mixedProfileError = catchThrowable(() -> config.jwtSigningKey(properties, mixedProfiles));

        // then
        assertThat(productionError).isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
        assertThat(mixedProfileError).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("잘못된 JWT 키를 거절할 수 있다.")
    void rejectInvalidKey() {
        // given
        var secrets = List.of("invalid-base64!", JwtFixture.base64Secret(16));
        var config = new JwtConfig();
        var environment = JwtFixture.environment("prod");

        // when
        var errors = secrets.stream().map(secret -> catchThrowable(
                () -> config.jwtSigningKey(JwtFixture.properties(secret), environment))).toList();

        // then
        assertThat(errors).allSatisfy(error -> assertThat(error).isInstanceOf(IllegalStateException.class));
    }

    @Test
    @DisplayName("운영 키를 유지하고 로컬 키를 생성할 수 있다.")
    void generateSigningKey() {
        // given
        String encoded = JwtFixture.base64Secret(32);
        var config = new JwtConfig();
        var properties = JwtFixture.properties(encoded);
        var localProperties = JwtFixture.properties();
        var production = JwtFixture.environment("prod");
        var local = JwtFixture.environment("local");

        // when
        var productionKey = config.jwtSigningKey(properties, production);
        var firstLocalKey = config.jwtSigningKey(localProperties, local);
        var secondLocalKey = config.jwtSigningKey(localProperties, local);

        // then
        assertThat(productionKey.getEncoded()).isEqualTo(Base64.getDecoder().decode(encoded));
        assertThat(firstLocalKey.getEncoded()).hasSize(32).isNotEqualTo(secondLocalKey.getEncoded());
    }
}
