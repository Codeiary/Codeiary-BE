package com.codeiary.global.config;

import com.codeiary.support.IntegrationTestSupport;
import com.querydsl.jpa.impl.JPAQueryFactory;
import fixtures.persistence.QSampleEntry;
import fixtures.persistence.SampleEntry;
import fixtures.persistence.SampleEntryMapper;
import fixtures.persistence.SampleEntryMapperImpl;
import fixtures.persistence.SampleEntryRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
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

    @TestConfiguration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = SampleEntry.class)
    @EnableJpaRepositories(basePackageClasses = SampleEntryRepository.class)
    static class PersistenceConfiguration {
    }
}
