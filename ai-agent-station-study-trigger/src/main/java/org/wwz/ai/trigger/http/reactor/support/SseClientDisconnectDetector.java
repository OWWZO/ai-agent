package org.wwz.ai.trigger.http.reactor.support;

import java.util.Locale;

/**
 * 识别 SSE 下游客户端主动断开导致的常见异常，避免把浏览器中断误记为服务端失败。
 */
final class SseClientDisconnectDetector {

    private SseClientDisconnectDetector() {
    }

    static boolean isClientDisconnected(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String className = current.getClass().getName();
            if ("org.apache.catalina.connector.ClientAbortException".equals(className)
                    || "org.springframework.web.context.request.async.AsyncRequestNotUsableException".equals(className)) {
                return true;
            }

            String message = current.getMessage();
            if (message != null && isDisconnectMessage(message)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean isDisconnectMessage(String message) {
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("broken pipe")
                || normalized.contains("connection reset by peer")
                || normalized.contains("forcibly closed by the remote host")
                || normalized.contains("an established connection was aborted by the software in your host machine")
                || message.contains("你的主机中的软件中止了一个已建立的连接");
    }
}
