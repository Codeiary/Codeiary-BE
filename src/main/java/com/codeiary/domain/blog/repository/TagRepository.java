package com.codeiary.domain.blog.repository;

import com.codeiary.domain.blog.entity.Tag;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findByNameInOrderByNameAsc(Collection<String> names);

    @Modifying
    @Query(value = """
            INSERT INTO tag (name, created_at, updated_at)
            VALUES (:name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (name) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(String name);
}
