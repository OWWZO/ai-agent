package org.wwz.ai.types.agent.user;

/** Request-scoped user identity shared by the authenticated trigger and runtime. */
public final class UserRequestContext {

    private static final ThreadLocal<String> USER_HOLDER = new ThreadLocal<>();

    private UserRequestContext() {
    }

    public static void bind(String userId) {
        if (userId == null || userId.isBlank()) {
            USER_HOLDER.remove();
            return;
        }
        USER_HOLDER.set(userId.trim());
    }

    public static String currentUserId() {
        return USER_HOLDER.get();
    }

    public static String requireUserId() {
        String userId = currentUserId();
        if (userId == null || userId.isBlank()) {
            throw new IllegalStateException("当前请求缺少 userId");
        }
        return userId;
    }

    public static void clear() {
        USER_HOLDER.remove();
    }
}
