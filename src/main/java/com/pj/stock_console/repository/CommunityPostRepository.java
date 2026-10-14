package com.pj.stock_console.repository;

import com.pj.stock_console.entity.CommunityPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {
    @Query("SELECT p FROM CommunityPost p JOIN FETCH p.author WHERE p.board.id = :boardId ORDER BY p.createdAt DESC")
    List<CommunityPost> findByBoardIdOrderByCreatedAtDesc(@Param("boardId") Long boardId);
}
