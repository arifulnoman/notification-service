package com.notification_service.registry;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLookupUrlRequest {

    @NotBlank(message = "userLookupUrl is required")
    private String userLookupUrl;

    /**
     * Required — sent as the "X-Internal-Api-Key" header on every call to userLookupUrl.
     * A lookup endpoint with no key on the call would be reachable unauthenticated, so this
     * registry never stores one without the other.
     */
    @NotBlank(message = "userLookupApiKey is required")
    private String userLookupApiKey;
}
