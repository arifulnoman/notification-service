package com.notification_service.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import lombok.extern.slf4j.Slf4j;

/**
 * Configures the master DataSource — the single central database that stores
 * per-tenant connection information (tenant_id, db_url, db_username, db_password).
 */
@Slf4j
@Configuration
public class MasterDataSourceConfig {

    @Value("${master.datasource.url}")
    private String url;

    @Value("${master.datasource.username}")
    private String username;

    @Value("${master.datasource.password}")
    private String password;

    @Bean("masterDataSource")
    DataSource masterDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("master-pool");
        config.setMinimumIdle(1);
        config.setMaximumPoolSize(5);
        return new HikariDataSource(config);
    }

    @Bean("masterJdbcTemplate")
    JdbcTemplate masterJdbcTemplate(DataSource masterDataSource) {
        return new JdbcTemplate(masterDataSource);
    }

    /**
     * Runs Flyway migrations against the master DB on startup,
     * creating the tenants table if it does not yet exist.
     */
    @Bean("masterFlyway")
    Flyway masterFlyway(DataSource masterDataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(masterDataSource)
                .locations("classpath:db/master-migration")
                .load();
        flyway.migrate();
        log.info("Master DB Flyway migration completed.");
        return flyway;
    }
}
