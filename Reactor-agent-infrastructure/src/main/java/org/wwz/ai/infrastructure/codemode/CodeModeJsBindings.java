package org.wwz.ai.infrastructure.codemode;

import org.apache.commons.lang3.StringUtils;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.wwz.ai.types.agent.config.CodeModeProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * 把 capability 绑成 GraalJS 可见的 {@code CodeModeContext}。
 * <p>
 * 只暴露 {@code browser / host / workspace / input}，外加 {@code console}
 * 与执行器内部使用的 {@code __complete / __fail}。
 * <b>{@code agentBrowser} 不出现在绑定对象里，也没有任何参数能切换过去。</b>
 * </p>
 * <p>
 * 全部通过 {@link ProxyObject} / {@link ProxyExecutable} 暴露，
 * 因为 Context 使用 {@code HostAccess.NONE}，普通 Java 对象在 JS 里不可访问。
 * </p>
 */
public final class CodeModeJsBindings {

    private final AtomicReference<Value> result = new AtomicReference<>();
    private final AtomicReference<String> failure = new AtomicReference<>();
    private final AtomicReference<String> errorCode = new AtomicReference<>();
    private final CountDownLatch latch = new CountDownLatch(1);

    private CodeModeJsBindings() {
    }

    public Value result() {
        return result.get();
    }

    public String failure() {
        return failure.get();
    }

    public String errorCode() {
        return errorCode.get();
    }

    public CountDownLatch latch() {
        return latch;
    }

    public void bindTo(Context context) {
        context.getBindings("js").putMember("__complete", (ProxyExecutable) args -> {
            result.set(args.length > 0 ? args[0] : null);
            latch.countDown();
            return null;
        });
        // __fail(code, message)：code 可为 null，表示按运行期异常归类
        context.getBindings("js").putMember("__fail", (ProxyExecutable) args -> {
            String code = args.length > 0 && args[0] != null && args[0].isString() ? args[0].asString() : null;
            Value message = args.length > 1 ? args[1] : (args.length > 0 ? args[0] : null);
            if (StringUtils.isNotBlank(code)) {
                errorCode.set(code);
            }
            failure.set(renderError(message));
            latch.countDown();
            return null;
        });
    }

    public static CodeModeJsBindings bind(Context context,
                                          CodeModeExecutionBudget budget,
                                          CodeModeProperties properties,
                                          WorkspaceCodeCapability workspaceCapability,
                                          BrowserCodeCapability browserCapability,
                                          HostCodeCapability hostCapability,
                                          Map<String, Object> inputs) {
        CodeModeJsBindings bindings = new CodeModeJsBindings();
        bindings.bindTo(context);

        Map<String, Object> contextObject = new LinkedHashMap<>();
        contextObject.put("browser", ProxyObject.fromMap(Map.of(
                "command", guarded(bindings, browserCapability::command))));
        contextObject.put("host", ProxyObject.fromMap(Map.of(
                "exec", guarded(bindings, hostCapability::exec))));
        contextObject.put("workspace", ProxyObject.fromMap(Map.of(
                "readText", guarded(bindings, workspaceCapability::readText),
                "readBytes", guarded(bindings, workspaceCapability::readBytes),
                "readCsv", guarded(bindings, workspaceCapability::readCsv),
                "writeText", guarded(bindings, workspaceCapability::writeText),
                "writeBytes", guarded(bindings, workspaceCapability::writeBytes),
                "writeCsv", guarded(bindings, workspaceCapability::writeCsv))));
        contextObject.put("input", GraalJsValueConverter.toJsObject(
                inputs == null ? Map.of() : inputs));

        context.getBindings("js").putMember("__reactorContext", ProxyObject.fromMap(contextObject));
        context.getBindings("js").putMember("console", consoleProxy(budget));
        return bindings;
    }

    /** capability 调用包装：统一参数转换、错误码留存和异常上抛。 */
    private static ProxyExecutable guarded(CodeModeJsBindings bindings,
                                           Function<Map<String, Object>, Object> capability) {
        return args -> {
            Map<String, Object> params;
            try {
                params = args == null || args.length == 0
                        ? new LinkedHashMap<>()
                        : GraalJsValueConverter.toJavaMap(args[0]);
            } catch (CodeModeExecutionException e) {
                bindings.errorCode.set(e.getErrorCode().code());
                throw e;
            }
            try {
                // capability 返回纯 Java Map，这里统一转成 JS 可见的代理对象
                return GraalJsValueConverter.toJsObject(capability.apply(params));
            } catch (CodeModeExecutionException e) {
                bindings.errorCode.set(e.getErrorCode().code());
                throw e;
            } catch (RuntimeException e) {
                bindings.errorCode.set(org.wwz.ai.domain.agent.runtime.tool.codemode
                        .CodeModeErrorCode.CODE_MODE_RUNTIME_ERROR.code());
                throw e;
            }
        };
    }

    private static ProxyObject consoleProxy(CodeModeExecutionBudget budget) {
        return ProxyObject.fromMap(Map.of(
                "log", (ProxyExecutable) args -> log(budget, "log", args),
                "info", (ProxyExecutable) args -> log(budget, "info", args),
                "warn", (ProxyExecutable) args -> log(budget, "warn", args),
                "error", (ProxyExecutable) args -> log(budget, "error", args),
                "debug", (ProxyExecutable) args -> log(budget, "debug", args)));
    }

    /**
     * 错误值渲染：JS 字符串必须走 {@code asString()}，
     * {@code toString()} 会打印引擎内部表示而不是用户看到的消息。
     */
    private static String renderError(Value message) {
        if (message == null || message.isNull()) {
            return "unknown error";
        }
        if (message.isString()) {
            return message.asString();
        }
        try {
            if (message.hasMembers() || message.hasArrayElements()) {
                return String.valueOf(GraalJsValueConverter.toJavaObject(message));
            }
        } catch (RuntimeException ignore) {
            // 退回到 toString
        }
        return String.valueOf(message);
    }

    private static Object log(CodeModeExecutionBudget budget, String level, Value[] args) {
        List<String> parts = new ArrayList<>();
        if (args != null) {
            for (Value arg : args) {
                parts.add(render(arg));
            }
        }
        budget.log(level, String.join(" ", parts));
        return null;
    }

    private static String render(Value value) {
        if (value == null || value.isNull()) {
            return "null";
        }
        try {
            if (value.isString()) {
                return value.asString();
            }
            if (value.canExecute()) {
                return "[function]";
            }
            if (value.hasMembers() || value.hasArrayElements()) {
                return String.valueOf(GraalJsValueConverter.toJavaObject(value));
            }
            return value.toString();
        } catch (RuntimeException e) {
            return StringUtils.defaultString(value.toString());
        }
    }
}
