package org.wwz.ai.infrastructure.codemode;

import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.codemode.CodeModeErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 单次 Code Mode 执行的配额账本。
 * <p>
 * 三重配额：整体 wall-clock、内部 capability 调用次数、累计写入字节。
 * 每次 capability 调用前都要过 {@link #beginCall(AgentContext)}，
 * 它会同时检查用户取消和剩余时间，保证超时/停止能穿透到阻塞中的 CLI 调用。
 * </p>
 */
public final class CodeModeExecutionBudget {

    private static final int MAX_LOGS = 200;

    private final long startedNanos;
    private final long deadlineNanos;
    private final int maxToolCalls;
    private final long maxTotalWriteBytes;

    private int toolCalls;
    private long writtenBytes;
    private final List<String> logs = new ArrayList<>();
    private final List<String> producedFiles = new ArrayList<>();

    public CodeModeExecutionBudget(long timeoutMs, int maxToolCalls, long maxTotalWriteBytes) {
        long now = System.nanoTime();
        this.startedNanos = now;
        this.deadlineNanos = now + TimeUnit.MILLISECONDS.toNanos(Math.max(1L, timeoutMs));
        this.maxToolCalls = maxToolCalls > 0 ? maxToolCalls : 100;
        this.maxTotalWriteBytes = maxTotalWriteBytes > 0 ? maxTotalWriteBytes : 134_217_728L;
    }

    /**
     * 开始一次 capability 调用：先判取消，再判超时，最后判调用次数。
     */
    public synchronized void beginCall(AgentContext agentContext) {
        if (agentContext != null && agentContext.isRunCancelled()) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_CANCELLED, "用户已停止本轮对话");
        }
        if (remainingMs() <= 0) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_TIMEOUT, "Code Mode 执行超时");
        }
        if (toolCalls >= maxToolCalls) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_TOOL_CALL_LIMIT,
                    "内部能力调用次数超过上限 " + maxToolCalls);
        }
        toolCalls++;
    }

    /** 剩余毫秒；已超时返回 0。 */
    public long remainingMs() {
        long remaining = deadlineNanos - System.nanoTime();
        return remaining <= 0 ? 0L : TimeUnit.NANOSECONDS.toMillis(remaining);
    }

    public synchronized void accountWrite(long bytes) {
        writtenBytes += Math.max(0L, bytes);
        if (writtenBytes > maxTotalWriteBytes) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_MEMORY_LIMIT,
                    "累计写入字节超过预算 " + maxTotalWriteBytes);
        }
    }

    public synchronized int toolCallCount() {
        return toolCalls;
    }

    public synchronized void log(String level, String message) {
        if (logs.size() >= MAX_LOGS) {
            return;
        }
        logs.add("[" + level + "] " + message);
    }

    public synchronized void produced(String relativePath) {
        if (relativePath != null && !producedFiles.contains(relativePath)) {
            producedFiles.add(relativePath);
        }
    }

    public synchronized List<String> snapshotLogs() {
        return new ArrayList<>(logs);
    }

    public synchronized List<String> snapshotProducedFiles() {
        return new ArrayList<>(producedFiles);
    }

    public long elapsedMs() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }
}
