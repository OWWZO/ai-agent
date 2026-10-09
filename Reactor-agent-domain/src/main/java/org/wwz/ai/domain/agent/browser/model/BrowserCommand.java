package org.wwz.ai.domain.agent.browser.model;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed command sent to one user's browser relay.
 *
 * <p>The parameters are business data for the browser action. Transport
 * framing, JSON, and generated RPC ids are deliberately outside this model.</p>
 */
public record BrowserCommand(String userId,
                             BrowserAction action,
                             String rpcId,
                             Map<String, Object> parameters,
                             Duration timeout,
                             Long deadlineAt) {

    public BrowserCommand {
        userId = trimToNull(userId);
        rpcId = trimToNull(rpcId);
        action = action == null ? null : action;
        parameters = parameters == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        if (timeout != null && timeout.isNegative()) {
            timeout = Duration.ZERO;
        }
    }

    public static BrowserCommand of(String userId,
                                    String action,
                                    Map<String, Object> parameters,
                                    Duration timeout) {
        return new BrowserCommand(
                userId,
                BrowserAction.of(action),
                rpcIdFrom(parameters),
                parameters,
                timeout,
                deadlineAtFrom(parameters)
        );
    }

    public BrowserCommand withRpcId(String value) {
        return new BrowserCommand(userId, action, value, parameters, timeout, deadlineAt);
    }

    public BrowserCommand withTimeout(Duration value) {
        return new BrowserCommand(userId, action, rpcId, parameters, value, deadlineAt);
    }

    public BrowserCommand withDeadlineAt(Long value) {
        return new BrowserCommand(userId, action, rpcId, parameters, timeout, value);
    }

    public String getUserId() {
        return userId;
    }

    public BrowserAction getAction() {
        return action;
    }

    public String getRpcId() {
        return rpcId;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public Long getDeadlineAt() {
        return deadlineAt;
    }

    private static String rpcIdFrom(Map<String, Object> parameters) {
        if (parameters == null || !(parameters.get("id") instanceof String value)) {
            return null;
        }
        return trimToNull(value);
    }

    private static Long deadlineAtFrom(Map<String, Object> parameters) {
        if (parameters == null || !(parameters.get("deadlineAt") instanceof Number value)) {
            return null;
        }
        return value.longValue();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
