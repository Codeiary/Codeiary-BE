package com.codeiary.domain.portfolio.repository;

import com.codeiary.domain.portfolio.entity.Font;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FontRepository extends JpaRepository<Font, Long> {
    List<Font> findAllByEnabledTrueOrderByIdAsc();
}
