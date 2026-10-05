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

    @Column(nullable = false, length = 60)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean enabled = true;

    public User(String email, String passwordHash, String name) {
        this(email, passwordHash, name, Role.USER);
    }

    public User(String email, String passwordHash, String name, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = Objects.requireNonNull(role);
    }

    public void disable() {
        this.enabled = false;
    }
}
