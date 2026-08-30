package org.wwz.ai.infrastructure.tooloutput;

import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.CanvasPublishToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.CodeInterpreterToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.DataAnalysisToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.DeepSearchToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.GenUiPatchToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.GenUiTreeToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ImageGenerationToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.MultimodalAgentToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputNames;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputPersistCommand;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolStructuredOutput;
import org.wwz.ai.domain.agent.ledger.tooloutput.ToolOutputWriter;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputCanvasPublishDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputCodeInterpreterDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputDataAnalysisDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputDeepSearchDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputEmitUiPatchDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputEmitUiTreeDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputImageGenerationDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputMultimodalAgentDao;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Execution Ledger 工具输出投影写入实现。
 *
 * <p>支持持久化的 rich tool 写入自己的专属输出表，通用 tool invocation 只保留执行
 * 事实和关联标识；本类负责把领域结构化输出转换为 DAO 行并按工具名分派。</p>
 *
 * <p>{@link #write(ToolOutputPersistCommand)} 采用旁路失败语义，适合请求收尾；
 * {@link #writeOrThrow(ToolOutputPersistCommand)} 保留失败，适合调用方必须感知投影
 * 是否建立的场景。重复键和 DAO 返回零行遵循输出表的幂等约束。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolOutputWriterImpl implements ToolOutputWriter {

    private final IToolOutputDeepSearchDao deepSearchDao;
    private final IToolOutputCodeInterpreterDao codeInterpreterDao;
    private final IToolOutputDataAnalysisDao dataAnalysisDao;
    private final IToolOutputMultimodalAgentDao multimodalAgentDao;
    private final IToolOutputImageGenerationDao imageGenerationDao;
    private final IToolOutputCanvasPublishDao canvasPublishDao;
    private final IToolOutputEmitUiTreeDao emitUiTreeDao;
    private final IToolOutputEmitUiPatchDao emitUiPatchDao;

    @Override
    public void write(ToolOutputPersistCommand command) {
        // 普通写入用于请求收尾或旁路投影：持久化失败只记录日志，不能让已经完成的 Agent 请求再次失败。
        try {
            writeInternal(command, false);
        } catch (Exception e) {
            log.error("tool output persist failed, toolName={}, requestId={}, toolCallId={}, toolInvocationId={}",
                    resolveToolName(command), command == null ? null : command.getRequestId(),
                    command == null ? null : command.getToolCallId(), command == null ? null : command.getToolInvocationId(), e);
        }
    }

    @Override
    public void writeOrThrow(ToolOutputPersistCommand command) {
        // 严格写入用于需要感知投影失败的场景，保留数据库异常和插入结果异常给调用方处理。
        writeInternal(command, true);
    }

    private void writeInternal(ToolOutputPersistCommand command, boolean strict) {
        if (command == null || command.getStructuredOutput() == null) {
            return;
        }
        String toolName = resolveToolName(command);
        if (!ToolOutputNames.isPersistedTool(toolName)
                || StringUtils.isBlank(command.getRequestId())
                || StringUtils.isBlank(command.getToolCallId())) {
            return;
        }
        try {
             // 每种可持久化 rich tool 使用独立输出表，但共享同一组账本关联字段。
            switch (toolName) {
                case ToolOutputNames.DEEP_SEARCH -> handleInsertResult(command, deepSearchDao.insert(buildDeepSearchRow(command, cast(command, DeepSearchToolOutput.class))), strict);
                case ToolOutputNames.CODE_INTERPRETER -> handleInsertResult(command, codeInterpreterDao.insert(buildCodeInterpreterRow(command, cast(command, CodeInterpreterToolOutput.class))), strict);
                case ToolOutputNames.DATA_ANALYSIS -> handleInsertResult(command, dataAnalysisDao.insert(buildDataAnalysisRow(command, cast(command, DataAnalysisToolOutput.class))), strict);
                case ToolOutputNames.MULTIMODAL_AGENT -> handleInsertResult(command, multimodalAgentDao.insert(buildMultimodalRow(command, cast(command, MultimodalAgentToolOutput.class))), strict);
                case ToolOutputNames.IMAGE_GENERATION -> handleInsertResult(command, imageGenerationDao.insert(buildImageGenerationRow(command, cast(command, ImageGenerationToolOutput.class))), strict);
                case ToolOutputNames.CANVAS_PUBLISH -> handleInsertResult(command, canvasPublishDao.insert(buildCanvasPublishRow(command, cast(command, CanvasPublishToolOutput.class))), strict);
                case ToolOutputNames.EMIT_UI_TREE -> handleInsertResult(command, emitUiTreeDao.insert(buildEmitUiTreeRow(command, cast(command, GenUiTreeToolOutput.class))), strict);
                case ToolOutputNames.EMIT_UI_PATCH -> handleInsertResult(command, emitUiPatchDao.insert(buildEmitUiPatchRow(command, cast(command, GenUiPatchToolOutput.class))), strict);
                default -> log.debug("skip unsupported tool output persist, toolName={}", toolName);
            }
        } catch (DuplicateKeyException e) {
            // 输出表以请求/工具调用建立幂等约束；普通模式把重复写视为重放，严格模式则交给调用方决定是否失败。
            if (strict) {
                throw e;
            }
            log.warn("tool output duplicate write ignored, toolName={}, requestId={}, toolCallId={}, toolInvocationId={}",
                    toolName, command.getRequestId(), command.getToolCallId(), command.getToolInvocationId());
        }
    }

    private Map<String, Object> buildDeepSearchRow(ToolOutputPersistCommand command, DeepSearchToolOutput output) {
        Map<String, Object> row = baseRow(command);
        row.put("query", output.getQuery());
        row.put("answerSummary", output.getAnswerSummary());
        row.put("stagesJson", toJson(output.getStages()));
        return row;
    }

    private Map<String, Object> buildCodeInterpreterRow(ToolOutputPersistCommand command, CodeInterpreterToolOutput output) {
        Map<String, Object> row = baseRow(command);
        row.put("codeOutput", output.getCodeOutput());
        row.put("content", output.getContent());
        row.put("code", output.getCode());
        row.put("explain", output.getExplain());
        return row;
    }

    private Map<String, Object> buildDataAnalysisRow(ToolOutputPersistCommand command, DataAnalysisToolOutput output) {
        Map<String, Object> row = baseRow(command);
        row.put("task", output.getTask());
        row.put("summary", output.getSummary());
        row.put("content", output.getContent());
        return row;
    }

    private Map<String, Object> buildMultimodalRow(ToolOutputPersistCommand command, MultimodalAgentToolOutput output) {
        Map<String, Object> row = baseRow(command);
        row.put("summary", output.getSummary());
        row.put("markdownContent", output.getMarkdownContent());
        return row;
    }

    private Map<String, Object> buildImageGenerationRow(ToolOutputPersistCommand command, ImageGenerationToolOutput output) {
        Map<String, Object> row = baseRow(command);
        row.put("prompt", output.getPrompt());
        row.put("mode", output.getMode());
        row.put("summary", output.getSummary());
        row.put("size", output.getSize());
        row.put("batchCount", output.getBatchCount());
        row.put("sourceImageCount", output.getSourceImageCount());
        row.put("maskImageCount", output.getMaskImageCount());
        row.put("usedFallback", output.getUsedFallback());
        return row;
    }

    private Map<String, Object> buildCanvasPublishRow(ToolOutputPersistCommand command, CanvasPublishToolOutput output) {
        Map<String, Object> row = baseRow(command);
        row.put("title", output.getTitle());
        row.put("mode", output.getMode());
        row.put("primaryFileName", output.getPrimaryFileName());
        row.put("previewUrl", output.getPreviewUrl());
        row.put("downloadUrl", output.getDownloadUrl());
        row.put("openInPanel", output.getOpenInPanel() == null ? null : (Boolean.TRUE.equals(output.getOpenInPanel()) ? 1 : 0));
        row.put("salvaged", output.getSalvaged() == null ? null : (Boolean.TRUE.equals(output.getSalvaged()) ? 1 : 0));
        return row;
    }

    private Map<String, Object> buildEmitUiTreeRow(ToolOutputPersistCommand command, GenUiTreeToolOutput output) {
        Map<String, Object> row = baseRow(command);
        row.put("canvasId", output.getCanvasId());
        row.put("salvaged", output.getSalvaged() == null ? null : (Boolean.TRUE.equals(output.getSalvaged()) ? 1 : 0));
        row.put("treeJson", toJson(output.getTree()));
        return row;
    }

    private Map<String, Object> buildEmitUiPatchRow(ToolOutputPersistCommand command, GenUiPatchToolOutput output) {
        Map<String, Object> row = baseRow(command);
        row.put("canvasId", output.getCanvasId());
        row.put("seq", output.getSeq());
        row.put("patchesJson", toJson(output.getPatches()));
        return row;
    }

    private Map<String, Object> baseRow(ToolOutputPersistCommand command) {
        // 基础关联字段必须和 Execution Ledger 的 tool invocation 对齐，具体工具字段只写入各自的输出表。
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("toolInvocationId", command.getToolInvocationId());
        row.put("runId", command.getRunId());
        row.put("requestId", command.getRequestId());
        row.put("requestSource", StringUtils.defaultIfBlank(command.getRequestSource(), ExecutionLedgerConstants.REQUEST_SOURCE_AGENT));
        row.put("sessionId", command.getSessionId());
        row.put("toolCallId", command.getToolCallId());
        row.put("status", command.getStatus());
        row.put("errorMsg", command.getErrorMsg());
        return row;
    }

    private String resolveToolName(ToolOutputPersistCommand command) {
        if (StringUtils.isNotBlank(command.getToolName())) {
            return command.getToolName();
        }
        ToolStructuredOutput structuredOutput = command.getStructuredOutput();
        return structuredOutput == null ? "" : structuredOutput.getToolName();
    }

    private void handleInsertResult(ToolOutputPersistCommand command, int inserted, boolean strict) {
        if (inserted > 0) {
            return;
        }
        if (strict) {
            throw new IllegalStateException(String.format(
                    "tool output duplicate or ignored, toolName=%s, requestId=%s, toolCallId=%s, toolInvocationId=%s",
                    resolveToolName(command), command.getRequestId(), command.getToolCallId(), command.getToolInvocationId()));
        }
        // DAO 返回 0 同样表示未建立新投影（例如数据库方言未抛 DuplicateKeyException），保持普通模式的幂等语义。
        log.warn("tool output first-write-wins ignored duplicate, toolName={}, requestId={}, toolCallId={}, toolInvocationId={}",
                resolveToolName(command), command.getRequestId(), command.getToolCallId(), command.getToolInvocationId());
    }

    private String toJson(Object value) {
        return JSON.toJSONString(value);
    }

    private <T extends ToolStructuredOutput> T cast(ToolOutputPersistCommand command, Class<T> type) {
        ToolStructuredOutput output = command.getStructuredOutput();
        if (!type.isInstance(output)) {
            throw new IllegalArgumentException("tool output type mismatch, expected=" + type.getSimpleName());
        }
        return type.cast(output);
    }
}
