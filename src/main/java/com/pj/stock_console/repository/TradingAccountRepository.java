package com.pj.stock_console.repository;

import com.pj.stock_console.entity.TradingAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TradingAccountRepository extends JpaRepository<TradingAccount, Long> {

    Optional<TradingAccount> findByMemberIdAndMarketType(
            Long memberId, TradingAccount.MarketType marketType);

    List<TradingAccount> findAllByMemberId(Long memberId);

    List<TradingAccount> findAllByMemberIdIn(java.util.Collection<Long> memberIds);

    /** 랭킹용 전체 조회 (Holdings 미리 로드하여 N+1 방지) */
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT ta FROM TradingAccount ta LEFT JOIN FETCH ta.holdings")
    List<TradingAccount> findAllWithHoldings();
}
