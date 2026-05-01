package org.wwz.ai.infrastructure.tooloutput;

import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.reactor.mapper.IToolOutputCodeInterpreterDao;
import org.wwz.ai.domain.agent.reactor.mapper.IToolOutputDataAnalysisDao;
import org.wwz.ai.domain.agent.reactor.mapper.IToolOutputDeepSearchDao;
import org.wwz.ai.domain.agent.reactor.mapper.IToolOutputFileToolDao;
import org.wwz.ai.domain.agent.reactor.mapper.IToolOutputImageGenerationDao;
import org.wwz.ai.domain.agent.reactor.mapper.IToolOutputMultimodalAgentDao;
import org.wwz.ai.domain.agent.reactor.mapper.IToolOutputReportToolDao;
import org.wwz.ai.domain.agent.reactor.mapper.IToolOutputScriptRunnerDao;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.CodeInterpreterToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.DataAnalysisToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.DeepSearchStage;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.DeepSearchToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.FileToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ImageGenerationToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.MultimodalAgentToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ReportToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ScriptRunnerToolOutput;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ToolFileRef;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ToolOutputNames;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ToolOutputView;
import org.wwz.ai.domain.agent.reactor.model.tooloutput.ToolStructuredOutput;
import org.wwz.ai.domain.agent.reactor.service.tooloutput.ToolOutputReader;

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
        return DeepSearchToolOutput.builder()
                .query(stringValue(row, "query"))
                .answerSummary(stringValue(row, "answer_summary", "answerSummary"))
                .stages(readStages(stringValue(row, "stages_json", "stagesJson")))
                .build();
    }

    private ToolStructuredOutput toFileToolOutput(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return FileToolOutput.builder()
                .command(stringValue(row, "command"))
                .primaryFileName(stringValue(row, "primary_file_name", "primaryFileName"))
                .contentStorageMode(stringValue(row, "content_storage_mode", "contentStorageMode"))
                .fileRefs(readFileRefs(stringValue(row, "file_refs_json", "fileRefsJson")))
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
                .fileRefs(readFileRefs(stringValue(row, "file_refs_json", "fileRefsJson")))
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
                .fileRefs(readFileRefs(stringValue(row, "file_refs_json", "fileRefsJson")))
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
                .fileRefs(readFileRefs(stringValue(row, "file_refs_json", "fileRefsJson")))
                .build();
    }

    private ToolStructuredOutput toMultimodalOutput(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        return MultimodalAgentToolOutput.builder()
                .summary(stringValue(row, "summary"))
                .markdownContent(stringValue(row, "markdown_content", "markdownContent"))
                .fileRefs(readFileRefs(stringValue(row, "file_refs_json", "fileRefsJson")))
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
                .fileRefs(readFileRefs(stringValue(row, "file_refs_json", "fileRefsJson")))
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
                .fileRefs(readFileRefs(stringValue(row, "file_refs_json", "fileRefsJson")))
                .build();
    }

    private ToolOutputView toView(String toolName, Map<String, Object> row, ToolStructuredOutput output) {
        if (row == null) {
            return null;
        }
        return ToolOutputView.builder()
                .toolName(toolName)
                .requestId(stringValue(row, "request_id", "requestId"))
                .sessionId(stringValue(row, "session_id", "sessionId"))
                .toolCallId(stringValue(row, "tool_call_id", "toolCallId"))
                .status(integerValue(row, "status"))
                .errorMsg(stringValue(row, "error_msg", "errorMsg"))
                .structuredOutput(output)
                .build();
    }

    private List<DeepSearchStage> readStages(String json) {
        if (StringUtils.isBlank(json)) {
            return new ArrayList<>();
        }
        return JSON.parseArray(json, DeepSearchStage.class);
    }

    private List<ToolFileRef> readFileRefs(String json) {
        if (StringUtils.isBlank(json)) {
            return new ArrayList<>();
        }
        return JSON.parseArray(json, ToolFileRef.class);
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

    private Boolean booleanValue(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object value = row.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Boolean booleanValue) {
                return booleanValue;
            }
            return Boolean.parseBoolean(String.valueOf(value));
        }
        return null;
    }
}
