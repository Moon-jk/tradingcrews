package com.pj.stock_console.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pending_orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PendingOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private TradingAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TradeHistory.TradeType tradeType;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(nullable = false, length = 100)
    private String companyName;

    @Column(nullable = false)
    private int quantity;

    /** 지정 목표가 (계좌 통화 기준) */
    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal targetPrice;

    /** 매수 지정가 시 동결 금액 */
    @Column(nullable = false, precision = 20, scale = 4)
    private BigDecimal reservedCash = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private OrderStatus status = OrderStatus.PENDING;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime filledAt;

    public enum OrderStatus { PENDING, FILLED, CANCELLED }

    public static PendingOrder create(TradingAccount account,
                                      TradeHistory.TradeType type,
                                      String symbol, String companyName,
                                      int qty, BigDecimal targetPrice,
                                      BigDecimal reservedCash) {
        PendingOrder o = new PendingOrder();
        o.account      = account;
        o.tradeType    = type;
        o.symbol       = symbol;
        o.companyName  = companyName;
        o.quantity     = qty;
        o.targetPrice  = targetPrice;
        o.reservedCash = reservedCash;
        return o;
    }

    public void fill()   { this.status = OrderStatus.FILLED;    this.filledAt = LocalDateTime.now(); }
    public void cancel() { this.status = OrderStatus.CANCELLED; }
}
