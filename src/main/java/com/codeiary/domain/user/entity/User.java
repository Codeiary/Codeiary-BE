package com.codeiary.domain.user.entity;

import com.codeiary.domain.user.entity.enums.OAuthProvider;
import com.codeiary.domain.user.entity.enums.Role;
import com.codeiary.domain.user.exception.UserErrorCode;
import com.codeiary.global.entity.TimeBaseEntity;
import com.codeiary.global.exception.RestApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends TimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    @Pattern(regexp = "[^\\p{Lu}\\p{Lt}]*", message = "이메일은 소문자로 입력해 주세요.")
    private String email;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 20, unique = true)
    @Pattern(regexp = "[가-힣A-Za-z0-9_]{2,20}", message = "닉네임은 한글, 영문, 숫자, 밑줄로 2~20자를 입력해 주세요.")
    private String nickname;

    @Column(length = 2048)
    @Size(max = 2048)
    private String profileImageUrl;

    @Column(length = 255)
    private String githubUrl;

    @Column(length = 254)
    private String contactEmail;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OAuthProvider oauthProvider;

    @Column(length = 255)
    private String oauthSubject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.PENDING;

    @Column(nullable = false)
    private boolean enabled = true;

    @Builder(access = AccessLevel.PRIVATE)
    private User(String email, String name, Role role, OAuthProvider oauthProvider, String oauthSubject) {
        this.email = email;
        this.name = name;
        this.role = Objects.requireNonNull(role);
        this.oauthProvider = oauthProvider;
        this.oauthSubject = oauthSubject;
    }

    public static User create(String email, String name) {
        return create(email, name, Role.PENDING);
    }

    public static User create(String email, String name, Role role) {
        return User.builder()
                .email(email)
                .name(name)
                .role(role)
                .build();
    }

    public static User createOAuth(String email, String name, OAuthProvider provider, String subject) {
        return User.builder()
                .email(email)
                .name(name)
                .role(Role.PENDING)
                .oauthProvider(provider)
                .oauthSubject(subject)
                .build();
    }

    public void linkOAuthAccount(OAuthProvider provider, String subject) {
        if (hasOAuthAccount() && !isLinkedTo(provider, subject)) {
            throw new RestApiException(UserErrorCode.OAUTH_ACCOUNT_CONFLICT);
        }
        this.oauthProvider = provider;
        this.oauthSubject = subject;
    }

    public boolean hasOAuthAccount() {
        return oauthProvider != null || oauthSubject != null;
    }

    public boolean isLinkedTo(OAuthProvider provider, String subject) {
        return provider != null && subject != null
                && oauthProvider == provider && subject.equals(oauthSubject);
    }

    public void disable() {
        this.enabled = false;
    }

    public void updateProfile(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public void completeOnboarding(String nickname) {
        this.nickname = nickname;
        if (role == Role.PENDING) {
            role = Role.USER;
        }
    }

    public void updatePublicProfile(String nickname, String profileImageUrl,
                                   String githubUrl, String contactEmail) {
        updateProfile(nickname, profileImageUrl);
        this.githubUrl = githubUrl;
        this.contactEmail = contactEmail;
    }

    public boolean isOnboardingCompleted() {
        return role != Role.PENDING;
    }
}
