package com.pj.stock_console.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trade_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private TradingAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TradeType tradeType;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 100)
    private String companyName;

    @Column(nullable = false)
    private int quantity;

    /** 체결가 (계좌 통화 기준) */
    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal price;

    /** 체결 총금액 */
    @Column(nullable = false, precision = 20, scale = 2)
    private BigDecimal totalAmount;

    /** 실현손익 (매도 시, 계좌 통화 기준) */
    @Column(precision = 20, scale = 2)
    private BigDecimal realizedPnl;

    @CreationTimestamp
    private LocalDateTime tradedAt;

    public enum TradeType { BUY, SELL }

    /* ── 팩토리 ── */
    public static TradeHistory ofBuy(TradingAccount account, String symbol,
                                     String companyName, int qty, BigDecimal price) {
        TradeHistory h = new TradeHistory();
        h.account      = account;
        h.tradeType    = TradeType.BUY;
        h.symbol       = symbol;
        h.companyName  = companyName;
        h.quantity     = qty;
        h.price        = price;
        h.totalAmount  = price.multiply(BigDecimal.valueOf(qty));
        h.realizedPnl  = BigDecimal.ZERO;
        return h;
    }

    public static TradeHistory ofSell(TradingAccount account, String symbol,
                                      String companyName, int qty,
                                      BigDecimal price, BigDecimal avgBuyPrice) {
        TradeHistory h = new TradeHistory();
        h.account      = account;
        h.tradeType    = TradeType.SELL;
        h.symbol       = symbol;
        h.companyName  = companyName;
        h.quantity     = qty;
        h.price        = price;
        h.totalAmount  = price.multiply(BigDecimal.valueOf(qty));
        h.realizedPnl  = price.subtract(avgBuyPrice).multiply(BigDecimal.valueOf(qty));
        return h;
    }
}
