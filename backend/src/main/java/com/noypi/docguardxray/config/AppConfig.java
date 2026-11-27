package com.noypi.docguardxray.config;

import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

public final class AppConfig {

    private static volatile ZoneId _zoneId = ZoneOffset.UTC;

    public static ZoneId zoneId() {
        return _zoneId;
    }
    
    @Component
    static class Binder {
        Binder(@Value("${app.zone-id:UTC}") String configZoneId) {
            _zoneId = (configZoneId == null || configZoneId.isBlank())
                    ? ZoneOffset.UTC
                    : ZoneId.of(configZoneId);

        }
    }
}
