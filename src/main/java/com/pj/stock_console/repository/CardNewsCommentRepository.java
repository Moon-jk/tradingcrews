package com.pj.stock_console.repository;

import com.pj.stock_console.entity.CardNewsComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CardNewsCommentRepository extends JpaRepository<CardNewsComment, Long> {

    @EntityGraph(attributePaths = { "member" })
    List<CardNewsComment> findByCardNewsIdOrderByCreatedAtDesc(Long cardNewsId);
}
