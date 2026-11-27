package com.noypi.docguardxray.common;

import java.util.Optional;
import java.util.UUID;

public interface ActiveTenantLookup {
    Optional<UUID> findActiveTenantIdBySlug(String slug);
}
