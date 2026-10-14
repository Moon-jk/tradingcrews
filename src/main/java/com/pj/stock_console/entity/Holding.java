package com.pj.stock_console.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "holdings",
       uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "symbol"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private TradingAccount account;

    /** 미국: AAPL / 한국: 005930 */
    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 100)
    private String companyName;

    @Column(nullable = false)
    private int quantity;

    /** 평균 매수가 (계좌 통화 기준) */
    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal avgBuyPrice;

    /* ── 팩토리 ── */
    public static Holding create(TradingAccount account, String symbol,
                                 String companyName, int quantity, BigDecimal price) {
        Holding h     = new Holding();
        h.account     = account;
        h.symbol      = symbol;
        h.companyName = companyName;
        h.quantity    = quantity;
        h.avgBuyPrice = price;
        return h;
    }

    /* ── 매수 (평균 단가 재계산) ── */
    public void buy(int qty, BigDecimal price) {
        BigDecimal prevTotal = avgBuyPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal newTotal  = price.multiply(BigDecimal.valueOf(qty));
        quantity    += qty;
        avgBuyPrice  = prevTotal.add(newTotal)
                .divide(BigDecimal.valueOf(quantity), 4, RoundingMode.HALF_UP);
    }

    /* ── 매도 ── */
    public void sell(int qty) {
        if (qty > quantity)
            throw new IllegalStateException("보유 수량 부족 (보유: " + quantity + "주, 요청: " + qty + "주)");
        quantity -= qty;
    }

    public BigDecimal getValuation(BigDecimal currentPrice) {
        return currentPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public BigDecimal getUnrealizedPnl(BigDecimal currentPrice) {
        return currentPrice.subtract(avgBuyPrice).multiply(BigDecimal.valueOf(quantity));
    }

    public BigDecimal getUnrealizedPnlRate(BigDecimal currentPrice) {
        if (avgBuyPrice.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return currentPrice.subtract(avgBuyPrice)
                .multiply(new BigDecimal("100"))
                .divide(avgBuyPrice, 2, RoundingMode.HALF_UP);
    }
}
