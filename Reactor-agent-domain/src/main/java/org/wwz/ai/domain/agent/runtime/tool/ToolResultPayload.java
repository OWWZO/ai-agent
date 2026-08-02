package org.wwz.ai.domain.agent.runtime.tool;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolStructuredOutput;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具执行结果载体。
 * 显式区分原始结果、主智能体 observation 和结构化输出，避免多种语义混用一个字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResultPayload {

    /**
     * 工具原始文本结果。
     * 主要用于日志、调试和普通工具结果展示。
     */
    private String toolResult;

    /**
     * 回传给主智能体继续推理的 observation。
     * 若为空且提供了 {@link #llmData}，由 {@link ToolObservationSerializer} 在 BaseAgent 中生成。
     */
    private String llmObservation;

    /**
     * 对齐 LeAgent {@code ToolResult.data} 的结构化载荷。
     * 成功时由 serialize_for_llm 序列化为 observation；失败时可作为 detail。
     */
    private Object llmData;

    /**
     * rich tool 强类型输出。
     */
    private ToolStructuredOutput structuredOutput;

    /** 图片工具结果的多模态内容，使用 data URL 以保留 MIME 类型。 */
    private String base64Image;

    private String imageMimeType;

    /**
     * 是否失败。
     */
    @Builder.Default
    private Boolean failed = Boolean.FALSE;

    /**
     * 工具错误信息。
     */
    private String errorMsg;

    /**
     * 纯文本工具的快捷工厂。
     */
    public static ToolResultPayload text(String resultText) {
        return ToolResultPayload.builder()
                .toolResult(resultText)
                .llmObservation(resultText)
                .llmData(resultText)
                .failed(Boolean.FALSE)
                .build();
    }

    /**
     * 对齐 LeAgent {@code ToolResult.ok(data)}：只带 data，observation 由中央 serialize 生成。
     */
    public static ToolResultPayload fromData(Object data) {
        return ToolResultPayload.builder()
                .toolResult(data instanceof String text ? text : null)
                .llmData(data)
                .failed(Boolean.FALSE)
                .build();
    }

    /**
     * 结构化 ledger 输出 + 主智能体 llmData（observation 由 serialize 生成，不写 prose）。
     */
    public static ToolResultPayload fromData(Object data, ToolStructuredOutput structuredOutput) {
        return ToolResultPayload.builder()
                .toolResult(data instanceof String text ? text : null)
                .llmData(data)
                .structuredOutput(structuredOutput)
                .failed(Boolean.FALSE)
                .build();
    }

    /**
     * 成功：注入 tool + ok=true，再合并业务字段。
     */
    public static ToolResultPayload okData(String toolName, Map<String, Object> fields) {
        return fromData(envelope(toolName, true, fields));
    }

    /**
     * 成功：注入 tool + ok=true + 业务字段，并挂 ledger structuredOutput。
     */
    public static ToolResultPayload okData(String toolName,
                                           Map<String, Object> fields,
                                           ToolStructuredOutput structuredOutput) {
        return fromData(envelope(toolName, true, fields), structuredOutput);
    }

    /**
     * 业务否决/未找到等：仍走 serialize 路径（failed=false），但 ok=false。
     */
    public static ToolResultPayload softFailData(String toolName, Map<String, Object> fields) {
        return fromData(envelope(toolName, false, fields));
    }

    /**
     * rich tool 强类型输出快捷工厂。
     * 注意：会预填 llmObservation，BaseAgent 不会再 serialize llmData。
     * 新工具请优先 {@link #fromData(Object, ToolStructuredOutput)}。
     */
    public static ToolResultPayload structured(String toolResult,
                                               String llmObservation,
                                               ToolStructuredOutput structuredOutput) {
        return ToolResultPayload.builder()
                .toolResult(toolResult)
                .llmObservation(llmObservation)
                .structuredOutput(structuredOutput)
                .failed(Boolean.FALSE)
                .build();
    }

    /**
     * 失败结果快捷工厂。
     */
    public static ToolResultPayload failure(String toolResult,
                                            String llmObservation,
                                            ToolStructuredOutput structuredOutput,
                                            String errorMsg) {
        return ToolResultPayload.builder()
                .toolResult(toolResult)
                .llmObservation(llmObservation)
                .structuredOutput(structuredOutput)
                .failed(Boolean.TRUE)
                .errorMsg(errorMsg)
                .build();
    }

    /**
     * 对齐 LeAgent {@code ToolResult.fail(error, data=detail)}。
     */
    public static ToolResultPayload failureFrom(String error, Object detail) {
        return ToolResultPayload.builder()
                .toolResult(error)
                .llmData(detail)
                .failed(Boolean.TRUE)
                .errorMsg(error)
                .build();
    }

    /**
     * 失败 + ledger structuredOutput + 可选 llmData detail。
     */
    public static ToolResultPayload failureFrom(String error,
                                                Object detail,
                                                ToolStructuredOutput structuredOutput) {
        return ToolResultPayload.builder()
                .toolResult(error)
                .llmData(detail)
                .structuredOutput(structuredOutput)
                .failed(Boolean.TRUE)
                .errorMsg(error)
                .build();
    }

    private static Map<String, Object> envelope(String toolName, boolean ok, Map<String, Object> fields) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tool", toolName);
        data.put("ok", ok);
        if (fields != null) {
            data.putAll(fields);
        }
        return data;
    }
}
