package org.wwz.ai.infrastructure.adapter.port;

import java.util.regex.Pattern;

/**
 * Removes credentials and browser endpoint values before an exception can cross the adapter boundary.
 */
final class AgentBrowserCredentialRedactor {

    private static final Pattern NAMED_SECRET = Pattern.compile(
            "(?i)([\\\"']?(?:cdp_ws_url|jwt)[\\\"']?\\s*[:=]\\s*[\\\"']?)([^\\\"'\\s,}&]+)([\\\"']?)");
    private static final Pattern WEBSOCKET_ENDPOINT = Pattern.compile(
            "(?i)wss?://[^\\s\\\"'<>]+");
    private static final Pattern QUERY_SECRET = Pattern.compile(
            "(?i)([?&](?:jwt|api[_-]?key)=)([^&#\\s\\\"']+)");

    private AgentBrowserCredentialRedactor() {
    }

    static String redact(String message, String apiKey) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        String redacted = message;
        if (apiKey != null && !apiKey.isBlank()) {
            redacted = redacted.replace(apiKey, "[REDACTED]");
        }
        redacted = NAMED_SECRET.matcher(redacted).replaceAll("$1[REDACTED]$3");
        redacted = WEBSOCKET_ENDPOINT.matcher(redacted).replaceAll("[REDACTED]");
        return QUERY_SECRET.matcher(redacted).replaceAll("$1[REDACTED]");
    }
}
