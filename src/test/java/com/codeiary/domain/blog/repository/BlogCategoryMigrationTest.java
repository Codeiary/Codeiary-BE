package com.codeiary.domain.blog.repository;

import com.codeiary.support.RepositoryTestSupport;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BlogCategoryMigrationTest extends RepositoryTestSupport {

    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("기존 글을 보존하면서 작성자별 카테고리로 이관할 수 있다.")
    void migrateExistingPosts() {
        // given
        String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
        var jdbc = new JdbcTemplate(dataSource);
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .target("6").load().migrate();
            jdbc.update("""
                    INSERT INTO %s.users (id, email, name, created_at, updated_at)
                    VALUES (1, 'first@example.com', '첫 사용자', now(), now()),
                           (2, 'second@example.com', '다른 사용자', now(), now())
                    """.formatted(schema));
            jdbc.update("""
                    INSERT INTO %s.blog_post (author_id, title, content, category, created_at, updated_at)
                    VALUES (1, '첫 글', '유지할 본문', ' Java ', now(), now()),
                           (1, '둘째 글', '본문', 'Java', now(), now()),
                           (1, '미분류 글', '본문', NULL, now(), now()),
                           (1, '빈 분류 글', '본문', ' ', now(), now()),
                           (2, '다른 사용자 글', '본문', 'Java', now(), now())
                    """.formatted(schema));

            // when
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();

            // then
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema + ".blog_post", Long.class)).isEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + schema + ".category", Long.class)).isEqualTo(3);
            assertThat(jdbc.queryForList("""
                    SELECT c.name FROM %1$s.blog_post p JOIN %1$s.category c ON c.id = p.category_id
                    ORDER BY p.id
                    """.formatted(schema), String.class)).containsExactly("Java", "Java", "미분류", "미분류", "Java");
            var categoryIds = jdbc.queryForList("SELECT category_id FROM " + schema + ".blog_post ORDER BY id", Long.class);
            assertThat(categoryIds.get(0)).isEqualTo(categoryIds.get(1)).isNotEqualTo(categoryIds.get(4));
            assertThat(categoryIds.get(2)).isEqualTo(categoryIds.get(3));
            assertThat(jdbc.queryForObject("SELECT content FROM " + schema + ".blog_post WHERE title = '첫 글'", String.class))
                    .isEqualTo("유지할 본문");
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }
}
