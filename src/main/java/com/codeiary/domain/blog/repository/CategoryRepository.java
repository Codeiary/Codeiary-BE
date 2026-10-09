package com.codeiary.domain.blog.repository;

import com.codeiary.domain.blog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Category findByAuthorIdAndName(Long authorId, String name);

    @Modifying
    @Query(value = """
            INSERT INTO category (author_id, name, created_at, updated_at)
            VALUES (:authorId, :name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (author_id, name) DO NOTHING
            """, nativeQuery = true)
    void insertIfAbsent(Long authorId, String name);
}
