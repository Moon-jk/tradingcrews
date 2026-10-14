package com.pj.stock_console.repository;

import com.pj.stock_console.entity.News;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface NewsRepository extends JpaRepository<News, Long> {
    
    // 1. 중복 체크용
    boolean existsByUrl(String url);
    boolean existsByHeadline(String headline);
    
    // 2. 오래된 데이터 삭제용
    @Modifying
    @Transactional
    void deleteByDatetimeLessThan(long timestamp);

    // 3. 전체 조회
    Page<News> findAll(Pageable pageable);

    // 4. 통합 검색 기능
    Page<News> findByHeadlineContainingIgnoreCaseOrSummaryContainingIgnoreCase(
        String headlineKeyword, 
        String summaryKeyword, 
        Pageable pageable
    );

    // 5. 텍스트 내 포함된 티커 및 티커 필드 기반 검색
    @Query(value = "SELECT n FROM News n WHERE " +
           "n.headline LIKE %:symbol% OR n.summary LIKE %:symbol% OR n.tickers LIKE %:symbol% " +
           "ORDER BY n.datetime DESC LIMIT 5")
    List<News> findTop5ByRelatedText(@Param("symbol") String symbol);

    // 6. 감성 분석 통계용
    long countBySentimentAndDatetimeGreaterThan(String sentiment, long timestamp);
}