package com.pj.stock_console.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Getter @Setter
public class Notice {
    
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false, length = 50)
    private String author;

    private int viewCount = 0;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt;

    // [추가] 중복 투표 방지를 위해 누른 유저의 닉네임을 Set으로 저장
    @ElementCollection
    private Set<String> likedUsers = new HashSet<>();

    @ElementCollection
    private Set<String> dislikedUsers = new HashSet<>();

    // [추가] 게시글 삭제 시 댓글도 자동 삭제(Cascade)되도록 설정
    @OneToMany(mappedBy = "notice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    @Column(nullable = false)
    private boolean pinned = false; // 상단 고정 여부
}