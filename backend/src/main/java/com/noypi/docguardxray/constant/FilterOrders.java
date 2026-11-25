package com.noypi.docguardxray.constant;

import org.springframework.core.Ordered;

public final class FilterOrders {
    private FilterOrders() {}

    public static final int REQUEST_API = Ordered.HIGHEST_PRECEDENCE + 10;
}
