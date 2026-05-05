package org.wwz.ai.infrastructure.tooloutput;

import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.ledger.entity.ArtifactRecord;
import org.wwz.ai.domain.agent.runtime.dto.Plan;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.CodeInterpreterToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.DataAnalysisToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.DeepSearchStage;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.DeepSearchToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.FileToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ImageGenerationToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.MultimodalAgentToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.PlanningToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ReportToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ScriptRunnerToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolFileRef;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputNames;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolOutputView;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolStructuredOutput;
import org.wwz.ai.domain.agent.ledger.tooloutput.ToolOutputReader;
import org.wwz.ai.infrastructure.dao.reactor.IArtifactLedgerDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputCodeInterpreterDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputDataAnalysisDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputDeepSearchDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputFileToolDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputImageGenerationDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputMultimodalAgentDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputPlanningDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputReportToolDao;
import org.wwz.ai.infrastructure.dao.reactor.IToolOutputScriptRunnerDao;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 输出表读取实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolOutputReaderImpl implements ToolOutputReader {

    private final IToolOutputDeepSearchDao deepSearchDao;
    private final IToolOutputFileToolDao fileToolDao;
    private final IToolOutputCodeInterpreterDao codeInterpreterDao;
    private final IToolOutputReportToolDao reportToolDao;
    private final IToolOutputDataAnalysisDao dataAnalysisDao;
    private final IToolOutputMultimodalAgentDao multimodalAgentDao;
    private final IToolOutputImageGenerationDao imageGenerationDao;
    private final IToolOutputScriptRunnerDao scriptRunnerDao;
    private final IToolOutputPlanningDao planningDao;
    private final IArtifactLedgerDao artifactLedgerDao;

    @Override
    public Optional<ToolStructuredOutput> readByInvocationId(String toolName, Long toolInvocationId) {
        if (StringUtils.isBlank(toolName) || toolInvocationId == null) {
            return Optional.empty();
        }
        return switch (toolName) {
            case ToolOutputNames.DEEP_SEARCH -> Optional.ofNullable(toDeepSearchOutput(deepSearchDao.queryByToolInvocationId(toolInvocationId)));
            case ToolOutputNames.FILE_TOOL -> Optional.ofNullable(toFileToolOutput(fileToolDao.queryByToolInvocationId(toolInvocationId)));
            case ToolOutputNames.CODE_INTERPRETER -> Optional.ofNullable(toCodeInterpreterOutput(codeInterpreterDao.queryByToolInvocationId(toolInvocationId)));
            case ToolOutputNames.REPORT_TOOL -> Optional.ofNullable(toReportToolOutput(reportToolDao.queryByToolInvocationId(toolInvocationId)));
            case ToolOutputNames.DATA_ANALYSIS -> Optional.ofNullable(toDataAnalysisOutput(dataAnalysisDao.queryByToolInvocationId(toolInvocationId)));
            case ToolOutputNames.MULTIMODAL_AGENT -> Optional.ofNullable(toMultimodalOutput(multimodalAgentDao.queryByToolInvocationId(toolInvocationId)));
            case ToolOutputNames.IMAGE_GENERATION -> Optional.ofNullable(toImageGenerationOutput(imageGenerationDao.queryByToolInvocationId(toolInvocationId)));
            case ToolOutputNames.SCRIPT_RUNNER -> Optional.ofNullable(toScriptRunnerOutput(scriptRunnerDao.queryByToolInvocationId(toolInvocationId)));
            case ToolOutputNames.PLANNING -> Optional.ofNullable(toPlanningOutput(planningDao.queryByToolInvocationId(toolInvocationId)));
            default -> Optional.empty();
        };
    }

    @Override
    public Optional<ToolOutputView> readDirect(String requestId, String toolCallId) {
        if (StringUtils.isBlank(requestId) || StringUtils.isBlank(toolCallId)) {
            return Optional.empty();
        }
        List<ToolOutputView> matches = new ArrayList<>();
        addIfPresent(matches, ToolOutputNames.DEEP_SEARCH, deepSearchDao.queryByRequestToolCall(requestId, toolCallId));
        addIfPresent(matches, ToolOutputNames.FILE_TOOL, fileToolDao.queryByRequestToolCall(requestId, toolCallId));
        addIfPresent(matches, ToolOutputNames.CODE_INTERPRETER, codeInterpreterDao.queryByRequestToolCall(requestId, toolCallId));
        addIfPresent(matches, ToolOutputNames.REPORT_TOOL, reportToolDao.queryByRequestToolCall(requestId, toolCallId));
        addIfPresent(matches, ToolOutputNames.DATA_ANALYSIS, dataAnalysisDao.queryByRequestToolCall(requestId, toolCallId));
        addIfPresent(matches, ToolOutputNames.MULTIMODAL_AGENT, multimodalAgentDao.queryByRequestToolCall(requestId, toolCallId));
        addIfPresent(matches, ToolOutputNames.IMAGE_GENERATION, imageGenerationDao.queryByRequestToolCall(requestId, toolCallId));
        addIfPresent(matches, ToolOutputNames.SCRIPT_RUNNER, scriptRunnerDao.queryByRequestToolCall(requestId, toolCallId));
        addIfPresent(matches, ToolOutputNames.PLANNING, planningDao.queryByRequestToolCall(requestId, toolCallId));
        if (matches.size() > 1) {
            log.warn("tool output direct lookup conflict, requestId={}, toolCallId={}, matchedTools={}",
                    requestId, toolCallId, matches.stream().map(ToolOutputView::getToolName).toList());
            return Optional.empty();
        }
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }

    private void addIfPresent(List<ToolOutputView> matches, String toolName, Map<String, Object> row) {
        ToolStructuredOutput output = switch (toolName) {
            case ToolOutputNames.DEEP_SEARCH -> toDeepSearchOutput(row);
            case ToolOutputNames.FILE_TOOL -> toFileToolOutput(row);
            case ToolOutputNames.CODE_INTERPRETER -> toCodeInterpreterOutput(row);
            case ToolOutputNames.REPORT_TOOL -> toReportToolOutput(row);
            case ToolOutputNames.DATA_ANALYSIS -> toDataAnalysisOutput(row);
            case ToolOutputNames.MULTIMODAL_AGENT -> toMultimodalOutput(row);
            case ToolOutputNames.IMAGE_GENERATION -> toImageGenerationOutput(row);
            case ToolOutputNames.SCRIPT_RUNNER -> toScriptRunnerOutput(row);
            case ToolOutputNames.PLANNING -> toPlanningOutput(row);
            default -> null;
        };
        ToolOutputView view = toView(toolName, row, output);
        if (view != null) {
            matches.add(view);
        }
    }

    private ToolStructuredOutput toDeepSearchOutput(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return DeepSearchToolOutput.of(
                stringValue(row, "query"),
                stringValue(row, "answer_summary", "answerSummary"),
                readStages(stringValue(row, "stages_json", "stagesJson"))
        );
    }

    private ToolStructuredOutput toFileToolOutput(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return FileToolOutput.builder()
                .command(stringValue(row, "command"))
                .primaryFileName(stringValue(row, "primary_file_name", "primaryFileName"))
                .previewUrl(stringValue(row, "preview_url", "previewUrl"))
                .downloadUrl(stringValue(row, "download_url", "downloadUrl"))
                .fileRefs(resolveFileRefs(row))
                .build();
    }

    private ToolStructuredOutput toCodeInterpreterOutput(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return CodeInterpreterToolOutput.builder()
                .codeOutput(stringValue(row, "code_output", "codeOutput"))
                .content(stringValue(row, "content"))
                .code(stringValue(row, "code"))
                .explain(stringValue(row, "explain"))
                .fileRefs(resolveFileRefs(row))
                .build();
    }

    private ToolStructuredOutput toReportToolOutput(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return ReportToolOutput.builder()
                .fileType(stringValue(row, "file_type", "fileType"))
                .summary(stringValue(row, "summary"))
                .content(stringValue(row, "content"))
                .fileRefs(resolveFileRefs(row))
                .build();
    }

    private ToolStructuredOutput toDataAnalysisOutput(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return DataAnalysisToolOutput.builder()
                .task(stringValue(row, "task"))
                .summary(stringValue(row, "summary"))
                .content(stringValue(row, "content"))
                .fileRefs(resolveFileRefs(row))
                .build();
    }

    private ToolStructuredOutput toMultimodalOutput(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return MultimodalAgentToolOutput.builder()
                .summary(stringValue(row, "summary"))
                .markdownContent(stringValue(row, "markdown_content", "markdownContent"))
                .fileRefs(resolveFileRefs(row))
                .build();
    }

    private ToolStructuredOutput toImageGenerationOutput(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return ImageGenerationToolOutput.builder()
                .prompt(stringValue(row, "prompt"))
                .mode(stringValue(row, "mode"))
                .summary(stringValue(row, "summary"))
                .size(stringValue(row, "size"))
                .batchCount(integerValue(row, "batch_count", "batchCount"))
                .sourceImageCount(integerValue(row, "source_image_count", "sourceImageCount"))
                .maskImageCount(integerValue(row, "mask_image_count", "maskImageCount"))
                .usedFallback(booleanValue(row, "used_fallback", "usedFallback"))
                .fileRefs(resolveFileRefs(row))
                .build();
    }

    private ToolStructuredOutput toScriptRunnerOutput(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return ScriptRunnerToolOutput.builder()
                .skillName(stringValue(row, "skill_name", "skillName"))
                .scriptName(stringValue(row, "script_name", "scriptName"))
                .runtime(stringValue(row, "runtime"))
                .success(booleanValue(row, "success"))
                .exitCode(integerValue(row, "exit_code", "exitCode"))
                .stdout(stringValue(row, "stdout"))
                .stderr(stringValue(row, "stderr"))
                .summary(stringValue(row, "summary"))
                .fileRefs(resolveFileRefs(row))
                .build();
    }

    private ToolStructuredOutput toPlanningOutput(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return PlanningToolOutput.builder()
                .command(stringValue(row, "command"))
                .beforePlan(readPlan(stringValue(row, "before_plan_json", "beforePlanJson")))
                .afterPlan(readPlan(stringValue(row, "after_plan_json", "afterPlanJson")))
                .currentStep(stringValue(row, "current_step", "currentStep"))
                .currentStepIndex(integerValue(row, "current_step_index", "currentStepIndex"))
                .autoAdvanced(booleanValue(row, "auto_advanced", "autoAdvanced"))
                .autoFinished(booleanValue(row, "auto_finished", "autoFinished"))
                .build();
    }

    private ToolOutputView toView(String toolName, Map<String, Object> row, ToolStructuredOutput output) {
        if (row == null) {
            return null;
        }
        return ToolOutputView.builder()
                .toolName(toolName)
                .requestId(stringValue(row, "request_id", "requestId"))
                .requestSource(stringValue(row, "request_source", "requestSource"))
                .sessionId(stringValue(row, "session_id", "sessionId"))
                .toolCallId(stringValue(row, "tool_call_id", "toolCallId"))
                .status(integerValue(row, "status"))
                .errorMsg(stringValue(row, "error_msg", "errorMsg"))
                .createdAt(localDateTimeValue(row, "created_at", "createdAt"))
                .structuredOutput(output)
                .build();
    }

    private List<DeepSearchStage> readStages(String json) {
        if (StringUtils.isBlank(json)) {
            return new ArrayList<>();
        }
        return JSON.parseArray(json, DeepSearchStage.class);
    }

    private Plan readPlan(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        return JSON.parseObject(json, Plan.class);
    }

    private List<ToolFileRef> resolveFileRefs(Map<String, Object> row) {
        if (row == null || artifactLedgerDao == null) {
            return List.of();
        }
        Long toolInvocationId = longValue(row, "tool_invocation_id", "toolInvocationId");
        List<ArtifactRecord> artifacts;
        if (toolInvocationId != null) {
            artifacts = artifactLedgerDao.queryOutputArtifactsByToolInvocationId(toolInvocationId);
        } else {
            Long runId = longValue(row, "run_id", "runId");
            String requestId = stringValue(row, "request_id", "requestId");
            String toolCallId = stringValue(row, "tool_call_id", "toolCallId");
            if (StringUtils.isBlank(toolCallId)) {
                return List.of();
            }
            if (runId != null) {
                artifacts = artifactLedgerDao.queryOutputArtifactsByRunIdAndToolCallId(runId, toolCallId);
            } else if (StringUtils.isNotBlank(requestId)) {
                artifacts = artifactLedgerDao.queryOutputArtifactsByRequestIdAndToolCallId(requestId, toolCallId);
            } else {
                return List.of();
            }
        }
        if (artifacts == null || artifacts.isEmpty()) {
            return List.of();
        }
        List<ToolFileRef> fileRefs = new ArrayList<>(artifacts.size());
        for (ArtifactRecord artifact : artifacts) {
            if (artifact == null) {
                continue;
            }
            fileRefs.add(ToolFileRef.builder()
                    .fileName(artifact.getFileName())
                    .downloadUrl(artifact.getDownloadUrl())
                    .previewUrl(artifact.getPreviewUrl())
                    .ossUrl(artifact.getDownloadUrl())
                    .domainUrl(artifact.getPreviewUrl())
                    .fileSize(artifact.getFileSize())
                    .mimeType(artifact.getMimeType())
                    .build());
        }
        return fileRefs;
    }

    private String stringValue(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            if (row.containsKey(key) && row.get(key) != null) {
                return String.valueOf(row.get(key));
            }
        }
        return null;
    }

    private Integer integerValue(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Number number) {
                return number.intValue();
            }
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long longValue(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Boolean booleanValue(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Boolean booleanValue) {
                return booleanValue;
            }
            if (value instanceof Number number) {
                return number.intValue() == 1;
            }
            return Boolean.parseBoolean(String.valueOf(value));
        }
        return null;
    }

    private LocalDateTime localDateTimeValue(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof LocalDateTime localDateTime) {
                return localDateTime;
            }
            if (value instanceof Timestamp timestamp) {
                return timestamp.toLocalDateTime();
            }
        }
        return null;
    }
}
