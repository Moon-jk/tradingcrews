package com.pj.stock_console.repository;

import com.pj.stock_console.entity.Member;
import com.pj.stock_console.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {
    List<Watchlist> findByMember(Member member);
    long countByMember(Member member);
    
    // [추가] 특정 회원의 특정 종목 하나만 가져오는 메서드
    Optional<Watchlist> findByMemberAndSymbol(Member member, String symbol);
    
    // [추가] 메인 화면에 고정된 관심 종목 가져오기
    List<Watchlist> findByMemberAndPinnedTrue(Member member);
    int countByMemberAndPinnedTrue(Member member);
    
    void deleteByMemberAndSymbol(Member member, String symbol);
    boolean existsByMemberAndSymbol(Member member, String symbol);
}