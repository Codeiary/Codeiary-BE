package com.codeiary;

import com.codeiary.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class CodeiaryBeApplicationTests extends IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("테스트용 PostgreSQL에 연결하여 애플리케이션을 실행할 수 있다.")
    void contextLoadsWithTestPostgres() {
        assertThat(jdbcTemplate.queryForObject("SELECT current_database()", String.class))
                .isEqualTo("codeiary_test");
        assertThat(jdbcTemplate.queryForObject("SELECT version()", String.class))
                .startsWith("PostgreSQL 17.");
    }

    @Test
    @DisplayName("Flyway로 테스트 DB를 초기화할 수 있다.")
    void flywayInitializesTheTestDatabase() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT to_regclass('public.flyway_schema_history')::text", String.class))
                .isEqualTo("flyway_schema_history");
    }

}
