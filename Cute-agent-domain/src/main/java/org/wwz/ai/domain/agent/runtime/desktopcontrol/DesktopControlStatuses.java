package org.wwz.ai.domain.agent.runtime.desktopcontrol;

import java.util.List;

public final class DesktopControlStatuses {

    public static final String PENDING = "PENDING";
    public static final String RESUME_PENDING = "RESUME_PENDING";
    public static final String RESUMING = "RESUMING";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";
    public static final String FAILED = "FAILED";

    public static final List<String> OPEN = List.of(PENDING, RESUME_PENDING, RESUMING);
    public static final List<String> CANCELABLE = List.of(PENDING, RESUME_PENDING);

    private DesktopControlStatuses() {
    }

    public static boolean isOpen(String status) {
        return PENDING.equals(status) || RESUME_PENDING.equals(status) || RESUMING.equals(status);
    }
}
