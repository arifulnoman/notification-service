package com.notification_service.tenant;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Routes every JPA/JDBC connection to the correct per-tenant DataSource
 * by reading the current thread's tenant from TenantContext.
 */
public class TenantAwareDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getTenantId();
    }
}
