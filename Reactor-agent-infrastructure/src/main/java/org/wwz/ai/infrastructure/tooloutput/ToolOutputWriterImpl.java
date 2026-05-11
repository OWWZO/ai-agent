package org.wwz.ai.infrastructure.tooloutput;

import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.model.ExecutionLedgerConstants;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.CodeInterpreterToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.DataAnalysisToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.DeepSearchToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.FileToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ImageGenerationToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.MultimodalAgentToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.PlanningToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ReportToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ScriptRunnerToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputNames;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputPersistCommand;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolStructuredOutput;
import org.wwz.ai.domain.agent.ledger.tooloutput.ToolOutputWriter;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputCodeInterpreterDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputDataAnalysisDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputDeepSearchDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputFileToolDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputImageGenerationDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputMultimodalAgentDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputPlanningDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputReportToolDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputScriptRunnerDao;

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
    private final IToolOutputPlanningDao planningDao;

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
                case ToolOutputNames.PLANNING -> handleInsertResult(command, planningDao.insert(buildPlanningRow(command, cast(command, PlanningToolOutput.class))), strict);
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
        row.put("previewUrl", output.getPreviewUrl());
        row.put("downloadUrl", output.getDownloadUrl());
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

    private Map<String, Object> buildPlanningRow(ToolOutputPersistCommand command, PlanningToolOutput output) {
        Map<String, Object> row = baseRow(command);
        row.put("command", output.getCommand());
        row.put("beforePlanJson", toJson(output.getBeforePlan()));
        row.put("afterPlanJson", toJson(output.getAfterPlan()));
        row.put("currentStep", output.getCurrentStep());
        row.put("currentStepIndex", output.getCurrentStepIndex());
        row.put("autoAdvanced", output.getAutoAdvanced());
        row.put("autoFinished", output.getAutoFinished());
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
