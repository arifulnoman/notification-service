package com.notification_service.config;

import java.util.Collections;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;

import com.notification_service.tenant.TenantAwareDataSource;
import com.notification_service.tenant.TenantProvisioningService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@DependsOn("masterFlyway")
public class MultiTenantDataSourceConfig {

    @Value("${master.datasource.url}")
    private String masterUrl;

    @Value("${master.datasource.username}")
    private String masterUsername;

    @Value("${master.datasource.password}")
    private String masterPassword;

    @Bean
    @Primary
    DataSource tenantAwareDataSource(@Qualifier("masterDataSource") DataSource masterDataSource) {

        log.info("Initializing lazy TenantAwareDataSource...");

        TenantProvisioningService provisioningService = new TenantProvisioningService(
                masterDataSource, masterUrl, masterUsername, masterPassword);

        provisioningService.preloadExistingTenants();

        TenantAwareDataSource routingDataSource = new TenantAwareDataSource();
        routingDataSource.setTargetDataSources(Collections.emptyMap());
        routingDataSource.setDefaultTargetDataSource(masterDataSource);
        routingDataSource.setTenantProvisioningService(provisioningService);
        
        return routingDataSource;
    }
}
