package com.pj.stock_console.controller;

import com.pj.stock_console.entity.Member;
import com.pj.stock_console.entity.Notification;
import com.pj.stock_console.service.NotificationService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private static final String LOGIN_MEMBER = "loginMember";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("MM/dd HH:mm");

    /**
     * 미읽음 알림 수 조회
     */
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(HttpSession session) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null) return ResponseEntity.ok(Map.of("count", 0));
        long count = notificationService.getUnreadCount(loginMember.getId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * 내 알림 목록 조회 (최근 20개)
     */
    @GetMapping
    public ResponseEntity<?> getNotifications(HttpSession session) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null) return ResponseEntity.ok(List.of());

        List<Notification> notifications = notificationService.getMyNotifications(loginMember.getId());
        List<Map<String, Object>> result = notifications.stream()
                .limit(20)
                .map(n -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", n.getId());
                    map.put("title", n.getTitle() != null ? n.getTitle() : "알림");
                    map.put("message", n.getMessage() != null ? n.getMessage() : "");
                    map.put("link", n.getLink()); // 기본값 없이 그대로 (null 포함)
                    map.put("isRead", n.isRead());
                    map.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt().format(FORMATTER) : "방금 전");
                    return map;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * 단건 읽음 처리
     */
    @PostMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable("id") Long id, HttpSession session) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null) return ResponseEntity.status(401).build();
        notificationService.markAsRead(id, loginMember.getId());
        return ResponseEntity.ok().build();
    }

    /**
     * 전체 읽음 및 숨김 처리
     */
    @PostMapping("/read-all")
    public ResponseEntity<?> markAllAsRead(HttpSession session) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null) return ResponseEntity.status(401).build();
        notificationService.markAllAsRead(loginMember.getId());
        return ResponseEntity.ok().build();
    }

    /**
     * 개별 숨김 처리
     */
    @PostMapping("/{id}/hide")
    public ResponseEntity<?> hideNotification(@PathVariable("id") Long id, HttpSession session) {
        Member loginMember = (Member) session.getAttribute(LOGIN_MEMBER);
        if (loginMember == null) return ResponseEntity.status(401).build();
        notificationService.hideNotification(id, loginMember.getId());
        return ResponseEntity.ok().build();
    }
}
