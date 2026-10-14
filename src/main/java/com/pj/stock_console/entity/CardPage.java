package com.pj.stock_console.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 카드뉴스 안의 개별 카드 1장
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class CardPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 부모 카드뉴스 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_news_id", nullable = false)
    private CardNews cardNews;

    /** 카드 번호 (1, 2, 3...) */
    @Column(nullable = false)
    private int pageNum;

    /** 카드 제목 */
    @Column(nullable = false)
    private String title;

    /** 카드 본문 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    public static CardPage of(CardNews cardNews, int pageNum, String title, String content) {
        CardPage cp = new CardPage();
        cp.cardNews = cardNews;
        cp.pageNum = pageNum;
        cp.title = title;
        cp.content = content;
        return cp;
    }
}
