package com.codeiary.domain.users.repository;

import com.codeiary.domain.users.entity.enums.Role;
import com.codeiary.domain.users.fixture.UserFixture;
import com.codeiary.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class UserRepositoryTest extends RepositoryTestSupport {

    @Autowired private UserRepository users;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("사용자 권한을 저장하고 조회할 수 있다.")
    void saveRole(Role role) {
        // given
        var user = UserFixture.create(role);

        // when
        Long id = users.saveAndFlush(user).getId();
        entityManager.clear();
        var loaded = users.findById(id);
        String storedRole = jdbc.queryForObject("select role from users where id = ?", String.class, id);

        // then
        assertThat(loaded).hasValueSatisfying(saved -> assertThat(saved.getRole()).isEqualTo(role));
        assertThat(storedRole).isEqualTo(role.name());
    }

    @Test
    @DisplayName("새 계정의 기본 권한을 USER로 설정할 수 있다.")
    void defaultToUserRole() {
        // given
        var user = UserFixture.createDefaultUser();
        String directEmail = "direct@example.com";

        // when
        Long id = users.saveAndFlush(user).getId();
        jdbc.update("insert into users(email, password_hash, name, created_at, updated_at) values (?, ?, ?, now(), now())",
                directEmail, user.getPasswordHash(), user.getName());
        entityManager.clear();
        var saved = users.findById(id);
        var direct = users.findByEmail(directEmail);

        // then
        assertThat(saved).hasValueSatisfying(account -> assertThat(account.getRole()).isEqualTo(Role.USER));
        assertThat(direct).hasValueSatisfying(account -> assertThat(account.getRole()).isEqualTo(Role.USER));
    }

    @Test
    @DisplayName("정의되지 않은 권한을 거절할 수 있다.")
    void rejectUnknownRole() {
        // given
        Long id = users.saveAndFlush(UserFixture.create()).getId();

        // when
        Throwable error = catchThrowable(() -> jdbc.update("update users set role = ? where id = ?", "SUPER_ADMIN", id));

        // then
        assertThat(error).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("이메일로 사용자를 조회할 수 있다.")
    void findByEmail() {
        // given
        var saved = users.saveAndFlush(UserFixture.create());
        entityManager.clear();

        // when
        var account = users.findByEmail(UserFixture.EMAIL);
        var unknownAccount = users.findByEmail("unknown@example.com");

        // then
        assertThat(account).hasValueSatisfying(user -> {
            assertThat(user.getId()).isEqualTo(saved.getId());
            assertThat(user.getEmail()).isEqualTo(UserFixture.EMAIL);
            assertThat(user.getName()).isEqualTo(UserFixture.NAME);
            assertThat(user.getCreatedAt()).isNotNull();
            assertThat(user.getUpdatedAt()).isNotNull();
        });
        assertThat(unknownAccount).isEmpty();
    }

    @Test
    @DisplayName("중복 이메일을 거절할 수 있다.")
    void rejectDuplicateEmail() {
        // given
        users.saveAndFlush(UserFixture.create());
        var duplicate = UserFixture.create();

        // when
        Throwable error = catchThrowable(() -> users.saveAndFlush(duplicate));

        // then
        assertThat(error).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("대문자 이메일의 엔티티 저장을 거절할 수 있다.")
    void rejectUppercaseEmail() {
        // given
        var account = UserFixture.create("Admin@example.com");

        // when
        Throwable error = catchThrowable(() -> users.saveAndFlush(account));

        // then
        assertThat(error).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    @DisplayName("대문자 이메일의 SQL 저장을 거절할 수 있다.")
    void rejectUppercaseEmailInDatabase() {
        // given
        Long id = users.saveAndFlush(UserFixture.create()).getId();
        String uppercaseEmail = "Admin@example.com";

        // when
        Throwable error = catchThrowable(() -> jdbc.update("update users set email = ? where id = ?", uppercaseEmail, id));

        // then
        assertThat(error).isInstanceOf(DataIntegrityViolationException.class);
    }
}
