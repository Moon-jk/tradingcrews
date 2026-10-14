package com.pj.stock_console.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class Watchlist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String symbol;

    @Column(columnDefinition = "TEXT") // 긴 문장도 저장 가능하도록 TEXT 타입 지정
    private String memo;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean pinned = false;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;
}