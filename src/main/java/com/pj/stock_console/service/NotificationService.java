package com.pj.stock_console.service;

import com.pj.stock_console.entity.Member;
import com.pj.stock_console.entity.Notification;
import com.pj.stock_console.entity.CrewMember;
import com.pj.stock_console.repository.CrewMemberRepository;
import com.pj.stock_console.repository.MemberRepository;
import com.pj.stock_console.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final CrewMemberRepository crewMemberRepository;

    /**
     * 알림 생성
     */
    @Transactional
    public void createNotification(Member recipient, String title, String message, String link) {
        if (recipient == null || recipient.getId() == null) return;
        Notification notification = Notification.create(recipient, title, message, link);
        notificationRepository.save(notification);
    }

    /**
     * 내 알림 목록 조회 (최신순)
     */
    public List<Notification> getMyNotifications(Long memberId) {
        return notificationRepository.findAllByRecipientIdAndIsHiddenFalseOrderByCreatedAtDesc(memberId);
    }

    /**
     * 미읽음 알림 수 조회
     */
    public long getUnreadCount(Long memberId) {
        return notificationRepository.countByRecipientIdAndIsReadFalseAndIsHiddenFalse(memberId);
    }

    /**
     * 단건 읽음 처리
     */
    @Transactional
    public void markAsRead(Long notificationId, Long memberId) {
        if (notificationId == null || memberId == null) return;
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getRecipient().getId().equals(memberId)) {
                n.setRead(true);
            }
        });
    }

    /**
     * 전체 읽음 처리
     */
    @Transactional
    public void markAllAsRead(Long memberId) {
        notificationRepository.markAllAsReadAndHideByRecipientId(memberId);
    }

    @Transactional
    public void hideNotification(Long id, Long memberId) {
        notificationRepository.hideNotification(id, memberId);
    }
    /**
     * 전체 사용자에게 알림 전송 (관리자용)
     */
    @Transactional
    public void sendNotificationToAll(String title, String message, String link) {
        List<Member> members = memberRepository.findAll();
        for (Member member : members) {
            createNotification(member, title, message, link);
        }
    }

    /**
     * 특정 크루 전원에게 알림 전송 (관리자용)
     */
    @Transactional
    public void sendNotificationToCrew(Long crewId, String title, String message, String link) {
        // 크루 멤버들을 조회하여 모두에게 알림 생성
        List<CrewMember> crewMembers = crewMemberRepository.findAll(); // 이 방식보다는 벌크 조회가 나을 듯
        // 하지만 기존 repository에 findAllByCrewId가 없으니 일단 필터링 하거나 추가가 필요
        // 수정: 알림 발송용 간단 조회 메서드 사용
        crewMembers.stream()
            .filter(cm -> cm.getCrew().getId().equals(crewId))
            .forEach(cm -> createNotification(cm.getMember(), title, message, link));
    }
}
