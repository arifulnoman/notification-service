package com.notification_service.registry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry of a registered service's user-lookup response — the sender's
 * current display name / avatar, fresh as of the read, not as of whenever
 * the notification was created.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolvedSenderInfo {

    private String displayName;

    private String avatarUrl;
}
