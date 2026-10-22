package org.wwz.ai.infrastructure.codemode;

import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.StringUtils;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.codemode.CodeModeErrorCode;
import org.wwz.ai.domain.agent.runtime.tool.codemode.CodeModeExecutorPort;
import org.wwz.ai.domain.agent.runtime.tool.codemode.CodeModeRequest;
import org.wwz.ai.domain.agent.runtime.tool.codemode.CodeModeRequestValidator;
import org.wwz.ai.domain.agent.runtime.tool.codemode.CodeModeResult;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceAccessException;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;
import org.wwz.ai.types.agent.config.CodeModeProperties;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * GraalJS 实现：在当前 JVM 内执行 JavaScript，把 browser / host / workspace 编排成一次调用。
 * <p>
 * 安全边界是"最小权限 Context + capability 各自限权"两层，<b>不是强沙箱</b>：
 * 同 JVM 模式无法绝对保证恶意脚本不会耗尽 CPU 或内存，只能保证
 * 不暴露 Java 类 / 文件系统 / 进程 / 网络，且超时与取消能中断执行。
 * </p>
 */
public class GraalJsCodeModeExecutor implements CodeModeExecutorPort {

    /** JS 包装器：固定 main(context) 入口，不支持 require / import / ES Module。 */
    private static final String WRAPPER = """
            (function () {
              "use strict";
            %s
              if (typeof main !== 'function') {
                __fail("CODE_MODE_INVALID_SOURCE", "main(context) is not defined");
                return undefined;
              }
              (async function () {
                try {
                  const __r = await main(__cuteContext);
                  __complete(__r);
                } catch (__e) {
                  __fail(null, __e && __e.message ? __e.message : String(__e));
                }
              })();
              return undefined;
            })()
            """;

    private final CodeModeProperties properties;
    private final WorkspaceService workspaceService;
    private final ScheduledExecutorService watchdog;

