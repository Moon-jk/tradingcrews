package com.pj.stock_console.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Getter
@Setter
@NoArgsConstructor // 기본 생성자 추가
public class News {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String headline; // 제목

    @Column(columnDefinition = "TEXT")
    private String summary; // 내용

    @Column(columnDefinition = "TEXT")
    private String url; // 링크
    private Long datetime; // 기사 발행 시간 (Unix Timestamp)

    // [추가된 필드]
    @Column(length = 100)
    private String symbol; // 종목 코드 (예: AAPL)

    @Column(columnDefinition = "TEXT")
    private String source; // 출처 (예: Bloomberg, CNBC)

    @Column(length = 20)
    private String sentiment; // 감정 분석 결과 (POSITIVE, NEUTRAL, NEGATIVE)

    @Column(columnDefinition = "TEXT")
    private String tickers; // 연관 티커 (콤마 구분)

    private LocalDateTime createdAt; // DB 저장 시간

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    // [편의 생성자] 서비스에서 객체 만들기 쉽게 추가
    public News(String symbol, String headline, String summary, String url, Long datetime, String source) {
        this.symbol = symbol;
        this.headline = headline;
        this.summary = summary;
        this.url = url;
        this.datetime = datetime;
        this.source = source;
    }

    // [보너스] 화면 출력용 날짜 변환 메서드 (Long -> LocalDateTime)
    public LocalDateTime getPubDate() {
        if (this.datetime == null)
            return LocalDateTime.now();
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(this.datetime), ZoneId.of("Asia/Seoul"));
    }
}