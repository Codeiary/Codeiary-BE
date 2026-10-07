package com.codeiary.domain.users.entity;

import com.codeiary.domain.users.entity.enums.Role;
import com.codeiary.global.entity.TimeBaseEntity;
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean enabled = true;

    public User(String email, String name) {
        this(email, name, Role.USER);
    }

    public User(String email, String name, Role role) {
        this.email = email;
        this.name = name;
        this.role = Objects.requireNonNull(role);
    }

    public void disable() {
        this.enabled = false;
    }

    public void updateProfile(String nickname, String profileImageUrl) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    public boolean isOnboardingCompleted() {
        return nickname != null && !nickname.isBlank();
    }
}
