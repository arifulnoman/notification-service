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
public class NotificationResponse {

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

    public static NotificationResponse fromEntity(Notification notification) {
        return NotificationResponse.builder()
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
}
