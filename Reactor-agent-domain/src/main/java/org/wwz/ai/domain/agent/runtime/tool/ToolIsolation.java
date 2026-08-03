package org.wwz.ai.domain.agent.runtime.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具并发隔离：默认给每个 Agent 独占工具实例，避免共享实例上的长锁串行化。
 * <p>
 * 隔离优先级：
 * 1. {@link ContextIsolatableTool#isolateFor(AgentContext)} 显式副本
 * 2. 反射 fork（复制构造依赖 + 绑定 context，不复制执行态字段）
 * 3. 共享锁 rebind（仅当无法 fork 时兜底，会串行化同名工具）
 * <p>
 * 会话级协作状态（任务列表、PlanMode、账本）必须放在 {@link AgentContext} 上共享，
 * 不得放在工具实例字段上。
 */
public final class ToolIsolation {

    private static final Logger log = LoggerFactory.getLogger(ToolIsolation.class);
    private static final Map<Class<?>, Boolean> FORK_CAPABLE = new ConcurrentHashMap<>();

    private ToolIsolation() {
    }

    /**
     * 将工具绑定到目标 AgentContext，优先返回可并发的独立实例。
     */
    public static BaseTool bindToContext(BaseTool tool, AgentContext context) {
        if (tool == null || context == null) {
            return tool;
        }
        BaseTool unwrapped = ContextScopedTool.unwrap(tool);
        if (unwrapped instanceof ContextIsolatableTool isolatable) {
            return isolatable.isolateFor(context);
        }
        BaseTool forked = tryFork(unwrapped, context);
        if (forked != null) {
            return forked;
        }
        if (Boolean.FALSE.equals(FORK_CAPABLE.get(unwrapped.getClass()))) {
            log.debug("tool isolation fallback to shared lock: {}", unwrapped.getName());
        }
        return ContextScopedTool.wrapShared(unwrapped, context);
    }

    /**
     * 就地隔离 ToolCollection 内全部基础工具。
     */
    public static void bindAll(ToolCollection tools, AgentContext context) {
        if (tools == null || context == null || tools.getToolMap() == null) {
            return;
        }
        tools.setAgentContext(context);
        for (Map.Entry<String, BaseTool> entry : tools.getToolMap().entrySet()) {
            entry.setValue(bindToContext(entry.getValue(), context));
        }
    }

    /**
     * 尝试为工具创建不共享执行态的独立实例。
     * 成功时已绑定 context；失败返回 null。
     */
    static BaseTool tryFork(BaseTool source, AgentContext context) {
        if (source == null || context == null) {
            return null;
        }
        Class<?> clazz = source.getClass();
        if (Boolean.FALSE.equals(FORK_CAPABLE.get(clazz))) {
            return null;
        }
        try {
            Object copy = instantiateCopy(source);
            if (copy == null) {
                FORK_CAPABLE.put(clazz, Boolean.FALSE);
                return null;
            }
            // 无参构造路径也要把服务/注册表等依赖拷过去；不拷执行态
            copyInjectableFields(source, copy);
            writeAgentContext(copy, context);
            FORK_CAPABLE.putIfAbsent(clazz, Boolean.TRUE);
            return (BaseTool) copy;
        } catch (Exception e) {
            FORK_CAPABLE.put(clazz, Boolean.FALSE);
            log.warn("tool fork failed, will use shared lock path: class={}", clazz.getName(), e);
            return null;
        }
    }

    private static Object instantiateCopy(BaseTool source) throws Exception {
        Class<?> clazz = source.getClass();
        // 优先带参构造（完整注入 final 依赖），再回落无参 + 字段拷贝
        Constructor<?> best = null;
        for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 0) {
                continue;
            }
            if (best == null || constructor.getParameterCount() > best.getParameterCount()) {
                best = constructor;
            }
        }
        if (best != null) {
            best.setAccessible(true);
            Class<?>[] paramTypes = best.getParameterTypes();
            Object[] args = new Object[paramTypes.length];
            boolean complete = true;
            for (int i = 0; i < paramTypes.length; i++) {
                Object dep = findDependency(source, paramTypes[i]);
                if (dep == null && paramTypes[i].isPrimitive()) {
                    complete = false;
                    break;
                }
                if (dep == null && !paramTypes[i].isPrimitive()) {
                    // 允许 null 依赖，但若全部为 null 且参数>0 可能无意义；仍尝试
                }
                args[i] = dep;
            }
            if (complete) {
                try {
                    return best.newInstance(args);
                } catch (Exception ignored) {
                    // fall through to no-arg
                }
            }
        }
        try {
            Constructor<?> noArg = clazz.getDeclaredConstructor();
            noArg.setAccessible(true);
            return noArg.newInstance();
        } catch (NoSuchMethodException e) {
            return null;
        } catch (ReflectiveOperationException e) {
            // 私有嵌套类等在强封装下可能不可访问，交由上层走共享锁兜底
            log.debug("no-arg fork inaccessible: {}", clazz.getName());
            return null;
        }
    }

    /**
     * 复制可共享依赖（服务、注册表、options）；跳过 AgentContext 与执行态字段。
     */
    private static void copyInjectableFields(Object source, Object target) throws IllegalAccessException {
        if (source == null || target == null || source.getClass() != target.getClass()) {
            // 允许子类：按 target 类型字段从 source 取值
        }
        for (Field field : allFields(target.getClass())) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            if (AgentContext.class.isAssignableFrom(field.getType())) {
                continue;
            }
            if (isExecutionStateField(field.getName(), field.getType())) {
                continue;
            }
            // 跳过 JDK / 集合执行缓冲
            if (field.getType().getName().startsWith("java.util.")
                    && !field.getType().getName().contains("concurrent")
                    && isLikelyMutableBuffer(field.getName())) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(source);
            if (value == null) {
                continue;
            }
            // 只拷贝依赖型引用，不拷贝简单可变业务状态（plan 已在 isExecutionStateField）
            if (isPrimitiveLike(field.getType())) {
                continue;
            }
            field.set(target, value);
        }
    }

    private static boolean isLikelyMutableBuffer(String name) {
        String n = name == null ? "" : name.toLowerCase();
        return n.contains("map") || n.contains("list") || n.contains("buffer")
                || n.contains("handler") || n.contains("cache");
    }

    private static boolean isPrimitiveLike(Class<?> type) {
        return type.isPrimitive()
                || type == Boolean.class
                || type == Byte.class
                || type == Short.class
                || type == Integer.class
                || type == Long.class
                || type == Float.class
                || type == Double.class
                || type == Character.class
                || type == String.class
                || type.isEnum();
    }

    private static Object findDependency(Object source, Class<?> type) {
        if (type.isInstance(source)) {
            return source;
        }
        List<Field> fields = allFields(source.getClass());
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (AgentContext.class.isAssignableFrom(field.getType())) {
                continue;
            }
            // 跳过明显的执行态字段，避免把 stream/plan 缓冲拷到子实例
            String name = field.getName();
            if (isExecutionStateField(name, field.getType())) {
                continue;
            }
            if (!type.isAssignableFrom(field.getType()) && !field.getType().isAssignableFrom(type)) {
                // 仍尝试取值后做类型匹配
            }
            try {
                field.setAccessible(true);
                Object value = field.get(source);
                if (value != null && type.isInstance(value)) {
                    return value;
                }
            } catch (IllegalAccessException ignored) {
                // next
            }
        }
        // getter fallback
        for (Method method : source.getClass().getMethods()) {
            if (method.getParameterCount() != 0 || method.getReturnType() == void.class) {
                continue;
            }
            if (!type.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            String n = method.getName();
            if (!n.startsWith("get") && !n.startsWith("is")) {
                continue;
            }
            if ("getAgentContext".equals(n) || "getClass".equals(n)) {
                continue;
            }
            try {
                Object value = method.invoke(source);
                if (value != null && type.isInstance(value)) {
                    return value;
                }
            } catch (Exception ignored) {
                // next
            }
        }
        return null;
    }

    private static boolean isExecutionStateField(String name, Class<?> type) {
        String n = name == null ? "" : name.toLowerCase();
        if (n.contains("stream") || n.contains("session") || n.contains("future")
                || n.contains("buffer") || n.contains("builder") || n.equals("plan")
                || n.contains("laststructured") || n.contains("active")) {
            return true;
        }
        String typeName = type.getName();
        return typeName.contains("RemoteStreamSession")
                || typeName.contains("CompletableFuture")
                || typeName.contains("AtomicReference");
    }

    private static List<Field> allFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                fields.add(field);
            }
            current = current.getSuperclass();
        }
        return fields;
    }

    private static void writeAgentContext(Object tool, AgentContext context) throws Exception {
        Method setter = findSetter(tool.getClass(), "setAgentContext", AgentContext.class);
        if (setter != null) {
            setter.invoke(tool, context);
            return;
        }
        for (Field field : allFields(tool.getClass())) {
            if (AgentContext.class.isAssignableFrom(field.getType())
                    && !Modifier.isStatic(field.getModifiers())
                    && !Modifier.isFinal(field.getModifiers())) {
                field.setAccessible(true);
                field.set(tool, context);
                return;
            }
        }
        throw new IllegalStateException("cannot bind AgentContext on " + tool.getClass().getName());
    }

    private static Method findSetter(Class<?> type, String name, Class<?> paramType) {
        try {
            Method method = type.getMethod(name, paramType);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
