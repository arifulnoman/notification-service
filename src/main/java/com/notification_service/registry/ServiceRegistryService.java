package com.notification_service.registry;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Self-service registry for the systems that publish notifications to CNS
 * (ERP, HRMS, or any future integration). Keyed by (tenantId, sourceSystem):
 * each tenant runs its own separate instance of a given source system, with
 * its own JWT signing key and its own user directory, so "erp" for tenant A
 * and "erp" for tenant B are two independent registrations.
 *
 * Registering a service:
 *   1. persists it in the master DB (survives restarts)
 *   2. declares its RabbitMQ queue + DLX binding
 *   3. starts a listener container for it
 * all at runtime — onboarding a new integration never requires editing
 * this service's config or redeploying it. See {@link DynamicNotificationListenerRegistrar}.
 */
@Slf4j
@Service
@DependsOn("masterFlyway")
public class ServiceRegistryService {

    private final JdbcTemplate masterJdbc;
    private final AmqpAdmin amqpAdmin;
    private final DirectExchange exchange;
    private final String deadLetterQueueName;
    private final DynamicNotificationListenerRegistrar listenerRegistrar;

    private final ConcurrentMap<String, RegisteredService> cache = new ConcurrentHashMap<>();

    public ServiceRegistryService(
            @Qualifier("masterJdbcTemplate") JdbcTemplate masterJdbc,
            AmqpAdmin amqpAdmin,
            DirectExchange exchange,
            @Value("${rabbitmq.queue.dead-letter}") String deadLetterQueueName,
            DynamicNotificationListenerRegistrar listenerRegistrar) {
        this.masterJdbc = masterJdbc;
        this.amqpAdmin = amqpAdmin;
        this.exchange = exchange;
        this.deadLetterQueueName = deadLetterQueueName;
        this.listenerRegistrar = listenerRegistrar;
    }

    @PostConstruct
    public void preloadRegisteredServices() {
        List<RegisteredService> services = masterJdbc.query(
                "SELECT * FROM registered_services WHERE is_active = TRUE",
                ServiceRegistryService::mapRow);
        services.forEach(this::activate);
        log.info("Preloaded {} registered service(s) from the registry.", services.size());
    }

    public RegisteredService register(RegisterServiceRequest request) {
        String tenantId = request.getTenantId().trim();
        String sourceSystem = normalize(request.getSourceSystem());
        String queueName = StringUtils.hasText(request.getQueueName())
                ? request.getQueueName().trim()
                : tenantId + "." + sourceSystem + ".notification.queue";

        if (StringUtils.hasText(request.getUserLookupUrl()) && !StringUtils.hasText(request.getUserLookupApiKey())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "userLookupApiKey is required when userLookupUrl is set");
        }

        if (findByTenantAndSourceSystem(tenantId, sourceSystem).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Service [" + sourceSystem + "] for tenant [" + tenantId + "] is already registered");
        }

        LocalDateTime now = LocalDateTime.now();
        RegisteredService service = RegisteredService.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .sourceSystem(sourceSystem)
                .jwtPublicKey(request.getJwtPublicKey())
                .queueName(queueName)
                .userLookupUrl(request.getUserLookupUrl())
                .userLookupApiKey(request.getUserLookupApiKey())
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        try {
            masterJdbc.update(
                    "INSERT INTO registered_services " +
                    "(id, tenant_id, source_system, jwt_public_key, queue_name, user_lookup_url, user_lookup_api_key, is_active, created_at, updated_at) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?)",
                    service.getId(), service.getTenantId(), service.getSourceSystem(), service.getJwtPublicKey(),
                    service.getQueueName(), service.getUserLookupUrl(), service.getUserLookupApiKey(), service.isActive(),
                    service.getCreatedAt(), service.getUpdatedAt());
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Service [" + sourceSystem + "] for tenant [" + tenantId + "], or queue [" + queueName
                            + "], is already registered", e);
        }

