package org.wwz.ai.domain.agent.runtime.tool;

import org.wwz.ai.domain.agent.runtime.agent.AgentContext;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 共享工具实例的 context 绑定包装器（并发兜底路径）。
 * <p>
 * 正常路径请走 {@link ToolIsolation#bindToContext}：优先 fork 独立实例。
 * 仅当工具无法 fork 时才使用本类；{@code synchronized} 会串行化同一底层工具的全部 execute。
 */
public final class ContextScopedTool implements BaseTool {

    private static final MethodAccess NO_ACCESS = new MethodAccess(null, null);
    private static final ConcurrentHashMap<Class<?>, MethodAccess> ACCESS_CACHE = new ConcurrentHashMap<>();

    private final BaseTool delegate;
    private final AgentContext boundContext;
    private final Object lock;

    private ContextScopedTool(BaseTool delegate, AgentContext boundContext, Object lock) {
        this.delegate = delegate;
        this.boundContext = boundContext;
        this.lock = lock;
    }

    /**
     * 绑定工具到 context：优先隔离实例，失败才走共享锁。
     */
    public static BaseTool bind(BaseTool tool, AgentContext context) {
        return ToolIsolation.bindToContext(tool, context);
    }

    /**
     * 仅创建共享锁包装（不尝试 fork）。供 {@link ToolIsolation} 兜底使用。
     */
    public static BaseTool wrapShared(BaseTool tool, AgentContext context) {
        if (tool == null || context == null) {
            return tool;
        }
        BaseTool unwrapped = unwrap(tool);
        Object lock = unwrapped;
        if (tool instanceof ContextScopedTool scoped) {
            lock = scoped.lock;
        }
        return new ContextScopedTool(unwrapped, context, lock);
    }

    /**
     * 就地绑定 ToolCollection 内全部基础工具。
     */
    public static void bindAll(ToolCollection tools, AgentContext context) {
        ToolIsolation.bindAll(tools, context);
    }

    public BaseTool unwrap() {
        return unwrap(delegate);
    }

    public static BaseTool unwrap(BaseTool tool) {
        BaseTool current = tool;
        while (current instanceof ContextScopedTool scoped) {
            current = scoped.delegate;
        }
        return current;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public Map<String, Object> toParams() {
        return delegate.toParams();
    }

    @Override
    public Object execute(Object input) {
        synchronized (lock) {
            MethodAccess access = resolveAccess(delegate);
            AgentContext previous = readAgentContext(delegate, access);
            writeAgentContext(delegate, access, boundContext);
            try {
                return delegate.execute(input);
            } finally {
                writeAgentContext(delegate, access, previous);
            }
        }
    }

    private static MethodAccess resolveAccess(BaseTool tool) {
        if (tool == null) {
            return NO_ACCESS;
        }
        return ACCESS_CACHE.computeIfAbsent(tool.getClass(), ContextScopedTool::lookupAccess);
    }

    private static MethodAccess lookupAccess(Class<?> toolClass) {
        Method getter = findMethod(toolClass, "getAgentContext");
        Method setter = findMethod(toolClass, "setAgentContext", AgentContext.class);
        if (getter == null && setter == null) {
            return NO_ACCESS;
        }
        return new MethodAccess(getter, setter);
    }

    private static Method findMethod(Class<?> toolClass, String name, Class<?>... parameterTypes) {
        try {
            Method method = toolClass.getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static AgentContext readAgentContext(BaseTool tool, MethodAccess access) {
        if (tool == null || access.getter == null) {
            return null;
        }
        try {
            Object value = access.getter.invoke(tool);
            return value instanceof AgentContext agentContext ? agentContext : null;
        } catch (Exception e) {
            throw new IllegalStateException("read tool agentContext failed: " + tool.getName(), e);
        }
    }

    private static void writeAgentContext(BaseTool tool, MethodAccess access, AgentContext context) {
        if (tool == null || access.setter == null) {
            return;
        }
        try {
            access.setter.invoke(tool, context);
        } catch (Exception e) {
            throw new IllegalStateException("write tool agentContext failed: " + tool.getName(), e);
        }
    }

    private static final class MethodAccess {
        private final Method getter;
        private final Method setter;

        private MethodAccess(Method getter, Method setter) {
            this.getter = getter;
            this.setter = setter;
        }
    }
}
