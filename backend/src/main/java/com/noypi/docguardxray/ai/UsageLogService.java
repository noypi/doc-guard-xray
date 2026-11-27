package com.noypi.docguardxray.ai;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.noypi.docguardxray.common.time.TimeHelper;
import com.noypi.docguardxray.constant.UsageSource;

@Service 
public class UsageLogService {

    private static final Logger log = LoggerFactory.getLogger(UsageLogService.class);
    private final UsageLogRepository usageLogRepository;
    
    public UsageLogService(UsageLogRepository usageLogRepository) {
        this.usageLogRepository = usageLogRepository;
    }

    @Transactional 
    public void recordUsage(
            UUID tenantId,
            UsageSource source,
            int totalTokens,
            int piiRedactedCount,
            String traceId
    ) {
        log.debug("recordUsage id:{}, traceId:{}", tenantId, traceId);
        UsageLog log = new UsageLog(
                tenantId,
                source,
                totalTokens,
                piiRedactedCount,
                traceId);

        this.usageLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public int sumTokensUsedByTenantThisMonth(UUID tenantId) {
        Instant start = TimeHelper.startOfMonth();
        Instant end = TimeHelper.startNextMonth();

        return this.usageLogRepository.sumTokensUsedByTenantAndPeriod(tenantId, start, end);
    }
}
