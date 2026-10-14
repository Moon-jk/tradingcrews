package com.pj.stock_console.repository;

import com.pj.stock_console.entity.Member;
import com.pj.stock_console.entity.TpHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TpHistoryRepository extends JpaRepository<TpHistory, Long> {
    List<TpHistory> findAllByMemberOrderByCreatedAtDesc(Member member);
}
