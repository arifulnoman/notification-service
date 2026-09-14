package com.notification_service.registry;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves a sender's *current* display name/avatar by calling the
 * registered service's userLookupUrl (see RegisterServiceRequest) — the
 * fix for notifications otherwise freezing a stale avatar/name at the
 * moment they were created.
 *
 * Deliberately best-effort and never notification-service's job to keep
 * fresh on its own: no lookup URL registered, a timeout, a non-2xx, or a
 * malformed response all just fall back to the stored snapshot rather than
 * failing the request. This keeps CNS agnostic — it never needs code
 * changes to support a new integration's identity system, only a URL.
 *
 * Looked up by (tenantId, sourceSystem), matching how services register —
 * each tenant's deployment of a source system has its own lookup URL.
 */
@Slf4j
@Component
public class SenderProfileResolver {

    private final ServiceRegistryService serviceRegistryService;
    private final RestClient userLookupRestClient;

    public SenderProfileResolver(
            ServiceRegistryService serviceRegistryService,
            @Qualifier("userLookupRestClient") RestClient userLookupRestClient) {
        this.serviceRegistryService = serviceRegistryService;
        this.userLookupRestClient = userLookupRestClient;
    }

    public Map<String, ResolvedSenderInfo> resolve(String tenantId, String sourceSystem, Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        Optional<RegisteredService> service = serviceRegistryService.findByTenantAndSourceSystem(tenantId, sourceSystem);
        if (service.isEmpty() || !StringUtils.hasText(service.get().getUserLookupUrl())) {
            return Map.of();
        }
        String lookupUrl = service.get().getUserLookupUrl();
        String apiKey = service.get().getUserLookupApiKey();

        log.debug("Calling user-lookup [{}] for service [{}] tenant [{}] — {} user id(s)",
                lookupUrl, sourceSystem, tenantId, userIds.size());

        try {
            RestClient.RequestBodySpec request = userLookupRestClient.post()
                    .uri(lookupUrl)
                    .contentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(apiKey)) {
                request = request.header("X-Internal-Api-Key", apiKey);
            }
            Map<String, ResolvedSenderInfo> resolved = request
                    .body(Map.of("userIds", userIds))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, ResolvedSenderInfo>>() { });
            log.debug("User-lookup [{}] for service [{}] tenant [{}] resolved {} of {} requested user id(s)",
                    lookupUrl, sourceSystem, tenantId, resolved != null ? resolved.size() : 0, userIds.size());
            return resolved != null ? resolved : Map.of();
        } catch (RestClientResponseException e) {
            // A response actually came back (auth rejected, 404, 500, ...) — the status code is
            // the single most useful thing here, e.g. 401/403 almost always means userLookupApiKey
            // is missing or wrong on this registration (see PUT .../user-lookup-url).
            log.warn("User-lookup call to [{}] for service [{}] tenant [{}] returned HTTP {} {} — "
                    + "falling back to the stored snapshot. Response body: [{}]",
                    lookupUrl, sourceSystem, tenantId, e.getStatusCode().value(), e.getStatusText(),
                    truncate(e.getResponseBodyAsString()));
            return Map.of();
        } catch (Exception e) {
            // Nothing came back at all — DNS failure, connection refused, timeout, etc.
            log.warn("User-lookup call to [{}] for service [{}] tenant [{}] failed with no response "
                    + "({}: {}) — falling back to the stored snapshot",
                    lookupUrl, sourceSystem, tenantId, e.getClass().getSimpleName(), e.getMessage());
            return Map.of();
        }
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() > 300 ? body.substring(0, 300) + "...(truncated)" : body;
    }
}
