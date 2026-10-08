package com.codeiary.global.security.handler;

import com.codeiary.domain.users.entity.enums.OAuthProvider;
import com.codeiary.domain.users.entity.User;
import com.codeiary.domain.users.exception.UserErrorCode;
import com.codeiary.domain.users.fixture.UserFixture;
import com.codeiary.global.exception.RestApiException;
import com.codeiary.domain.auth.dto.AuthInfo;
import com.codeiary.global.security.dto.oauth2user.CustomOAuth2User;
import com.codeiary.domain.auth.service.OAuthAccountService;
import com.codeiary.domain.auth.dto.TokenPair;
import com.codeiary.domain.auth.cookie.TokenCookieManager;
import com.codeiary.domain.auth.service.TokenSessionService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CustomOauth2SuccessHandlerTest {

    @Mock private OAuthAccountService oauthAccounts;
    @Mock private TokenSessionService sessions;
    @Mock private TokenCookieManager cookies;
    @Mock private CustomOauth2FailureHandler failureHandler;
    @InjectMocks private CustomOauth2SuccessHandler handler;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final String callback = "https://codeiary.com/auth/callback";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "redirectHome", callback);
    }

    @Test
    @DisplayName("확인된 사용자로 세션과 쿠키를 발급하고 콜백으로 이동할 수 있다.")
    void createSessionForResolvedUser() throws Exception {
        // given
        User user = UserFixture.createWithId();
        CustomOAuth2User oauthUser = oauthUser();
        TokenPair pair = new TokenPair("access", "refresh", Instant.now().plusSeconds(1800),
                Instant.now().plusSeconds(604800));
        given(oauthAccounts.getOrCreate(oauthUser.getAuthInfo())).willReturn(user);
        given(sessions.createSession(user.getEmail())).willReturn(pair);

        // when
        handler.onAuthenticationSuccess(request, response, authentication(oauthUser));

        // then
        then(sessions).should().createSession(UserFixture.EMAIL);
        then(cookies).should().writeTokens(response, pair);
        then(failureHandler).shouldHaveNoInteractions();
        assertThat(response.getRedirectedUrl()).isEqualTo(callback);
    }

    @Test
    @DisplayName("계정 생성이 거절되면 토큰 없이 로그인 실패로 연결할 수 있다.")
    void handleRejectedAccount() throws Exception {
        // given
        CustomOAuth2User oauthUser = oauthUser();
        given(oauthAccounts.getOrCreate(oauthUser.getAuthInfo()))
                .willThrow(new RestApiException(UserErrorCode.OAUTH_ACCOUNT_CONFLICT));

        // when
        handler.onAuthenticationSuccess(request, response, authentication(oauthUser));

        // then
        then(failureHandler).should().onAuthenticationFailure(eq(request), eq(response),
                any(BadCredentialsException.class));
        then(sessions).shouldHaveNoInteractions();
        then(cookies).shouldHaveNoInteractions();
        assertThat(response.getRedirectedUrl()).isNull();
    }

    private CustomOAuth2User oauthUser() {
        String subject = "google-subject-123";
        var delegate = new DefaultOAuth2User(List.of(), Map.of("sub", subject), "sub");
        return new CustomOAuth2User(delegate,
                new AuthInfo(OAuthProvider.GOOGLE, subject, "google-current@gmail.com", UserFixture.NAME, true, true));
    }

    private Authentication authentication(Object principal) {
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of());
    }
}
