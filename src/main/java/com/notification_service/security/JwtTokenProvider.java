package com.notification_service.security;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Collections;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification_service.registry.RegisteredService;
import com.notification_service.registry.ServiceRegistryService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import lombok.extern.slf4j.Slf4j;

/**
 * Verifies incoming JWTs against the registered public key of the service
 * named in the token's "iss" claim — there is no shared secret. Each
 * service signs its own tokens with its own private key and rotates it
 * independently (PUT /api/admin/services/{sourceSystem}/jwt-public-key)
 * without CNS or any other integration ever changing anything.
 *
 * A token that doesn't name a registered service with a public key is
 * simply rejected — no legacy fallback, since this service predates
 * production traffic and every integration onboards through the registry.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_TENANT_ID = "tenantId";
    private static final String CLAIM_ISSUER = "iss";

    private final ServiceRegistryService serviceRegistryService;
    private final ObjectMapper objectMapper;

    public JwtTokenProvider(ServiceRegistryService serviceRegistryService, ObjectMapper objectMapper) {
        this.serviceRegistryService = serviceRegistryService;
        this.objectMapper = objectMapper;
    }

    public boolean validateToken(String authToken) {
        try {
            parseClaims(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // Expected traffic, not a bug here — a client with an expired/malformed/unregistered
            // token, not a failure in this service — so one line at WARN, no stack trace.
            log.warn("Rejected JWT: {}: {}", e.getClass().getSimpleName(), e.getMessage());
        }
        return false;
    }

    public String getUserId(String token) {
        return parseClaims(token).getSubject();
    }

    public String getTenantId(String token) {
        return parseClaims(token).get(CLAIM_TENANT_ID, String.class);
    }

    public Authentication getAuthentication(String token) {
        String userId = getUserId(token);
        String tenantId = getTenantId(token);
        String principal = tenantId + ":" + userId;
        return new UsernamePasswordAuthenticationToken(principal, token, Collections.emptyList());
    }

    private Claims parseClaims(String token) {
        PublicKey verificationKey = resolveVerificationKey(token);
        return Jwts.parserBuilder()
                .setSigningKey(verificationKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Peeking at the (unverified) "iss"/"tenantId" claims before verification is
     * safe here — they only select which registered key to check the signature
     * against; the signature check right after still fails closed if either
     * claim was forged. Registrations are keyed by (tenantId, sourceSystem), so
     * both are needed to pick the right key — each tenant's deployment of a
     * given source system has its own.
     */
    private PublicKey resolveVerificationKey(String token) {
        JsonNode payload = peekPayloadUnverified(token);
        String issuer = textOrNull(payload, CLAIM_ISSUER);
        String tenantId = textOrNull(payload, CLAIM_TENANT_ID);
        if (!StringUtils.hasText(issuer) || !StringUtils.hasText(tenantId)) {
            throw new JwtException(
                    "Token must carry both \"iss\" (sourceSystem) and \"tenantId\" claims to select a verification key");
        }

        RegisteredService service = serviceRegistryService.findByTenantAndSourceSystem(tenantId, issuer)
                .filter(s -> StringUtils.hasText(s.getJwtPublicKey()))
                .orElseThrow(() -> new JwtException(
                        "No JWT public key registered for service [" + issuer + "] tenant [" + tenantId + "]"));

        return toPublicKey(service.getJwtPublicKey());
    }

    private JsonNode peekPayloadUnverified(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] payloadBytes = Decoders.BASE64URL.decode(parts[1]);
            return objectMapper.readTree(payloadBytes);
        } catch (Exception e) {
            return null;
        }
    }

    private String textOrNull(JsonNode payload, String field) {
        if (payload == null) {
            return null;
        }
        JsonNode node = payload.get(field);
        return node != null ? node.asText(null) : null;
    }

    private PublicKey toPublicKey(String pem) {
        try {
            String cleaned = pem
                    .replaceAll("-----BEGIN (.*)-----", "")
                    .replaceAll("-----END (.*)-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Decoders.BASE64.decode(cleaned);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new JwtException("Registered JWT public key is not a valid RSA public key", e);
        }
    }
}
