package org.wwz.ai.domain.agent.runtime.tool;

import org.wwz.ai.domain.agent.runtime.agent.AgentContext;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 将共享工具实例绑定到固定 AgentContext。
 * 执行时在底层工具锁上临时 rebind，结束后恢复，避免并行子 Agent 抢写 agentContext。
 */
public final class ContextScopedTool implements BaseTool {

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
            AgentContext previous = readAgentContext(delegate);
            writeAgentContext(delegate, boundContext);
            try {
                return delegate.execute(input);
            } finally {
                writeAgentContext(delegate, previous);
            }
        }
    }

    private static AgentContext readAgentContext(BaseTool tool) {
        if (tool == null) {
            return null;
        }
        try {
            Method getter = tool.getClass().getMethod("getAgentContext");
            getter.setAccessible(true);
            Object value = getter.invoke(tool);
            return value instanceof AgentContext agentContext ? agentContext : null;
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (Exception e) {
            throw new IllegalStateException("read tool agentContext failed: " + tool.getName(), e);
        }
    }

    private static void writeAgentContext(BaseTool tool, AgentContext context) {
        if (tool == null) {
            return;
        }
        try {
            Method setter = tool.getClass().getMethod("setAgentContext", AgentContext.class);
            setter.setAccessible(true);
            setter.invoke(tool, context);
        } catch (NoSuchMethodException ignored) {
            // 无 agentContext 的工具跳过
        } catch (Exception e) {
            throw new IllegalStateException("write tool agentContext failed: " + tool.getName(), e);
        }
    }
}
