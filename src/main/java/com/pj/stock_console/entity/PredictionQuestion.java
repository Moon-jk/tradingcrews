package com.pj.stock_console.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "prediction_question")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PredictionStatus status;

    @Builder.Default
    @Column(nullable = false)
    private Long totalPool = 0L;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime closedAt;

    private LocalDateTime settledAt;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PredictionOption> options = new ArrayList<>();

    // --- 자동화 필드 ---
    private String targetStockCode; // 종목코드
    private String marketType;      // US or KR
    private BigDecimal basePrice;   // 기준 주가 (생성 당시 가격)

    private String resultMessage;   // 정산 결과 메시지 (사용자 공지용)

    public void addTotalPool(Long amount) {
        this.totalPool += amount;
    }
}
