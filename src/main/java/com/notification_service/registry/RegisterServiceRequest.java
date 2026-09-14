package com.notification_service.registry;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterServiceRequest {

    /** Must exactly match the tenantId this deployment's JWTs and NotificationEventDTOs carry. */
    @NotBlank(message = "tenantId is required")
    private String tenantId;

    @NotBlank(message = "sourceSystem is required")
    private String sourceSystem;

    /** Optional — public key CNS will use to verify this deployment's JWTs (see step 1 in the README). */
    private String jwtPublicKey;

    /** Optional — defaults to "<tenantId>.<sourceSystem>.notification.queue" when omitted. */
    private String queueName;

    /** Optional — endpoint CNS can call to resolve this service's users' current avatar/display name. */
    private String userLookupUrl;

    /** Optional — sent as the "X-Internal-Api-Key" header on every call to userLookupUrl. */
    private String userLookupApiKey;
}
