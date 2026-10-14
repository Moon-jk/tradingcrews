package com.pj.stock_console.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 정보센터 카드뉴스 게시글 (1건)
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class CardNews {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 주제 (예: 이란 전쟁 주식시장에 미칠 영향은) */
    @Column(nullable = false)
    private String topic;

    /** 생성 날짜 */
    @Column(nullable = false)
    private LocalDate createdDate;

    /**
     * 파이썬 폴더명 (예: output_2026-03-05_이란_전쟁...)
     * 중복 임포트 방지용 유니크 키
     */
    @Column(unique = true, nullable = false)
    private String folderId;

    /** 인스타그램 업로드용 자동 생성 캡션 */
    @Column(columnDefinition = "TEXT")
    private String instagramCaption;

    /** 카드 페이지 목록 (1:N) */
    @BatchSize(size = 20)
    @OneToMany(mappedBy = "cardNews", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("pageNum ASC")
    private List<CardPage> pages = new ArrayList<>();

    /** 좋아요(추천) 누른 회원 (N:M) */
    @BatchSize(size = 20)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "card_news_likes", joinColumns = @JoinColumn(name = "card_news_id"), inverseJoinColumns = @JoinColumn(name = "member_id"))
    private Set<Member> likedMembers = new HashSet<>();

    /** 저장(스크랩) 누른 회원 (N:M) */
    @BatchSize(size = 20)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "card_news_saves", joinColumns = @JoinColumn(name = "card_news_id"), inverseJoinColumns = @JoinColumn(name = "member_id"))
    private Set<Member> savedMembers = new HashSet<>();

    public static CardNews of(String topic, LocalDate createdDate, String folderId, String instagramCaption) {
        CardNews cn = new CardNews();
        cn.topic = topic;
        cn.createdDate = createdDate;
        cn.folderId = folderId;
        cn.instagramCaption = instagramCaption;
        return cn;
    }
}