    public GraalJsCodeModeExecutor(CodeModeProperties properties, WorkspaceService workspaceService) {
        this.properties = properties == null ? new CodeModeProperties() : properties;
        this.workspaceService = workspaceService;
        this.watchdog = Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable, "code-mode-watchdog");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public CodeModeResult execute(CodeModeRequest request, AgentContext agentContext) {
        CodeModeExecutionBudget budget = new CodeModeExecutionBudget(
                resolveTimeout(request),
                this.properties.getMaxToolCalls(),
                this.properties.getMaxTotalMemoryBytes());
        if (request == null) {
            return failure(CodeModeErrorCode.CODE_MODE_INVALID_ARGUMENT, "请求为空", budget, null);
        }
        if (agentContext == null) {
            return failure(CodeModeErrorCode.CODE_MODE_INVALID_ARGUMENT, "缺少 AgentContext", budget, null);
        }
        if (agentContext.isRunCancelled()) {
            return failure(CodeModeErrorCode.CODE_MODE_CANCELLED, "用户已停止本轮对话", budget, null);
        }

        CodeModeRequestValidator.Validation validation = CodeModeRequestValidator.validateSourceTarget(
                request.source(),
                request.workspaceFile(),
                maxSourceChars(),
                this.properties.getWorkspace().getAllowedScriptPrefixes());
        if (!validation.isValid()) {
            return failure(validation.errorCode(), validation.message(), budget, null);
        }

        String source;
        try {
            source = StringUtils.isNotBlank(request.source())
                    ? request.source()
                    : readWorkspaceScript(agentContext, request.workspaceFile());
        } catch (CodeModeExecutionException e) {
            return failure(e.getErrorCode(), e.getMessage(), budget, null);
        }

        String sourceHash = sha256Hex(source.getBytes(StandardCharsets.UTF_8));
        Context context = null;
        CodeModeJsBindings bindings = null;
        ScheduledFuture<?> timeout = null;
        Runnable unregisterAbort = null;
        try {
            context = GraalJsContextFactory.newContext();
            bindings = CodeModeJsBindings.bind(
                    context,
                    budget,
                    this.properties,
                    new WorkspaceCodeCapability(agentContext, budget, workspaceService, this.properties.getWorkspace()),
                    new BrowserCodeCapability(agentContext, budget, this.properties.getMaxOutputChars()),
                    new HostCodeCapability(agentContext, budget, this.properties.getMaxOutputChars(),
                            HostCodeCapability.resolveAvailableTools(agentContext.getRuntimeDependencies())),
                    request.inputs());

            final Context runningContext = context;
            timeout = watchdog.schedule(() -> closeQuietly(runningContext, true),
                    budget.remainingMs(), TimeUnit.MILLISECONDS);
            unregisterAbort = agentContext.registerRunAbort(() -> closeQuietly(runningContext, true));

            context.eval("js", String.format(WRAPPER, source));
            return resolve(bindings, budget, sourceHash);
        } catch (PolyglotException e) {
            if (e.isCancelled()) {
                return agentContext.isRunCancelled()
                        ? failure(CodeModeErrorCode.CODE_MODE_CANCELLED, "用户已停止本轮对话", budget, sourceHash)
                        : failure(CodeModeErrorCode.CODE_MODE_TIMEOUT,
                        "Code Mode 执行超时（" + resolveTimeout(request) + "ms）", budget, sourceHash);
            }
            if (e.isSyntaxError()) {
                return failure(CodeModeErrorCode.CODE_MODE_INVALID_SOURCE,
                        "JavaScript 语法错误：" + e.getMessage(), budget, sourceHash);
            }
            return failure(bindings == null || bindings.errorCode() == null
                            ? CodeModeErrorCode.CODE_MODE_RUNTIME_ERROR
                            : CodeModeErrorCode.valueOf(bindings.errorCode()),
                    e.getMessage(), budget, sourceHash);
        } catch (CodeModeExecutionException e) {
            return failure(e.getErrorCode(), e.getMessage(), budget, sourceHash);
        } catch (RuntimeException e) {
            return failure(CodeModeErrorCode.CODE_MODE_RUNTIME_ERROR,
                    "Code Mode 执行失败：" + e.getMessage(), budget, sourceHash);
        } finally {
            if (timeout != null) {
                timeout.cancel(false);
            }
            if (unregisterAbort != null) {
                unregisterAbort.run();
            }
            if (context != null) {
                closeQuietly(context, false);
            }
        }
    }

