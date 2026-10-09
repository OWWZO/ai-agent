package org.wwz.ai.domain.agent.browser.model;

import java.util.Locale;
import java.util.Objects;

/**
 * A normalized action understood by the browser relay use case.
 */
public record BrowserAction(String value) {

    public BrowserAction {
        value = Objects.requireNonNull(value, "action").trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("action must not be blank");
        }
    }

    public static BrowserAction of(String value) {
        return new BrowserAction(value);
    }

    public String getValue() {
        return value;
    }

    public boolean isLeaseRelease() {
        return "lease-release".equals(value);
    }
}
