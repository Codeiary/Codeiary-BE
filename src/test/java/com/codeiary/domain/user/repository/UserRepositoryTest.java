package com.codeiary.domain.user.repository;

import com.codeiary.domain.blog.entity.BlogPost;
import com.codeiary.domain.blog.entity.Category;
import com.codeiary.domain.blog.repository.BlogPostRepository;
import com.codeiary.domain.blog.repository.CategoryRepository;
import com.codeiary.domain.user.entity.enums.OAuthProvider;
import com.codeiary.domain.user.entity.enums.Role;
import com.codeiary.domain.user.fixture.UserFixture;
import com.codeiary.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.auditing.CurrentDateTimeProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class UserRepositoryTest extends RepositoryTestSupport {

    @Autowired private UserRepository users;
    @Autowired private BlogPostRepository posts;
    @Autowired private CategoryRepository categories;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AuditingHandler auditingHandler;

    @AfterEach
    void restoreDateTimeProvider() {
        auditingHandler.setDateTimeProvider(CurrentDateTimeProvider.INSTANCE);
    }

    @Test
    @DisplayName("새 계정의 기본 권한을 PENDING으로 설정할 수 있다.")
    void defaultToPendingRole() {
        // given
        var user = UserFixture.createDefaultUser();
        String directEmail = "direct@example.com";

        // when
        Long id = users.saveAndFlush(user).getId();
        jdbc.update("insert into users(email, name, created_at, updated_at) values (?, ?, now(), now())",
                directEmail, user.getName());
        entityManager.clear();
        var saved = users.findById(id);
        var direct = users.findByEmail(directEmail);

        // then
        assertThat(saved).hasValueSatisfying(account -> assertThat(account.getRole()).isEqualTo(Role.PENDING));
        assertThat(direct).hasValueSatisfying(account -> assertThat(account.getRole()).isEqualTo(Role.PENDING));
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
    @DisplayName("중복 이메일을 거절할 수 있다.")
    void rejectDuplicateEmail() {
        // given
        users.saveAndFlush(UserFixture.create());
        var duplicate = UserFixture.create();

        // when
        Throwable error = catchThrowable(() -> users.saveAndFlush(duplicate));

        // then
        assertThat(error).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(error.getCause()).isInstanceOfSatisfying(
                org.hibernate.exception.ConstraintViolationException.class,
                violation -> assertThat(violation.getConstraintName()).isEqualTo("users_email_key"));
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

    @Test
    @DisplayName("프로필 변경을 저장하고 생성 시간을 유지하며 수정 시간을 갱신할 수 있다.")
    void savePublicProfile() {
        // given
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 9, 12, 0);
        auditingHandler.setDateTimeProvider(() -> Optional.of(createdAt));
        var account = users.saveAndFlush(UserFixture.createDefaultUser());

        // when
        auditingHandler.setDateTimeProvider(() -> Optional.of(createdAt.plusHours(1)));
        account.updatePublicProfile("CodeWriter", "https://example.com/profile.png",
                "https://github.com/code-writer", "contact@example.com");
        users.flush();
        entityManager.clear();
        var loaded = users.findById(account.getId());

        // then
        assertThat(loaded).hasValueSatisfying(saved -> {
            assertThat(saved.getNickname()).isEqualTo("CodeWriter");
            assertThat(saved.getProfileImageUrl()).isEqualTo("https://example.com/profile.png");
            assertThat(saved.getGithubUrl()).isEqualTo("https://github.com/code-writer");
            assertThat(saved.getContactEmail()).isEqualTo("contact@example.com");
            assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
            assertThat(saved.getUpdatedAt()).isEqualTo(createdAt.plusHours(1));
        });
    }

    @Test
    @DisplayName("대소문자만 다른 중복 닉네임의 저장을 거절할 수 있다.")
    void rejectCaseInsensitiveDuplicateNickname() {
        // given
        var account = UserFixture.create();
        account.updateProfile("CodeWriter", null);
        users.saveAndFlush(account);
        var duplicate = UserFixture.create("another@example.com");
        duplicate.updateProfile("codewriter", null);

        // when
        Throwable error = catchThrowable(() -> users.saveAndFlush(duplicate));

        // then
        assertThat(error).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("OAuth 제공자를 기존 문자열로 저장하고 enum으로 조회할 수 있다.")
    void persistOAuthProvider() {
        // given
        var account = UserFixture.createDefaultUser();
        account.linkOAuthAccount(OAuthProvider.GOOGLE, "google-user-123");

        // when
        Long id = users.saveAndFlush(account).getId();
        entityManager.clear();
        String stored = jdbc.queryForObject("select oauth_provider from users where id = ?", String.class, id);
        var loaded = users.findByOauthProviderAndOauthSubject(OAuthProvider.GOOGLE, "google-user-123");

        // then
        assertThat(stored).isEqualTo("google");
        assertThat(loaded).hasValueSatisfying(user -> {
            assertThat(user.getId()).isEqualTo(id);
            assertThat(user.getOauthProvider()).isEqualTo(OAuthProvider.GOOGLE);
        });
    }

    @Test
    @DisplayName("같은 OAuth 계정의 다른 이메일 가입을 거절할 수 있다.")
    void rejectDuplicateOAuthIdentity() {
        // given
        var account = UserFixture.createDefaultUser();
        account.linkOAuthAccount(OAuthProvider.GOOGLE, "google-user-123");
        Long id = users.saveAndFlush(account).getId();
        entityManager.clear();
        var duplicate = UserFixture.create("another@example.com");
        duplicate.linkOAuthAccount(OAuthProvider.GOOGLE, "google-user-123");

        // when
        var existing = users.findByOauthProviderAndOauthSubject(OAuthProvider.GOOGLE, "google-user-123");
        Throwable error = catchThrowable(() -> users.saveAndFlush(duplicate));

        // then
        assertThat(existing).hasValueSatisfying(saved -> assertThat(saved.getId()).isEqualTo(id));
        assertThat(error).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(error.getCause()).isInstanceOfSatisfying(
                org.hibernate.exception.ConstraintViolationException.class,
                violation -> assertThat(violation.getConstraintName()).isEqualTo("uk_users_oauth_identity"));
    }

    @Test
    @DisplayName("대소문자 없이 닉네임을 조회하고 본인의 닉네임을 중복 검사에서 제외할 수 있다.")
    void findNicknameAndExcludeOwner() {
        // given
        var account = UserFixture.createDefaultUser();
        account.updateProfile("CodeWriter", null);
        Long ownerId = users.saveAndFlush(account).getId();
        Long otherId = users.saveAndFlush(UserFixture.create("another@example.com")).getId();
        entityManager.clear();

        // when
        var found = users.findByNicknameIgnoreCase("codewriter");
        boolean duplicateForOwner = users.existsByNicknameIgnoreCaseAndIdNot("CODEWRITER", ownerId);
        boolean duplicateForOther = users.existsByNicknameIgnoreCaseAndIdNot("codewriter", otherId);
        boolean unknownNickname = users.existsByNicknameIgnoreCaseAndIdNot("NewWriter", ownerId);

        // then
        assertThat(found).hasValueSatisfying(saved -> assertThat(saved.getId()).isEqualTo(ownerId));
        assertThat(duplicateForOwner).isFalse();
        assertThat(duplicateForOther).isTrue();
        assertThat(unknownNickname).isFalse();
    }

    @Test
    @DisplayName("공개 글 활동 순으로 온보딩한 이웃을 조회할 수 있다.")
    void findNeighborhoodByPublicActivity() {
        // given
        var active = UserFixture.create("active@example.com");
        active.updateProfile("활동기록자", null);
        active = users.saveAndFlush(active);

        var quiet = UserFixture.create("quiet@example.com");
        quiet.updateProfile("조용한기록자", null);
        quiet = users.saveAndFlush(quiet);
        users.saveAndFlush(UserFixture.createDefaultUser());

        var category = categories.saveAndFlush(Category.create(active, "개발"));
        posts.saveAndFlush(BlogPost.create(active, "공개 글", "내용", category, null, true));
        posts.saveAndFlush(BlogPost.create(active, "비공개 글", "내용", category, null, false));
        entityManager.flush();
        entityManager.clear();

        // when
        var result = users.findNeighborhood(PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).extracting("nickname")
                .containsExactly("활동기록자", "조용한기록자");
        assertThat(result.getContent()).extracting("postCount")
                .containsExactly(1L, 0L);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }
}
