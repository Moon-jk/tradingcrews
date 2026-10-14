package com.pj.stock_console.repository;

import com.pj.stock_console.entity.TradeHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeHistoryRepository extends JpaRepository<TradeHistory, Long> {
    Page<TradeHistory> findAllByAccountIdOrderByTradedAtDesc(Long accountId, Pageable pageable);
}
