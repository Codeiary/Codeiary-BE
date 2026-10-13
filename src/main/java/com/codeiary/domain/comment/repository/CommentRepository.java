package com.codeiary.domain.comment.repository;

import com.codeiary.domain.comment.entity.Comment;
import com.codeiary.domain.comment.entity.CommentTargetType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @EntityGraph(attributePaths = "author")
    List<Comment> findAllByTargetTypeAndTargetIdOrderByCreatedAtAscIdAsc(CommentTargetType targetType, Long targetId);

    Optional<Comment> findByIdAndTargetTypeAndTargetId(Long id, CommentTargetType targetType, Long targetId);
}
