package com.notification_service.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.notification_service.entity.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(
            String recipientUserId, Pageable pageable);

    Page<Notification> findByRecipientUserIdAndIsReadOrderByCreatedAtDesc(
            String recipientUserId, boolean isRead, Pageable pageable);

    long countByRecipientUserIdAndIsRead(String recipientUserId, boolean isRead);
}

