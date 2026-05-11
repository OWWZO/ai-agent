package org.wwz.ai.domain.agent.runtime.dto.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {
    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 执行状态
     */
    private ExecutionStatus status;

    /**
     * 执行结果
     */
    private Object result;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 执行时间（毫秒）
     */
    private Long executionTime;

    /**
     * 执行参数
     */
    private Object parameters;

    /**
     * 执行状态枚举
     */
    public enum ExecutionStatus {
        SUCCESS,    // 执行成功
        FAILED,     // 执行失败
        TIMEOUT,    // 执行超时
        CANCELLED,  // 执行被取消
        SKIPPED     // 执行被跳过
    }

    /**
     * 创建成功结果
     */
    public static ToolResult success(String toolName, Object result, Object parameters) {
        return ToolResult.builder()
                .toolName(toolName)
                .status(ExecutionStatus.SUCCESS)
                .result(result)
                .parameters(parameters)
                .build();
    }

    /**
     * 创建失败结果
     */
    public static ToolResult failed(String toolName, String error, Object parameters) {
        return ToolResult.builder()
                .toolName(toolName)
                .status(ExecutionStatus.FAILED)
                .error(error)
                .parameters(parameters)
                .build();
    }

    /**
     * 创建超时结果
     */
    public static ToolResult timeout(String toolName, Object parameters) {
        return ToolResult.builder()
                .toolName(toolName)
                .status(ExecutionStatus.TIMEOUT)
                .parameters(parameters)
                .build();
    }

    /**
     * 创建取消结果
     */
    public static ToolResult cancelled(String toolName, Object parameters) {
        return ToolResult.builder()
                .toolName(toolName)
                .status(ExecutionStatus.CANCELLED)
                .parameters(parameters)
                .build();
    }

    /**
     * 创建跳过结果
     */
    public static ToolResult skipped(String toolName, Object parameters) {
        return ToolResult.builder()
                .toolName(toolName)
                .status(ExecutionStatus.SKIPPED)
                .parameters(parameters)
                .build();
    }

    /**
     * 检查是否执行成功
     */
    public boolean isSuccess() {
        return status == ExecutionStatus.SUCCESS;
    }

    /**
     * 检查是否执行失败
     */
    public boolean isFailed() {
        return status == ExecutionStatus.FAILED;
    }

    /**
     * 检查是否执行超时
     */
    public boolean isTimeout() {
        return status == ExecutionStatus.TIMEOUT;
    }

    /**
     * 检查是否被取消
     */
    public boolean isCancelled() {
        return status == ExecutionStatus.CANCELLED;
    }

    /**
     * 检查是否被跳过
     */
    public boolean isSkipped() {
        return status == ExecutionStatus.SKIPPED;
    }

    /**
     * 获取结果或抛出异常
     */
    public <T> T getResultOrThrow(Class<T> resultType) {
        if (!isSuccess()) {
            throw new IllegalStateException("Tool execution failed: " + error);
        }

        if (result == null) {
            throw new IllegalStateException("Tool execution result is null");
        }

        if (!resultType.isInstance(result)) {
            throw new ClassCastException("Result is not of type " + resultType.getName());
        }

        return resultType.cast(result);
    }

    /**
     * 获取结果或返回默认值
     */
    public <T> T getResultOrDefault(Class<T> resultType, T defaultValue) {
        try {
            return getResultOrThrow(resultType);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 获取错误信息或返回默认值
     */
    public String getErrorOrDefault(String defaultValue) {
        return error != null ? error : defaultValue;
    }

    /**
     * 获取执行时间或返回默认值
     */
    public Long getExecutionTimeOrDefault(Long defaultValue) {
        return executionTime != null ? executionTime : defaultValue;
    }

    /**
     * 转换为字符串表示
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ToolResult{")
                .append("toolName='").append(toolName).append("'")
                .append(", status=").append(status);

        if (isSuccess()) {
            sb.append(", result=").append(result);
        } else {
            sb.append(", error='").append(error).append("'");
        }

        if (executionTime != null) {
            sb.append(", executionTime=").append(executionTime).append("ms");
        }

        sb.append("}");
        return sb.toString();
    }
}