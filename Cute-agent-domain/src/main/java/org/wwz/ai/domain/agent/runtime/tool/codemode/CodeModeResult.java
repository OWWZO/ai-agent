package org.wwz.ai.domain.agent.runtime.tool.codemode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Code Mode 执行结果。
 * <p>
 * {@code result} 必须是 JSON-safe 数据（null/String/Boolean/Number/List/Map），
 * 由执行器在返回前完成转换，不允许携带 Java 对象或引擎 Value。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeModeResult {

    private boolean success;

    private Object result;

    private String errorCode;

    private String errorMessage;

    /** console.log/warn/error 收集的执行日志（已截断）。 */
    private List<String> logs;

    private long durationMs;

    /** 本次执行写回 workspace 的相对路径列表。 */
    private List<String> producedFiles;

    /** 内部 capability 调用次数。 */
    private int toolCallCount;

    /** source 或 workspaceFile 源码的 SHA-256（不写源码全文）。 */
    private String sourceHash;

    public static CodeModeResult success(Object result, List<String> logs, long durationMs,
                                         List<String> producedFiles, int toolCallCount, String sourceHash) {
        return CodeModeResult.builder()
                .success(true)
                .result(result)
                .logs(logs == null ? new ArrayList<>() : logs)
                .durationMs(durationMs)
                .producedFiles(producedFiles == null ? new ArrayList<>() : producedFiles)
                .toolCallCount(toolCallCount)
                .sourceHash(sourceHash)
                .build();
    }

    public static CodeModeResult failure(CodeModeErrorCode errorCode,
                                         String errorMessage,
                                         List<String> logs,
                                         long durationMs,
                                         List<String> producedFiles,
                                         int toolCallCount,
                                         String sourceHash) {
        return CodeModeResult.builder()
                .success(false)
                .errorCode(errorCode == null ? null : errorCode.code())
                .errorMessage(errorMessage)
                .logs(logs == null ? new ArrayList<>() : logs)
                .durationMs(durationMs)
                .producedFiles(producedFiles == null ? new ArrayList<>() : producedFiles)
                .toolCallCount(toolCallCount)
                .sourceHash(sourceHash)
                .build();
    }
}
