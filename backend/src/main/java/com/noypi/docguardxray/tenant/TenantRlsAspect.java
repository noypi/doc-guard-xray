package com.noypi.docguardxray.tenant;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect 
@Component 
public class TenantRlsAspect {
    
    private final DataSource dataSource;

    public TenantRlsAspect(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Before("execution(* com.noypi.docguardxray..*Repository.*(..))")
    public void applyTenantSession() {
        TenantContext.getTenantId().ifPresent(tenantId -> {
            try (var conn = dataSource.getConnection();
                var stmt = conn.prepareStatement("SELECT set_config('app.current_tenant_id', ?, true)")) {
                stmt.setString(1, tenantId.toString());
                stmt.execute();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to set tenant ID in session", e);
            }
        });
    }
}
