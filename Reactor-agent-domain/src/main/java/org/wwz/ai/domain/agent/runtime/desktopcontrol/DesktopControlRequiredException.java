package org.wwz.ai.domain.agent.runtime.desktopcontrol;

public class DesktopControlRequiredException extends RuntimeException {

    private final String reason;
    private final String streamUrl;
    private final Double holdUntil;
    private final String toolCallId;

    public DesktopControlRequiredException(String reason, String streamUrl, Double holdUntil, String toolCallId) {
        super("DESKTOP_CONTROL_REQUIRED");
        this.reason = reason == null ? "" : reason;
        this.streamUrl = streamUrl;
        this.holdUntil = holdUntil;
        this.toolCallId = toolCallId;
    }

    public String getReason() {
        return reason;
    }

    public String getStreamUrl() {
        return streamUrl;
    }

    public Double getHoldUntil() {
        return holdUntil;
    }

    public String getToolCallId() {
        return toolCallId;
    }
}
