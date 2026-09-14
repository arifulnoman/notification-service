package com.notification_service.registry;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A system (ERP, HRMS, or any future integration) that has registered itself
 * to publish notifications through this service. Stored in the master DB —
 * one row per (tenantId, sourceSystem) pair, since each tenant runs its own
 * separate instance of a given source system with its own JWT signing key
 * and its own user directory.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisteredService {

    private UUID id;

    /** Part of this registration's identity together with sourceSystem — see class javadoc. */
    private String tenantId;

    private String sourceSystem;

    /** Public key used to verify JWTs issued by this service. Stored for a future rollout — not yet enforced. */
    private String jwtPublicKey;

    private String queueName;

    /** Endpoint CNS can call at read time to resolve this service's users' current display info (avatar, name). */
    private String userLookupUrl;

    /** Sent as the "X-Internal-Api-Key" header on every call to userLookupUrl, if set. */
    private String userLookupApiKey;

    private boolean active;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
