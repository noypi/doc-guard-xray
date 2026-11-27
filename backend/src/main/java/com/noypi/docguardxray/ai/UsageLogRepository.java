package com.noypi.docguardxray.ai;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsageLogRepository extends JpaRepository<UsageLog, UUID> {
    List<UsageLog> findTop20ByOrderByCreatedAtDesc();

    @Query ("""
        SELECT COALESCE(SUM(u.tokensUsed), 0)
        FROM UsageLog u
        WHERE u.tenantId = :tenantId
            AND u.createdAt >= :startInclusive
            AND u.createdAt < :endExclusive
        """)
    int sumTokensUsedByTenantAndPeriod(
        @Param("tenantId") UUID tenantId,
        @Param("startInclusive")  Instant startInclusive,
        @Param("endExclusive") Instant endExclusive
    );
}
