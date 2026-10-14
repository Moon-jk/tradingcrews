package com.pj.stock_console.repository;

import com.pj.stock_console.entity.WatchlistMemo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import com.pj.stock_console.entity.Member;

public interface WatchlistMemoRepository extends JpaRepository<WatchlistMemo, Long> {
    // 특정 멤버의 특정 종목 메모를 최신순으로 조회
    List<WatchlistMemo> findByMemberAndSymbolOrderByCreatedAtDesc(Member member, String symbol);
}