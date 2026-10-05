package com.codeiary.global.config;

import com.codeiary.CodeiaryBeApplication;
import com.codeiary.support.IntegrationTestSupport;
import com.querydsl.jpa.impl.JPAQueryFactory;
import fixtures.persistence.QSampleEntry;
import fixtures.persistence.SampleEntry;
import fixtures.persistence.SampleEntryMapper;
import fixtures.persistence.SampleEntryMapperImpl;
import fixtures.persistence.SampleEntryRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.auditing.CurrentDateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@Import({CodeGenerationIntegrationTest.PersistenceConfiguration.class, SampleEntryMapperImpl.class})
@Transactional
class CodeGenerationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private JPAQueryFactory queryFactory;

    @Autowired
    private SampleEntryRepository repository;

    @Autowired
    private SampleEntryMapper mapper;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AuditingHandler auditingHandler;

    @AfterEach
    void restoreDateTimeProvider() {
        auditingHandler.setDateTimeProvider(CurrentDateTimeProvider.INSTANCE);
    }

    @Test
    @DisplayName("생성된 Q 타입으로 PostgreSQL과 Spring Data에서 조건 조회할 수 있다.")
    void generatedQTypeWorksWithJpaQueryFactoryAndSpringData() {
        repository.saveAllAndFlush(List.of(
                new SampleEntry("Spring Boot", "backend"),
                new SampleEntry("Vue", "frontend")));
        QSampleEntry entry = QSampleEntry.sampleEntry;

        assertThat(queryFactory.selectFrom(entry)
                .where(entry.category.eq("backend"))
                .fetch())
                .extracting(SampleEntry::getTitle)
                .containsExactly("Spring Boot");
        assertThat(repository.findAll(entry.category.eq("frontend")))
                .extracting(SampleEntry::getTitle)
                .containsExactly("Vue");
    }

    @Test
    @DisplayName("Lombok 접근자와 MapStruct Spring 빈으로 엔티티를 DTO로 변환할 수 있다.")
    void generatedSpringMapperReadsLombokGetters() {
        SampleEntry entry = repository.saveAndFlush(new SampleEntry("Code Diary", "backend"));

        assertThat(mapper.toResponse(entry))
                .satisfies(response -> {
                    assertThat(response.id()).isEqualTo(entry.getId());
                    assertThat(response.title()).isEqualTo("Code Diary");
                    assertThat(response.category()).isEqualTo("backend");
                });
    }

    @Test
    @DisplayName("엔티티를 저장할 때 생성 시간과 수정 시간을 자동 기록할 수 있다.")
    void recordsCreationAndModificationTimesOnInsert() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 5, 12, 0);
        auditingHandler.setDateTimeProvider(() -> Optional.of(createdAt));
        Long id = repository.saveAndFlush(new SampleEntry("First entry", "backend")).getId();
        entityManager.clear();

        SampleEntry stored = repository.findById(id).orElseThrow();

        assertThat(stored.getCreatedAt()).isEqualTo(createdAt);
        assertThat(stored.getUpdatedAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("엔티티를 변경할 때 생성 시간을 유지하고 수정 시간만 갱신할 수 있다.")
    void preservesCreationTimeAndUpdatesModificationTime() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 5, 12, 0);
        LocalDateTime updatedAt = createdAt.plusHours(1);
        auditingHandler.setDateTimeProvider(() -> Optional.of(createdAt));
        Long id = repository.saveAndFlush(new SampleEntry("First entry", "backend")).getId();
        entityManager.clear();

        SampleEntry entry = repository.findById(id).orElseThrow();
        auditingHandler.setDateTimeProvider(() -> Optional.of(updatedAt));
        entry.rename("Updated entry");
        repository.flush();
        entityManager.clear();

        SampleEntry stored = repository.findById(id).orElseThrow();
        assertThat(stored.getTitle()).isEqualTo("Updated entry");
        assertThat(stored.getCreatedAt()).isEqualTo(createdAt);
        assertThat(stored.getUpdatedAt()).isEqualTo(updatedAt);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = {SampleEntry.class, CodeiaryBeApplication.class})
    @EnableJpaRepositories(basePackageClasses = {SampleEntryRepository.class, CodeiaryBeApplication.class})
    static class PersistenceConfiguration {
    }
}
