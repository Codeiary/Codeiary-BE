package com.codeiary.global.security.dto.adapter;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleOAuth2ResponseTest {

    @ParameterizedTest
    @MethodSource("emailClaims")
    @DisplayName("Google 응답을 공통 이메일 인증과 소유권 정보로 변환할 수 있다.")
    void mapEmailClaims(String email, Object verified, Object hostedDomain,
                        boolean expectedVerified, boolean expectedAuthoritative) {
        // given
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("email", email);
        attributes.put("email_verified", verified);
        attributes.put("hd", hostedDomain);
        var response = new GoogleOAuth2Response(attributes);

        // when
        boolean emailVerified = response.isEmailVerified();
        boolean emailAuthoritative = response.isEmailAuthoritative();

        // then
        assertThat(emailVerified).isEqualTo(expectedVerified);
        assertThat(emailAuthoritative).isEqualTo(expectedAuthoritative);
    }

    private static Stream<Arguments> emailClaims() {
        return Stream.of(
                Arguments.of("owner@gmail.com", true, null, true, true),
                Arguments.of("Owner@GMAIL.COM", true, null, true, true),
                Arguments.of("owner@company.com", true, "company.com", true, true),
                Arguments.of("owner@company.com", true, null, true, false),
                Arguments.of("owner@company.com", true, " ", true, false),
                Arguments.of("owner@gmail.com", false, null, false, false),
                Arguments.of("owner@gmail.com", "true", null, false, false),
                Arguments.of("owner@company.com", false, "company.com", false, false),
                Arguments.of(null, true, "company.com", true, false));
    }
}
