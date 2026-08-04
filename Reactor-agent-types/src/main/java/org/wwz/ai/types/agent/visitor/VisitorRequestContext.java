package org.wwz.ai.types.agent.visitor;

/**
 * 当前请求匿名访客上下文。
 */
public final class VisitorRequestContext {

    private static final ThreadLocal<String> VISITOR_HOLDER = new ThreadLocal<>();

    private VisitorRequestContext() {
    }

    /**
     * 将当前入口解析出的访客 ID 绑定到线程上下文。
     * <p>调用方必须在请求结束时执行 {@link #clear()}，避免线程池复用导致访客身份串线。</p>
     */
    public static void bind(String visitorId) {
        VISITOR_HOLDER.set(visitorId);
    }

    /** 返回当前线程已绑定的访客 ID；未绑定时返回 {@code null}。 */
    public static String currentVisitorId() {
        return VISITOR_HOLDER.get();
    }

    /**
     * 获取当前访客 ID，并把缺失身份转换为入口可处理的非法状态异常。
     *
     * @return 非空访客 ID
     * @throws IllegalStateException 当前请求没有完成访客身份绑定
     */
    public static String requireVisitorId() {
        String visitorId = VISITOR_HOLDER.get();
        if (visitorId == null || visitorId.isBlank()) {
            throw new IllegalStateException("当前请求缺少 visitorId");
        }
        return visitorId;
    }

    /** 清理当前线程的访客身份，结束请求或异步任务时必须调用。 */
    public static void clear() {
        VISITOR_HOLDER.remove();
    }
}
