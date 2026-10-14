package com.pj.stock_console.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "prediction_option")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private PredictionQuestion question;

    @Column(nullable = false)
    private String content;

    @Builder.Default
    @Column(nullable = false)
    private Long optionPool = 0L;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isWinner = false;

    @OneToMany(mappedBy = "option", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private java.util.List<PredictionBet> bets = new java.util.ArrayList<>();

    public void addOptionPool(Long amount) {
        this.optionPool += amount;
    }
}
