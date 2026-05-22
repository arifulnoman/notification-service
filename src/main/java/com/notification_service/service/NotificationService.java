package com.notification_service.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.notification_service.dto.NotificationEvent;
import com.notification_service.dto.NotificationResponse;
import com.notification_service.entity.Notification;
import com.notification_service.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void processNotificationEvent(NotificationEvent event) {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .sourceSystem(event.getSourceSystem())
                .recipientUserId(event.getRecipientUserId())
                .message(event.getMessage())
                .actionUrl(event.getActionUrl())
                .notificationType(event.getType())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        Notification savedNotification = notificationRepository.save(notification);

        // Push real-time notification to user via WebSocket
        pushRealTimeNotification(savedNotification);
    }

    private void pushRealTimeNotification(Notification notification) {
        NotificationResponse response = NotificationResponse.fromEntity(notification);
        messagingTemplate.convertAndSendToUser(
                notification.getRecipientUserId(), 
                "/queue/notifications", 
                response
        );
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotifications(String userId, List<String> sourceSystems, boolean unreadOnly, Pageable pageable) {
        Page<Notification> page;
        
        if (sourceSystems != null && !sourceSystems.isEmpty()) {
            if (unreadOnly) {
                page = notificationRepository.findByRecipientUserIdAndSourceSystemInAndIsReadOrderByCreatedAtDesc(userId, sourceSystems, false, pageable);
            } else {
                page = notificationRepository.findByRecipientUserIdAndSourceSystemInOrderByCreatedAtDesc(userId, sourceSystems, pageable);
            }
        } else {
            if (unreadOnly) {
                page = notificationRepository.findByRecipientUserIdAndIsReadOrderByCreatedAtDesc(userId, false, pageable);
            } else {
                page = notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(userId, pageable);
            }
        }
        return page.map(NotificationResponse::fromEntity);
    }

    @Transactional
    public void markAsRead(UUID id) {
        notificationRepository.findById(id).ifPresent(notification -> {
            notification.setRead(true);
            notificationRepository.save(notification);
        });
    }
}
