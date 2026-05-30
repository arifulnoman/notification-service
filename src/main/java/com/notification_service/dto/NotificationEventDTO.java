package com.notification_service.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEventDTO {

    private String sourceSystem;

    private List<String> recipientUserIds;

    private String message;

    private String actionUrl;

    private boolean persistNotification;
}
