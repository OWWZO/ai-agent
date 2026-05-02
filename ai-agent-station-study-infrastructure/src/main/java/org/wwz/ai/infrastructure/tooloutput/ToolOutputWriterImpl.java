package org.wwz.ai.infrastructure.tooloutput;

import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.reactor.mapper.IToolOutputCodeInterpreterDao;
import org.wwz.ai.domain.agent.reactor.mapper.IToolOutputDataAnalysisDao;
import org.wwz.ai.domain.agent.reactor.mapper.IToolOutputDeepSearchDao;
import org.wwz.ai.domain.agent.reactor.mapper.IToolOutputFileToolDao;
import org.wwz.ai.domain.agent.reactor.mapper.IToolOutputImageGenerationDao;
import org.wwz.ai.domain.agent.reactor.mapper.IToolOutputMultimodalAgentDao;
import org.wwz.ai.domain.agent.reactor.mapper.IToolOutputReportToolDao;
import org.wwz.ai.domain.agent.reactor.mapper.IToolOutputScriptRunnerDao;
import org.wwz.ai.domain.agent.reactor.model.ledger.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.CodeInterpreterToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.DataAnalysisToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.DeepSearchToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.FileToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ImageGenerationToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.MultimodalAgentToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ReportToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ScriptRunnerToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ToolOutputNames;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ToolOutputPersistCommand;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ToolStructuredOutput;
import org.wwz.ai.domain.agent.reactor.service.tooloutput.ToolOutputWriter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 输出表写入实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolOutputWriterImpl implements ToolOutputWriter {

    private final IToolOutputDeepSearchDao deepSearchDao;
    private final IToolOutputFileToolDao fileToolDao;
    private final IToolOutputCodeInterpreterDao codeInterpreterDao;
    private final IToolOutputReportToolDao reportToolDao;
    private final IToolOutputDataAnalysisDao dataAnalysisDao;
    private final IToolOutputMultimodalAgentDao multimodalAgentDao;
    private final IToolOutputImageGenerationDao imageGenerationDao;
    private final IToolOutputScriptRunnerDao scriptRunnerDao;

    @Override
    public void write(ToolOutputPersistCommand command) {
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
        writeInternal(command, true);
    }

    private void writeInternal(ToolOutputPersistCommand command, boolean strict) {
        if (command == null || command.getStructuredOutput() == null) {
            return;
        }
        String toolName = resolveToolName(command);
        if (!ToolOutputNames.isRichTool(toolName)
                || StringUtils.isBlank(command.getRequestId())
                || StringUtils.isBlank(command.getToolCallId())) {
            return;
        }
        try {
            switch (toolName) {
                case ToolOutputNames.DEEP_SEARCH -> handleInsertResult(command, deepSearchDao.insert(buildDeepSearchRow(command, cast(command, DeepSearchToolOutput.class))), strict);
                case ToolOutputNames.FILE_TOOL -> handleInsertResult(command, fileToolDao.insert(buildFileToolRow(command, cast(command, FileToolOutput.class))), strict);
                case ToolOutputNames.CODE_INTERPRETER -> handleInsertResult(command, codeInterpreterDao.insert(buildCodeInterpreterRow(command, cast(command, CodeInterpreterToolOutput.class))), strict);
                case ToolOutputNames.REPORT_TOOL -> handleInsertResult(command, reportToolDao.insert(buildReportToolRow(command, cast(command, ReportToolOutput.class))), strict);
                case ToolOutputNames.DATA_ANALYSIS -> handleInsertResult(command, dataAnalysisDao.insert(buildDataAnalysisRow(command, cast(command, DataAnalysisToolOutput.class))), strict);
                case ToolOutputNames.MULTIMODAL_AGENT -> handleInsertResult(command, multimodalAgentDao.insert(buildMultimodalRow(command, cast(command, MultimodalAgentToolOutput.class))), strict);
                case ToolOutputNames.IMAGE_GENERATION -> handleInsertResult(command, imageGenerationDao.insert(buildImageGenerationRow(command, cast(command, ImageGenerationToolOutput.class))), strict);
                case ToolOutputNames.SCRIPT_RUNNER -> handleInsertResult(command, scriptRunnerDao.insert(buildScriptRunnerRow(command, cast(command, ScriptRunnerToolOutput.class))), strict);
                default -> log.debug("skip unsupported tool output persist, toolName={}", toolName);
            }
        } catch (DuplicateKeyException e) {
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

    private Map<String, Object> buildFileToolRow(ToolOutputPersistCommand command, FileToolOutput output) {
        Map<String, Object> row = baseRow(command);
        row.put("command", output.getCommand());
        row.put("primaryFileName", output.getPrimaryFileName());
        row.put("contentStorageMode", output.getContentStorageMode());
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

    private Map<String, Object> buildReportToolRow(ToolOutputPersistCommand command, ReportToolOutput output) {
        Map<String, Object> row = baseRow(command);
        row.put("fileType", output.getFileType());
        row.put("summary", output.getSummary());
        row.put("content", output.getContent());
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

    private Map<String, Object> buildScriptRunnerRow(ToolOutputPersistCommand command, ScriptRunnerToolOutput output) {
        Map<String, Object> row = baseRow(command);
        row.put("skillName", output.getSkillName());
        row.put("scriptName", output.getScriptName());
        row.put("runtime", output.getRuntime());
        row.put("success", output.getSuccess());
        row.put("exitCode", output.getExitCode());
        row.put("stdout", output.getStdout());
        row.put("stderr", output.getStderr());
        row.put("summary", output.getSummary());
        return row;
    }

    private Map<String, Object> baseRow(ToolOutputPersistCommand command) {
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
