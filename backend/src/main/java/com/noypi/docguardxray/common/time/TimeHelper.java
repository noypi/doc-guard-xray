package com.noypi.docguardxray.common.time;

import java.time.Instant;
import java.time.YearMonth;

import com.noypi.docguardxray.config.AppConfig;

public final class TimeHelper {
    
    public static Instant startOfMonth() {
        return YearMonth.now(AppConfig.zoneId())
                .atDay(1)
                .atStartOfDay(AppConfig.zoneId())
                .toInstant();
    }

    public static Instant startNextMonth() {
        return YearMonth.now(AppConfig.zoneId())
                .plusMonths(1)
                .atDay(1)
                .atStartOfDay(AppConfig.zoneId())
                .toInstant();
    }
}
