package com.codeiary.domain.portfolio.repository;

import com.codeiary.domain.portfolio.entity.Font;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FontRepository extends JpaRepository<Font, Long> {
  List<Font> findAllByEnabledTrueOrderByIdAsc();
}
