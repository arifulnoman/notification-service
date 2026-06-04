package com.notification_service.tenant;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Routes every JPA/JDBC connection to the correct per-tenant DataSource
 * by reading the current thread's tenant from TenantContext.
 * Automatically provisions new tenants on-the-fly via TenantProvisioningService.
 */
public class TenantAwareDataSource extends AbstractRoutingDataSource {

    private TenantProvisioningService tenantProvisioningService;

    public void setTenantProvisioningService(TenantProvisioningService tenantProvisioningService) {
        this.tenantProvisioningService = tenantProvisioningService;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getTenantId();
    }

    @Override
    protected DataSource determineTargetDataSource() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            // No tenant specified, return the default (master) database
            return (DataSource) getResolvedDefaultDataSource();
        }
        
        // Dynamically get or create the tenant's connection pool
        return tenantProvisioningService.getOrCreateDataSource(tenantId);
    }
}
