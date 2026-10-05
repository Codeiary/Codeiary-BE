package fixtures.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;

public interface SampleEntryRepository extends JpaRepository<SampleEntry, Long>,
        QuerydslPredicateExecutor<SampleEntry> {
}
