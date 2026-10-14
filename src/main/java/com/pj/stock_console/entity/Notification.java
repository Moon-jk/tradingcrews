package com.pj.stock_console.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 알림을 받을 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private Member recipient;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    // 클릭 시 이동할 링크 (선택)
    @Column
    private String link;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean isRead = false;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean isHidden = false;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public static Notification create(Member recipient, String title, String message, String link) {
        Notification n = new Notification();
        n.recipient = recipient;
        n.title = title;
        n.message = message;
        n.link = link;
        return n;
    }
}
