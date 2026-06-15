package com.notification_service.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.notification_service.entity.Notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPushDTO {

    private UUID id;

    private String sourceSystem;

    private String recipientUserId;

    private String message;

    private String actionUrl;

    private boolean isRead;

    private LocalDateTime createdAt;

    private String senderUserId;

    private String senderDisplayName;

    private String senderAvatarUrl;


    public static NotificationPushDTO fromEntity(Notification notification) {
        return NotificationPushDTO.builder()
                .id(notification.getId())
                .sourceSystem(notification.getSourceSystem())
                .recipientUserId(notification.getRecipientUserId())
                .message(notification.getMessage())
                .actionUrl(notification.getActionUrl())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .senderUserId(notification.getSenderUserId())
                .senderDisplayName(notification.getSenderDisplayName())
                .senderAvatarUrl(notification.getSenderAvatarUrl())
                .build();
    }

    public static NotificationPushDTO fromEvent(NotificationEventDTO event, String userId) {
        SenderInfoDTO sender = event.getSenderInfo();
        return NotificationPushDTO.builder()
                .sourceSystem(event.getSourceSystem())
                .recipientUserId(userId)
                .message(event.getMessage())
                .actionUrl(event.getActionUrl())
                .senderUserId(sender != null ? sender.getUserId() : null)
                .senderDisplayName(sender != null ? sender.getDisplayName() : null)
                .senderAvatarUrl(sender != null ? sender.getAvatarUrl() : null)
                .build();
    }
}
