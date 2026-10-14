package com.pj.stock_console.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 모의투자 계좌
 *
 * 회원 1명당 US 계좌 + KR 계좌 각각 1개 보유.
 * 초기 시드는 Member.getInitialSeed() 로 등급별로 다르게 적용됨.
 *
 *  ADMIN / TIER1  US: $50,000   KR: ₩50,000,000
 *  TIER2          US: $20,000   KR: ₩20,000,000
 *  TIER3          US: $10,000   KR: ₩10,000,000
 */
@Entity
@Table(name = "trading_accounts",
       uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "market_type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradingAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Setter
    @Column(nullable = false, length = 50)
    private String memberNickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_type", nullable = false, length = 5)
    private MarketType marketType;

    /** 보유 현금 (US: USD / KR: KRW) */
    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal cash;

    /** 초기 시드 - 등급별로 다름 */
    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal initialSeed;

    /** 계좌 생성 시 부여된 등급 (기록용) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role memberRole;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Holding> holdings = new ArrayList<>();

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("tradedAt DESC")
    private List<TradeHistory> tradeHistories = new ArrayList<>();

    /* ── Enum ── */
    public enum MarketType {
        US,  // 미국 (USD, Finnhub)
        KR   // 한국 (KRW, KIS)
    }

    /* ════════════════════════════════════════
       팩토리 - Member 기반 생성 (권장)
       등급에 맞는 시드 자동 적용
       ════════════════════════════════════════ */
    public static TradingAccount create(Member member, MarketType type) {
        TradingAccount a = new TradingAccount();
        a.memberId       = member.getId();
        a.memberNickname = member.getNickname();
        a.marketType     = type;
        a.memberRole     = member.getRole();
        a.initialSeed    = member.getInitialSeed(type);
        a.cash           = a.initialSeed;
        return a;
    }

    /* ════════════════════════════════════════
       비즈니스 메서드
       ════════════════════════════════════════ */

    public void deposit(BigDecimal amount) {
        this.cash = this.cash.add(amount);
    }

    public void withdraw(BigDecimal amount) {
        if (this.cash.compareTo(amount) < 0)
            throw new IllegalStateException(
                    "잔고가 부족합니다. (보유: " + getCurrencySymbol()
                    + String.format("%,.2f", cash) + ")");
        this.cash = this.cash.subtract(amount);
    }

    /** 총 자산 = 현금 + 보유종목 평가액 */
    public BigDecimal getTotalAsset(BigDecimal holdingValuation) {
        return this.cash.add(holdingValuation);
    }

    /** 수익률 (%) */
    public BigDecimal getProfitRate(BigDecimal totalAsset) {
        if (initialSeed.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return totalAsset.subtract(initialSeed)
                .multiply(new BigDecimal("100"))
                .divide(initialSeed, 2, RoundingMode.HALF_UP);
    }

    /** 통화 기호 */
    public String getCurrencySymbol() {
        return marketType == MarketType.US ? "$" : "₩";
    }

    /** 통화 코드 */
    public String getCurrencyCode() {
        return marketType == MarketType.US ? "USD" : "KRW";
    }
}