        activate(service);
        log.info("Registered new service [{}] for tenant [{}] -> queue [{}]", sourceSystem, tenantId, queueName);
        return service;
    }

    public List<RegisteredService> listAll() {
        return masterJdbc.query(
                "SELECT * FROM registered_services ORDER BY created_at DESC", ServiceRegistryService::mapRow);
    }

    public Optional<RegisteredService> findByTenantAndSourceSystem(String tenantId, String sourceSystem) {
        String key = cacheKey(tenantId, sourceSystem);
        RegisteredService cached = cache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        return masterJdbc.query(
                        "SELECT * FROM registered_services WHERE tenant_id = ? AND source_system = ?",
                        ServiceRegistryService::mapRow, tenantId, sourceSystem)
                .stream().findFirst();
    }

    public RegisteredService rotateKey(String tenantId, String sourceSystem, String newPublicKey) {
        RegisteredService service = requireActive(tenantId, sourceSystem);
        masterJdbc.update(
                "UPDATE registered_services SET jwt_public_key = ?, updated_at = ? WHERE tenant_id = ? AND source_system = ?",
                newPublicKey, LocalDateTime.now(), tenantId, sourceSystem);
        service.setJwtPublicKey(newPublicKey);
        cache.put(cacheKey(tenantId, sourceSystem), service);
        log.info("Rotated JWT public key for service [{}] tenant [{}]", sourceSystem, tenantId);
        return service;
    }

    public RegisteredService setUserLookupUrl(
            String tenantId, String sourceSystem, String userLookupUrl, String userLookupApiKey) {
        RegisteredService service = requireActive(tenantId, sourceSystem);
        masterJdbc.update(
                "UPDATE registered_services SET user_lookup_url = ?, user_lookup_api_key = ?, updated_at = ? " +
                "WHERE tenant_id = ? AND source_system = ?",
                userLookupUrl, userLookupApiKey, LocalDateTime.now(), tenantId, sourceSystem);
        service.setUserLookupUrl(userLookupUrl);
        service.setUserLookupApiKey(userLookupApiKey);
        cache.put(cacheKey(tenantId, sourceSystem), service);
        log.info("Updated user-lookup URL for service [{}] tenant [{}]", sourceSystem, tenantId);
        return service;
    }

    public void deactivate(String tenantId, String sourceSystem) {
        RegisteredService service = requireActive(tenantId, sourceSystem);
        masterJdbc.update(
                "UPDATE registered_services SET is_active = FALSE, updated_at = ? WHERE tenant_id = ? AND source_system = ?",
                LocalDateTime.now(), tenantId, sourceSystem);
        cache.remove(cacheKey(tenantId, sourceSystem));
        listenerRegistrar.stopListener(service.getQueueName());
        log.info("Deactivated service [{}] tenant [{}] — queue [{}] left in place but no longer consumed",
                sourceSystem, tenantId, service.getQueueName());
    }

    public RegisteredService reactivate(String tenantId, String sourceSystem) {
        RegisteredService service = findByTenantAndSourceSystem(tenantId, sourceSystem)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Service [" + sourceSystem + "] for tenant [" + tenantId + "] is not registered"));
        if (service.isActive()) {
            return service;
        }
        masterJdbc.update(
                "UPDATE registered_services SET is_active = TRUE, updated_at = ? WHERE tenant_id = ? AND source_system = ?",
                LocalDateTime.now(), tenantId, sourceSystem);
        service.setActive(true);
        activate(service);
        log.info("Reactivated service [{}] tenant [{}] — queue [{}] listener restarted",
                sourceSystem, tenantId, service.getQueueName());
        return service;
    }

    private RegisteredService requireActive(String tenantId, String sourceSystem) {
        return findByTenantAndSourceSystem(tenantId, sourceSystem)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Service [" + sourceSystem + "] for tenant [" + tenantId + "] is not registered"));
    }

    private void activate(RegisteredService service) {
        Queue queue = QueueBuilder.durable(service.getQueueName())
                .withArgument("x-dead-letter-exchange", exchange.getName())
                .withArgument("x-dead-letter-routing-key", deadLetterQueueName)
                .build();
        amqpAdmin.declareQueue(queue);

        Binding binding = BindingBuilder.bind(queue).to(exchange).with(service.getQueueName());
        amqpAdmin.declareBinding(binding);

        listenerRegistrar.registerListenerFor(service.getQueueName());
        cache.put(cacheKey(service.getTenantId(), service.getSourceSystem()), service);
    }

    private static String normalize(String sourceSystem) {
        return sourceSystem.trim().toLowerCase();
    }

    /**
     * tenantId is kept exactly as given (only trimmed, never case-folded) because
     * it must match verbatim against the tenantId claim on incoming JWTs and the
     * tenantId field on incoming NotificationEventDTOs — both are treated as
     * case-sensitive everywhere else in this codebase.
     */
    private static String cacheKey(String tenantId, String sourceSystem) {
        return tenantId + "::" + sourceSystem;
    }

    private static RegisteredService mapRow(ResultSet rs, int rowNum) throws SQLException {
        return RegisteredService.builder()
                .id(UUID.fromString(rs.getString("id")))
                .tenantId(rs.getString("tenant_id"))
                .sourceSystem(rs.getString("source_system"))
                .jwtPublicKey(rs.getString("jwt_public_key"))
                .queueName(rs.getString("queue_name"))
                .userLookupUrl(rs.getString("user_lookup_url"))
                .userLookupApiKey(rs.getString("user_lookup_api_key"))
                .active(rs.getBoolean("is_active"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                .build();
    }
}
