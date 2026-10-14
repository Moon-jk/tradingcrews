package com.pj.stock_console.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Getter
@Setter
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.TIER3;

    @Column(name = "created_at")
    private LocalDate createdAt = LocalDate.now();

    /* 
     * OAuth2 전용 필드
     */
    private String provider; // 예: "google"
    private String providerId; // 구글 고유 ID (sub)

    public boolean isSocialUser() {
        return provider != null;
    }

    /*
     * ════════════════════════════════════════
     * 가상 재화 (TP) 및 출석
     * ════════════════════════════════════════
     */
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer tp = 0; // Trade Point

    @Column(name = "last_attendance_date")
    private LocalDate lastAttendanceDate;

    @Column(name = "last_card_news_read_date")
    private LocalDate lastCardNewsReadDate;

    public boolean isAttendanceCompleted(LocalDate today) {
        return lastAttendanceDate != null && lastAttendanceDate.isEqual(today);
    }

    public boolean isCardNewsMissionCompleted(LocalDate today) {
        return lastCardNewsReadDate != null && lastCardNewsReadDate.isEqual(today);
    }

    public int getMissionCount(LocalDate today) {
        int count = 0;
        if (isAttendanceCompleted(today)) count++;
        if (isCardNewsMissionCompleted(today)) count++;
        return count;
    }

    /*
     * ════════════════════════════════════════
     * 권한 확인
     * ════════════════════════════════════════
     */

    /** 관리자 여부 */
    public boolean isAdmin() {
        return this.role == Role.ADMIN;
    }

    /** TIER1 이상 (ADMIN 포함) */
    public boolean isPremium() {
        return this.role == Role.ADMIN || this.role == Role.TIER1;
    }

    /*
     * ════════════════════════════════════════
     * 모의투자 - 등급별 제한
     * ════════════════════════════════════════
     */

    /**
     * 동시 보유 가능 최대 종목 수
     *
     * ADMIN / TIER1 : 30종목 (프리미엄)
     * TIER2 : 15종목
     * TIER3 : 5종목 (기본)
     */
    public int getMaxHoldings() {
        return switch (this.role) {
            case ADMIN, TIER1 -> 30;
            case TIER2 -> 15;
            case TIER3 -> 5;
        };
    }

    /**
     * 초기 시드머니
     *
     * 미국 계좌 (USD)
     * ADMIN / TIER1 : $50,000
     * TIER2 : $20,000
     * TIER3 : $10,000
     *
     * 한국 계좌 (KRW)
     * ADMIN / TIER1 : ₩50,000,000
     * TIER2 : ₩20,000,000
     * TIER3 : ₩10,000,000
     */
    public BigDecimal getInitialSeed(TradingAccount.MarketType marketType) {
        boolean isUs = marketType == TradingAccount.MarketType.US;
        return switch (this.role) {
            case ADMIN, TIER1 -> isUs
                    ? new BigDecimal("50000.0000")
                    : new BigDecimal("50000000.0000");
            case TIER2 -> isUs
                    ? new BigDecimal("20000.0000")
                    : new BigDecimal("20000000.0000");
            case TIER3 -> isUs
                    ? new BigDecimal("10000.0000")
                    : new BigDecimal("10000000.0000");
        };
    }

    /**
     * 1회 최대 주문 가능 금액 (계좌 통화 기준)
     * 과도한 단일 투자 방지용
     *
     * ADMIN / TIER1 : 제한 없음 (MAX_VALUE)
     * TIER2 : 총 시드의 50%
     * TIER3 : 총 시드의 30%
     */
    public BigDecimal getMaxOrderRatio() {
        return switch (this.role) {
            case ADMIN, TIER1 -> new BigDecimal("1.00"); // 100% (제한 없음)
            case TIER2 -> new BigDecimal("0.50"); // 50%
            case TIER3 -> new BigDecimal("0.30"); // 30%
        };
    }

    /**
     * 등급 표시 문자열 (UI 표시용)
     * 예: "👑 관리자", "🥇 1등급"
     */
    public String getRoleBadge() {
        return switch (this.role) {
            case ADMIN -> "👑 " + role.getDescription();
            case TIER1 -> "🥇 " + role.getDescription();
            case TIER2 -> "🥈 " + role.getDescription();
            case TIER3 -> "🥉 " + role.getDescription();
        };
    }
}
