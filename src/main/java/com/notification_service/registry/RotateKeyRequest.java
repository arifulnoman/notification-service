package com.notification_service.registry;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RotateKeyRequest {

    @NotBlank(message = "jwtPublicKey is required")
    private String jwtPublicKey;
}
