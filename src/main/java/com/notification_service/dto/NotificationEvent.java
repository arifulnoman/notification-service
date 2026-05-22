package com.notification_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {

    private String sourceSystem;
    
    private String recipientUserId;
    
    private String message;
    
    private String actionUrl;
    
    private String type;
}
