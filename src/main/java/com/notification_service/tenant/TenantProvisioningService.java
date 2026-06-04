package com.notification_service.tenant;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TenantProvisioningService {

    private final JdbcTemplate masterJdbc;
    private final ConcurrentMap<String, DataSource> dataSources = new ConcurrentHashMap<>();

    private final String masterUrl;
    private final String masterUsername;
    private final String masterPassword;

    public TenantProvisioningService(DataSource masterDataSource, String masterUrl, String masterUsername, String masterPassword) {
        this.masterJdbc = new JdbcTemplate(masterDataSource);
        this.masterUrl = masterUrl;
        this.masterUsername = masterUsername;
        this.masterPassword = masterPassword;
    }

    public void preloadExistingTenants() {
        List<Map<String, Object>> rows = masterJdbc.queryForList("SELECT tenant_id FROM tenants WHERE is_active = TRUE");
        for (Map<String, Object> row : rows) {
            String tenantId = (String) row.get("tenant_id");
            getOrCreateDataSource(tenantId);
        }
        log.info("Preloaded {} existing tenants from the master DB.", rows.size());
    }

    public DataSource getOrCreateDataSource(String tenantId) {
        return dataSources.computeIfAbsent(tenantId, this::provisionTenant);
    }

    private DataSource provisionTenant(String tenantId) {
        String dbUrl = null;
        String dbUsername = null;
        String dbPassword = null;

        List<Map<String, Object>> rows = masterJdbc.queryForList(
                "SELECT db_url, db_username, db_password FROM tenants WHERE tenant_id = ? AND is_active = TRUE",
                tenantId);

        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            dbUrl = (String) row.get("db_url");
            dbUsername = (String) row.get("db_username");
            dbPassword = (String) row.get("db_password");
            log.info("Loaded credentials for tenant [{}] from master DB.", tenantId);
        } else {
            dbUrl = extractBaseUrl(masterUrl) + "/" + tenantId;
            dbUsername = masterUsername;
            dbPassword = masterPassword;

            log.info("Tenant [{}] not found. Auto-registering with URL [{}]", tenantId, dbUrl);
            masterJdbc.update(
                    "INSERT INTO tenants (id, tenant_id, db_url, db_username, db_password, is_active, created_at) VALUES (gen_random_uuid(), ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP)",
                    tenantId, dbUrl, dbUsername, dbPassword);
        }

        createDatabaseIfNotExists(dbUrl, dbUsername, dbPassword, tenantId);

        HikariDataSource dataSource = buildDataSource(tenantId, dbUrl, dbUsername, dbPassword);
        runTenantMigration(dataSource, tenantId);

        return dataSource;
    }

    private String extractBaseUrl(String url) {
        int lastSlash = url.lastIndexOf('/');
        return url.substring(0, lastSlash);
    }

    private void createDatabaseIfNotExists(String jdbcUrl, String username, String password, String tenantId) {
        String dbName = extractDatabaseName(jdbcUrl);
        String adminUrl = buildAdminUrl(jdbcUrl);

        try (Connection conn = DriverManager.getConnection(adminUrl, username, password)) {
            try (PreparedStatement checkStmt = conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
                checkStmt.setString(1, dbName);

                if (!checkStmt.executeQuery().next()) {
                    try (Statement createStmt = conn.createStatement()) {
                        createStmt.execute("CREATE DATABASE \"" + dbName + "\"");
                        log.info("Auto-created database [{}] for tenant [{}]", dbName, tenantId);
                    }
                } else {
                    log.debug("Database [{}] already exists for tenant [{}]", dbName, tenantId);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to auto-create database for tenant [" + tenantId + "]: " + e.getMessage(), e);
        }
    }

    private String extractDatabaseName(String jdbcUrl) {
        String afterScheme = jdbcUrl.substring("jdbc:postgresql://".length());
        String path = afterScheme.substring(afterScheme.indexOf('/') + 1);
        return path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
    }

    private String buildAdminUrl(String jdbcUrl) {
        int lastSlash = jdbcUrl.lastIndexOf('/');
        String base = jdbcUrl.substring(0, lastSlash);
        String suffix = jdbcUrl.substring(lastSlash + 1);
        String queryParams = suffix.contains("?") ? "?" + suffix.substring(suffix.indexOf('?') + 1) : "";
        return base + "/postgres" + queryParams;
    }

    private HikariDataSource buildDataSource(String tenantId, String url, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("tenant-pool-" + tenantId);
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(30_000);
        config.setIdleTimeout(600_000);
        return new HikariDataSource(config);
    }

    private void runTenantMigration(DataSource dataSource, String tenantId) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/tenant")
                .load();
        flyway.migrate();
        log.info("Flyway tenant migration completed for: [{}]", tenantId);
    }
}
