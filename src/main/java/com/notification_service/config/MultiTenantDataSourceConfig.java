package com.notification_service.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import com.notification_service.tenant.TenantAwareDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import lombok.extern.slf4j.Slf4j;

/**
 * Reads all active tenants from the master DB and builds a per-tenant HikariCP
 * DataSource for each one. The resulting TenantAwareDataSource is registered
 * as @Primary so all JPA repositories transparently route to the correct
 * tenant database based on TenantContext.
 *
 * On startup for each tenant:
 *   1. Auto-creates the tenant DB if it does not exist (PostgreSQL)
 *   2. Builds a lean HikariCP connection pool
 *   3. Runs Flyway migrations against the tenant DB
 */
@Slf4j
@Configuration
@DependsOn("masterFlyway")
public class MultiTenantDataSourceConfig {

    private static final String TENANT_QUERY =
            "SELECT tenant_id, db_url, db_username, db_password " +
            "FROM tenants WHERE is_active = TRUE";

    @Bean
    @Primary
    DataSource tenantAwareDataSource(
            @Qualifier("masterJdbcTemplate") JdbcTemplate masterJdbc) {

        List<Map<String, Object>> tenants = masterJdbc.queryForList(TENANT_QUERY);

        if (tenants.isEmpty()) {
            throw new IllegalStateException(
                    "No active tenants found in the master DB. " +
                    "Insert at least one row into the 'tenants' table.");
        }

        Map<Object, Object> targetDataSources = new HashMap<>();

        for (Map<String, Object> row : tenants) {
            String tenantId = (String) row.get("tenant_id");
            String dbUrl = (String) row.get("db_url");
            String dbUsername = (String) row.get("db_username");
            String dbPassword = (String) row.get("db_password");

            createDatabaseIfNotExists(dbUrl, dbUsername, dbPassword, tenantId);

            HikariDataSource dataSource = buildDataSource(tenantId, dbUrl, dbUsername, dbPassword);
            runTenantMigration(dataSource, tenantId);

            targetDataSources.put(tenantId, dataSource);
            log.info("Registered tenant DataSource: [{}]", tenantId);
        }

        DataSource defaultDataSource = (DataSource) targetDataSources.values().iterator().next();

        TenantAwareDataSource routingDataSource = new TenantAwareDataSource();
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(defaultDataSource);
        return routingDataSource;
    }

    private HikariDataSource buildDataSource(
            String tenantId, String url, String username, String password) {

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("tenant-pool-" + tenantId);
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(10);   // reduce to 5 if tenant count exceeds 30
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

    /**
     * Connects to the PostgreSQL 'postgres' maintenance database and creates
     * the tenant database if it does not yet exist. Safe to call on every startup —
     * it checks pg_database before issuing CREATE DATABASE.
     */
    private void createDatabaseIfNotExists(
            String jdbcUrl, String username, String password, String tenantId) {

        String dbName = extractDatabaseName(jdbcUrl);
        String adminUrl = buildAdminUrl(jdbcUrl);

        try (Connection conn = DriverManager.getConnection(adminUrl, username, password)) {
            try (PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT 1 FROM pg_database WHERE datname = ?")) {
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
            throw new RuntimeException(
                    "Failed to auto-create database for tenant [" + tenantId + "]: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts the database name from a JDBC URL.
     * e.g. "jdbc:postgresql://localhost:5432/acme_db" → "acme_db"
     */
    private String extractDatabaseName(String jdbcUrl) {
        String afterScheme = jdbcUrl.substring("jdbc:postgresql://".length());
        String path = afterScheme.substring(afterScheme.indexOf('/') + 1);
        return path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
    }

    /**
     * Replaces the tenant database name with "postgres" (PostgreSQL maintenance DB)
     * so we can issue administrative commands like CREATE DATABASE.
     * e.g. "jdbc:postgresql://localhost:5432/acme_db" → "jdbc:postgresql://localhost:5432/postgres"
     */
    private String buildAdminUrl(String jdbcUrl) {
        int lastSlash = jdbcUrl.lastIndexOf('/');
        String base = jdbcUrl.substring(0, lastSlash);
        String suffix = jdbcUrl.substring(lastSlash + 1);
        String queryParams = suffix.contains("?") ? "?" + suffix.substring(suffix.indexOf('?') + 1) : "";
        return base + "/postgres" + queryParams;
    }
}
