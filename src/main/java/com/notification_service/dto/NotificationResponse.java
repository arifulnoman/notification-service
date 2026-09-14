package com.notification_service.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.util.StringUtils;

import com.notification_service.entity.Notification;
import com.notification_service.registry.ResolvedSenderInfo;

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
        return fromEntity(notification, null);
    }

    /**
     * @param freshSenderInfo the sender's current display name/avatar, resolved live via
     *                        SenderProfileResolver; null (or blank fields within it) falls
     *                        back to the snapshot stored on the notification at creation time.
     */
    public static NotificationResponse fromEntity(Notification notification, ResolvedSenderInfo freshSenderInfo) {
        String freshDisplayName = freshSenderInfo != null ? freshSenderInfo.getDisplayName() : null;
        String freshAvatarUrl = freshSenderInfo != null ? freshSenderInfo.getAvatarUrl() : null;

        return NotificationResponse.builder()
                .id(notification.getId())
                .sourceSystem(notification.getSourceSystem())
                .recipientUserId(notification.getRecipientUserId())
                .message(notification.getMessage())
                .actionUrl(notification.getActionUrl())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .senderUserId(notification.getSenderUserId())
                .senderDisplayName(StringUtils.hasText(freshDisplayName)
                        ? freshDisplayName : notification.getSenderDisplayName())
                .senderAvatarUrl(StringUtils.hasText(freshAvatarUrl)
                        ? freshAvatarUrl : notification.getSenderAvatarUrl())
                .build();
    }
}
