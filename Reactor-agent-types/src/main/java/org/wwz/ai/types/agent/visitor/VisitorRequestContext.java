package org.wwz.ai.types.agent.visitor;

/**
 * 当前请求匿名访客上下文。
 */
public final class VisitorRequestContext {

    private static final ThreadLocal<String> VISITOR_HOLDER = new ThreadLocal<>();

    private VisitorRequestContext() {
    }

    public static void bind(String visitorId) {
        VISITOR_HOLDER.set(visitorId);
    }

    public static String currentVisitorId() {
        return VISITOR_HOLDER.get();
    }

    public static String requireVisitorId() {
        String visitorId = VISITOR_HOLDER.get();
        if (visitorId == null || visitorId.isBlank()) {
            throw new IllegalStateException("当前请求缺少 visitorId");
        }
        return visitorId;
    }

    public static void clear() {
        VISITOR_HOLDER.remove();
    }
}
