package com.notification_service.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.notification_service.dto.NotificationEventDTO;
import com.notification_service.dto.NotificationPushDTO;
import com.notification_service.dto.NotificationResponse;
import com.notification_service.entity.Notification;
import com.notification_service.registry.ResolvedSenderInfo;
import com.notification_service.registry.SenderProfileResolver;
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
    private final SenderProfileResolver senderProfileResolver;

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
        com.notification_service.dto.SenderInfoDTO sender = event.getSenderInfo();
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .sourceSystem(event.getSourceSystem())
                .recipientUserId(userId)
                .message(event.getMessage())
                .actionUrl(event.getActionUrl())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .senderUserId(sender != null ? sender.getUserId() : null)
                .senderDisplayName(sender != null ? sender.getDisplayName() : null)
                .senderAvatarUrl(sender != null ? sender.getAvatarUrl() : null)
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

        Map<String, ResolvedSenderInfo> freshSenderInfoByUserId = resolveFreshSenderInfo(page.getContent());
        return page.map(notification ->
                NotificationResponse.fromEntity(notification, freshSenderInfoByUserId.get(notification.getSenderUserId())));
    }

    /**
     * One user-lookup call per source system present on this page (almost
     * always one, at most a handful) instead of one per row — avoids
     * fanning out an HTTP call per notification just to keep avatars fresh.
     *
     * The current tenant is read from TenantContext (set for the duration of
     * this request by JwtAuthenticationFilter) since the registry is keyed
     * by (tenantId, sourceSystem) — each tenant's deployment of a source
     * system has its own lookup URL.
     */
    private Map<String, ResolvedSenderInfo> resolveFreshSenderInfo(List<Notification> notifications) {
        String tenantId = TenantContext.getTenantId();
        Map<String, Set<String>> senderIdsBySourceSystem = notifications.stream()
                .filter(n -> n.getSenderUserId() != null)
                .collect(Collectors.groupingBy(
                        Notification::getSourceSystem,
                        Collectors.mapping(Notification::getSenderUserId, Collectors.toSet())));

        Map<String, ResolvedSenderInfo> merged = new HashMap<>();
        senderIdsBySourceSystem.forEach((sourceSystem, senderIds) ->
                merged.putAll(senderProfileResolver.resolve(tenantId, sourceSystem, senderIds)));
        return merged;
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String userId) {
        return notificationRepository.countByRecipientUserIdAndIsRead(userId, false);
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
    public void deleteNotification(UUID id, String userId) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Notification not found"));

        if (!notification.getRecipientUserId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Cannot delete another user's notification");
        }

        notificationRepository.delete(notification);
        pushNotificationAndCount(null, userId);
    }

    @Transactional
    public void markAllAsRead(String userId) {
        int updatedCount = notificationRepository.markAllAsReadByUserId(userId);
        if (updatedCount > 0) {
            pushNotificationAndCount(null, userId);
        }
    }
}

