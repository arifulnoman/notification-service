package com.notification_service.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.notification_service.dto.NotificationEventDTO;
import com.notification_service.dto.NotificationPushDTO;
import com.notification_service.dto.NotificationResponse;
import com.notification_service.entity.Notification;
import com.notification_service.repository.NotificationRepository;
import com.notification_service.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(rollbackFor = Exception.class)
    public void processNotificationEvent(NotificationEventDTO event) {
        List<String> recipients = event.getRecipientUserIds();

        if (recipients == null || recipients.isEmpty()) {
            log.warn("Notification event from [{}] tenant=[{}] has no recipients — skipping",
                    event.getSourceSystem(), event.getTenantId());
            return;
        }

        for (String userId : recipients) {
            processForUser(event, userId);
        }
    }

    private void processForUser(NotificationEventDTO event, String userId) {
        if (event.isPersistNotification()) {
            Notification saved = saveNotification(event, userId);
            pushNotificationAndCount(NotificationPushDTO.fromEntity(saved), userId);
        } else {
            pushNotificationAndCount(NotificationPushDTO.fromEvent(event, userId), userId);
        }
    }

    private Notification saveNotification(NotificationEventDTO event, String userId) {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .sourceSystem(event.getSourceSystem())
                .recipientUserId(userId)
                .message(event.getMessage())
                .actionUrl(event.getActionUrl())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        return notificationRepository.save(notification);
    }

    /**
     * The WebSocket push principal is "tenantId:userId" (composite) so two separate
     * client deployments sharing the same userId never receive each other's pushes.
     */
    private void pushNotificationAndCount(NotificationPushDTO push, String userId) {
        long unreadCount = notificationRepository.countByRecipientUserIdAndIsRead(userId, false);

        Map<String, Object> payload = new HashMap<>();
        payload.put("unreadCount", unreadCount);
        if (push != null) {
            payload.put("notification", push);
        }

        String tenantId = TenantContext.getTenantId();
        String principal = tenantId + ":" + userId;
        messagingTemplate.convertAndSendToUser(principal, "/queue/notifications", payload);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotifications(
            String userId, boolean unreadOnly, Pageable pageable) {

        Page<Notification> page;

        if (unreadOnly) {
            page = notificationRepository
                    .findByRecipientUserIdAndIsReadOrderByCreatedAtDesc(userId, false, pageable);
        } else {
            page = notificationRepository
                    .findByRecipientUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        return page.map(NotificationResponse::fromEntity);
    }

    @Transactional
    public void markAsRead(UUID id) {
        notificationRepository.findById(id).ifPresent(notification -> {
            notification.setRead(true);
            notificationRepository.save(notification);
            pushNotificationAndCount(null, notification.getRecipientUserId());
        });
    }

    @Transactional
    public void markAllAsRead(String userId) {
        int updatedCount = notificationRepository.markAllAsReadByUserId(userId);
        if (updatedCount > 0) {
            pushNotificationAndCount(null, userId);
        }
    }
}

