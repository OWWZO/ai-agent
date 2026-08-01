package org.wwz.ai.domain.agent.runtime.tool;

import org.wwz.ai.domain.agent.runtime.agent.AgentContext;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 将共享工具实例绑定到固定 AgentContext。
 * 执行时在底层工具锁上临时 rebind，结束后恢复，避免并行子 Agent 抢写 agentContext。
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

    public static BaseTool bind(BaseTool tool, AgentContext context) {
        if (tool == null || context == null) {
            return tool;
        }
        if (tool instanceof ContextScopedTool scoped) {
            return new ContextScopedTool(scoped.unwrap(), context, scoped.lock);
        }
        return new ContextScopedTool(tool, context, tool);
    }

    /**
     * 就地包装 ToolCollection 内全部基础工具。
     */
    public static void bindAll(ToolCollection tools, AgentContext context) {
        if (tools == null || context == null || tools.getToolMap() == null) {
            return;
        }
        tools.setAgentContext(context);
        Map<String, BaseTool> toolMap = tools.getToolMap();
        for (Map.Entry<String, BaseTool> entry : toolMap.entrySet()) {
            entry.setValue(bind(entry.getValue(), context));
        }
    }

    public BaseTool unwrap() {
        BaseTool current = delegate;
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
