package com.pj.stock_console.repository;

import com.pj.stock_console.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByRecipientIdAndIsHiddenFalseOrderByCreatedAtDesc(Long recipientId);

    long countByRecipientIdAndIsReadFalseAndIsHiddenFalse(Long recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.isHidden = true WHERE n.recipient.id = :memberId AND n.isHidden = false")
    void markAllAsReadAndHideByRecipientId(@Param("memberId") Long memberId);

    @Modifying
    @Query("UPDATE Notification n SET n.isHidden = true WHERE n.id = :id AND n.recipient.id = :memberId")
    void hideNotification(@Param("id") Long id, @Param("memberId") Long memberId);
}
