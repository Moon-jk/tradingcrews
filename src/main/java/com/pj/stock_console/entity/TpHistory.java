package com.pj.stock_console.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TpHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    private Integer amount; // 변동 금액 (음수면 차감, 양수면 증액)

    private Integer balance; // 변동 후 잔액

    private String description; // 상세 내역

    private LocalDateTime createdAt;
}
