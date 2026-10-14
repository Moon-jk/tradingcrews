package com.pj.stock_console.repository;

import com.pj.stock_console.entity.Discussion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface DiscussionRepository extends JpaRepository<Discussion, Long> {
    
    // [수정] JOIN FETCH를 사용하여 Member(작성자) 정보를 한 번에 가져옵니다.
    @Query("select d from Discussion d join fetch d.member where d.symbol = :symbol order by d.createdAt asc")
    List<Discussion> findBySymbolOrderByCreatedAtAsc(@Param("symbol") String symbol);
}