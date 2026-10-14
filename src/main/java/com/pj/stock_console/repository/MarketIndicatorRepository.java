package com.pj.stock_console.repository;

import com.pj.stock_console.entity.MarketIndicator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MarketIndicatorRepository extends JpaRepository<MarketIndicator, Long> {

    // 데이터 중복 여부 확인
    boolean existsBySymbolAndTargetDate(String symbol, LocalDate targetDate);

    // 날짜 오름차순으로 특정 심볼의 데이터 반환 (차트 렌더링용)
    List<MarketIndicator> findBySymbolOrderByTargetDateAsc(String symbol);
}
