package com.notification_service.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.notification_service.registry.RegisterServiceRequest;
import com.notification_service.registry.RegisteredService;
import com.notification_service.registry.RotateKeyRequest;
import com.notification_service.registry.ServiceRegistryService;
import com.notification_service.registry.UserLookupUrlRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Self-service onboarding for systems that publish notifications through CNS.
 * Guarded by {@code AdminApiKeyFilter} (a separate admin credential), not the
 * shared end-user JWT — see SecurityConfig.
 *
 * Registrations are keyed by (tenantId, sourceSystem): each tenant runs its
 * own separate instance of a given source system, so both path segments are
 * required on every sub-resource operation below.
 */
@RestController
@RequestMapping("/api/admin/services")
@RequiredArgsConstructor
public class ServiceRegistryController {

    private final ServiceRegistryService serviceRegistryService;

    @PostMapping
    public ResponseEntity<RegisteredService> register(@Valid @RequestBody RegisterServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceRegistryService.register(request));
    }

    @GetMapping
    public ResponseEntity<List<RegisteredService>> listAll() {
        return ResponseEntity.ok(serviceRegistryService.listAll());
    }

    @PutMapping("/{tenantId}/{sourceSystem}/jwt-public-key")
    public ResponseEntity<RegisteredService> rotateKey(
            @PathVariable String tenantId, @PathVariable String sourceSystem,
            @Valid @RequestBody RotateKeyRequest request) {
        return ResponseEntity.ok(serviceRegistryService.rotateKey(tenantId, sourceSystem, request.getJwtPublicKey()));
    }

    @PutMapping("/{tenantId}/{sourceSystem}/user-lookup-url")
    public ResponseEntity<RegisteredService> setUserLookupUrl(
            @PathVariable String tenantId, @PathVariable String sourceSystem,
            @Valid @RequestBody UserLookupUrlRequest request) {
        return ResponseEntity.ok(serviceRegistryService.setUserLookupUrl(
                tenantId, sourceSystem, request.getUserLookupUrl(), request.getUserLookupApiKey()));
    }

    @DeleteMapping("/{tenantId}/{sourceSystem}")
    public ResponseEntity<Void> deactivate(@PathVariable String tenantId, @PathVariable String sourceSystem) {
        serviceRegistryService.deactivate(tenantId, sourceSystem);
        return ResponseEntity.noContent().build();
    }
}