    private CodeModeResult resolve(CodeModeJsBindings bindings,
                                   CodeModeExecutionBudget budget,
                                   String sourceHash) {
        if (bindings.latch().getCount() > 0) {
            // main 是同步且没有走 await 的极少数情况下，给微任务队列一点排空时间
            try {
                bindings.latch().await(Math.min(budget.remainingMs(), 1_000L), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return failure(CodeModeErrorCode.CODE_MODE_CANCELLED, "执行被中断", budget, sourceHash);
            }
        }
        String failureMessage = bindings.failure();
        if (failureMessage != null) {
            String code = bindings.errorCode();
            CodeModeErrorCode errorCode = code == null
                    ? CodeModeErrorCode.CODE_MODE_RUNTIME_ERROR
                    : safeCodeOf(code);
            return failure(errorCode, failureMessage, budget, sourceHash);
        }
        Object result = bindings.result() == null
                ? null
                : GraalJsValueConverter.toJavaObject(bindings.result());
        int serialized = JSON.toJSONString(result).length();
        if (serialized > maxOutputChars()) {
            return failure(CodeModeErrorCode.CODE_MODE_OUTPUT_TOO_LARGE,
                    "返回值序列化后 " + serialized + " 字符，超过上限 " + maxOutputChars(), budget, sourceHash);
        }
        return CodeModeResult.success(result, budget.snapshotLogs(), budget.elapsedMs(),
                budget.snapshotProducedFiles(), budget.toolCallCount(), sourceHash);
    }

    /**
     * 读取 workspace 内的 .js 脚本。只接受相对路径，只允许 scripts/ 与 jobs/。
     */
    private String readWorkspaceScript(AgentContext agentContext, String workspaceFile) {
        CodeModeRequestValidator.Validation validation = CodeModeRequestValidator.validateWorkspaceScriptPath(
                workspaceFile, this.properties.getWorkspace().getAllowedScriptPrefixes());
        if (!validation.isValid()) {
            throw new CodeModeExecutionException(
                    CodeModeErrorCode.valueOf(validation.errorCode()), validation.message());
        }
        if (workspaceService == null) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_CAPABILITY_UNAVAILABLE,
                    "workspace 未装配");
        }
        String root = agentContext.getWorkspaceRoot();
        Path workspaceRoot = StringUtils.isNotBlank(root)
                ? Path.of(root).toAbsolutePath().normalize()
                : workspaceService.resolveAndEnsureRoot(agentContext.getSessionId());
        Path script;
        try {
            script = workspaceService.resolveAllowedPath(workspaceRoot, workspaceFile.trim());
        } catch (WorkspaceAccessException e) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_WORKSPACE_FILE_DENIED, e.getMessage(), e);
        }
        if (!script.toAbsolutePath().normalize().startsWith(workspaceRoot)) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_WORKSPACE_FILE_DENIED,
                    "脚本路径不在当前会话 workspace 内");
        }
        if (!Files.isRegularFile(script)) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_WORKSPACE_FILE_NOT_FOUND,
                    "脚本文件不存在：" + workspaceFile);
        }
        String source;
        try {
            long size = Files.size(script);
            if (size > maxSourceChars() * 4L) {
                throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_INVALID_SOURCE,
                        "脚本文件超过大小上限");
            }
            source = Files.readString(script, StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_WORKSPACE_FILE_NOT_FOUND,
                    "读取脚本失败：" + e.getMessage(), e);
        }
        if (source.length() > maxSourceChars()) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_INVALID_SOURCE,
                    "脚本超过最大字符数 " + maxSourceChars());
        }
        return source;
    }

    private long resolveTimeout(CodeModeRequest request) {
        long requested = request == null ? 0L : request.timeoutMs();
        return CodeModeRequestValidator.resolveTimeoutMs(
                requested > 0 ? requested : null,
                this.properties.getDefaultTimeoutMs(),
                this.properties.getMaxTimeoutMs());
    }

    private int maxSourceChars() {
        return this.properties.getMaxSourceChars() > 0 ? this.properties.getMaxSourceChars() : 262_144;
    }

    private int maxOutputChars() {
        return this.properties.getMaxOutputChars() > 0 ? this.properties.getMaxOutputChars() : 1_048_576;
    }

    private static CodeModeResult failure(CodeModeErrorCode code,
                                          String message,
                                          CodeModeExecutionBudget budget,
                                          String sourceHash) {
        return failure(code.code(), message, budget, sourceHash);
    }

    private static CodeModeResult failure(String errorCode,
                                          String message,
                                          CodeModeExecutionBudget budget,
                                          String sourceHash) {
        return CodeModeResult.failure(
                errorCode == null ? null : safeCodeOf(errorCode),
                message,
                budget.snapshotLogs(),
                budget.elapsedMs(),
                budget.snapshotProducedFiles(),
                budget.toolCallCount(),
                sourceHash);
    }

    private static CodeModeErrorCode safeCodeOf(String code) {
        try {
            return CodeModeErrorCode.valueOf(code);
        } catch (RuntimeException e) {
            return CodeModeErrorCode.CODE_MODE_RUNTIME_ERROR;
        }
    }

    private static void closeQuietly(Context context, boolean cancelIfExecuting) {
        try {
            context.close(cancelIfExecuting);
        } catch (RuntimeException ignore) {
            // 关闭失败也要保证外层清理继续
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder builder = new StringBuilder("sha256:");
            for (byte b : digest.digest(bytes)) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            return "sha256:unavailable";
        }
    }
}
