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

    /** Optional — sent as the "X-Internal-Api-Key" header on every call to userLookupUrl. */
    private String userLookupApiKey;
}
