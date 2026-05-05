package org.wwz.ai.domain.agent.runtime.tool.common.skill;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.artifact.ToolArtifactSource;
import org.wwz.ai.domain.agent.runtime.dto.File;
import org.wwz.ai.domain.agent.runtime.dto.skill.ScriptRunnerToolRequest;
import org.wwz.ai.domain.agent.runtime.dto.skill.ScriptRunnerToolResponse;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillDefinition;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillLoadException;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillScriptDefinition;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillScriptRunnerClient;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ScriptRunnerToolOutput;
import org.wwz.ai.domain.agent.ledger.model.tooloutput.ToolFileRefMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行已注册 skill 脚本的工具。
 */
public class ScriptRunnerTool extends AbstractSkillPathTool {

    private final SkillScriptRunnerClient skillScriptRunnerClient;

    public ScriptRunnerTool(SkillRegistry skillRegistry,
                            SkillRuntimeOptions skillRuntimeOptions,
                            SkillScriptRunnerClient skillScriptRunnerClient) {
        super(skillRegistry, skillRuntimeOptions);
        this.skillScriptRunnerClient = skillScriptRunnerClient;
    }

    @Override
    public String getName() {
        return "script_runner_tool";
    }

    @Override
    public String getDescription() {
        return "这是一个 skill 脚本执行工具，用于执行已注册的脚本定义。"
                + "调用时传入 skill_name、script_name，并可按需补充 arguments 与 argv。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("skill_name", Map.of("type", "string", "description", "要执行脚本所属的 skill 名称"));
        properties.put("script_name", Map.of("type", "string", "description", "脚本名称，必须已在 skill 中注册"));
        Map<String, Object> argumentsSchema = new LinkedHashMap<>();
        argumentsSchema.put("type", "object");
        argumentsSchema.put("description", "结构化参数，会透传给脚本环境变量");
        argumentsSchema.put("properties", new LinkedHashMap<String, Object>());
        argumentsSchema.put("required", new ArrayList<String>());
        properties.put("arguments", argumentsSchema);
        properties.put("argv", Map.of(
                "type", "array",
                "description", "附加命令行参数数组",
                "items", Map.of("type", "string")
        ));
        properties.put("timeout_seconds", Map.of("type", "integer", "description", "本次执行超时时间，单位秒"));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("skill_name", "script_name"));
        return parameters;
    }

    @Override
    public Object execute(Object input) {
        try {
            Map<String, Object> params = requireInputMap(input);
            String skillName = requireText(params, "skill_name");
            String scriptName = requireText(params, "script_name");

            SkillDefinition skillDefinition = skillRegistry.getRequiredSkill(skillName);
            SkillScriptDefinition scriptDefinition = skillRegistry.getRequiredScript(skillName, scriptName);
            ScriptRunnerToolRequest request = ScriptRunnerToolRequest.builder()
                    .requestId(resolveRequestId())
                    .skillName(skillDefinition.getName())
                    .skillBasePath(skillDefinition.getBasePath().toString())
                    .scriptName(scriptDefinition.getScriptName())
                    .scriptPath(scriptDefinition.getRelativePath())
                    .runtime(scriptDefinition.getRuntime())
                    .arguments(readArguments(params))
                    .argv(readArgv(params))
                    .timeoutSeconds(readTimeout(params))
                    .build();

            ScriptRunnerToolResponse response = skillScriptRunnerClient.run(request);
            ToolArtifactSource artifactSource = agentContext.requireCurrentToolArtifactSource(getName());
            appendGeneratedFiles(response, artifactSource);
            return buildSuccessPayload(response);
        } catch (SkillLoadException e) {
            log.warn("{} script_runner_tool failed, input={}", requestId(), input, e);
            return buildFailurePayload(e.getMessage());
        } catch (Exception e) {
            log.error("{} script_runner_tool execute error, input={}", requestId(), input, e);
            return buildFailurePayload("script_runner_tool execute failed");
        }
    }

    private String requireText(Map<String, Object> params, String fieldName) {
        Object value = params.get(fieldName);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new SkillLoadException(fieldName + " is required");
        }
        return String.valueOf(value).trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readArguments(Map<String, Object> params) {
        Object argumentsValue = params.get("arguments");
        if (argumentsValue == null) {
            return new LinkedHashMap<>();
        }
        if (!(argumentsValue instanceof Map<?, ?> rawArguments)) {
            throw new SkillLoadException("arguments must be an object");
        }
        Map<String, Object> arguments = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawArguments.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            arguments.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return arguments;
    }

    private List<String> readArgv(Map<String, Object> params) {
        Object argvValue = params.get("argv");
        if (argvValue == null) {
            return new ArrayList<>();
        }
        if (!(argvValue instanceof List<?> rawArgv)) {
            throw new SkillLoadException("argv must be an array");
        }
        List<String> argv = new ArrayList<>();
        for (Object item : rawArgv) {
            if (item == null) {
                continue;
            }
            argv.add(String.valueOf(item));
        }
        return argv;
    }

    private int readTimeout(Map<String, Object> params) {
        int timeoutSeconds = readInt(params, "timeout_seconds", skillRuntimeOptions.getDefaultScriptTimeoutSeconds());
        return Math.max(1, timeoutSeconds);
    }

    private String resolveRequestId() {
        if (agentContext == null) {
            return "unknown";
        }
        if (agentContext.getSessionId() != null && !agentContext.getSessionId().isBlank()) {
            return agentContext.getSessionId();
        }
        return agentContext.getRequestId();
    }

    private void appendGeneratedFiles(ScriptRunnerToolResponse response, ToolArtifactSource artifactSource) {
        if (agentContext == null || response == null || response.getFileInfo() == null) {
            return;
        }
        for (ScriptRunnerToolResponse.FileInfo fileInfo : response.getFileInfo()) {
            if (fileInfo == null) {
                continue;
            }
            File file = File.builder()
                    .fileName(fileInfo.getFileName())
                    .ossUrl(fileInfo.getOssUrl())
                    .domainUrl(fileInfo.getDomainUrl())
                    .fileSize(fileInfo.getFileSize())
                    .description(fileInfo.getFileName())
                    .isInternalFile(false)
                    .build();
            agentContext.registerGeneratedArtifact(artifactSource, file);
        }
    }

    /**
     * 脚本执行结果需要保留运行态与文件引用，便于后续审计与 replay。
     */
    private ToolResultPayload buildSuccessPayload(ScriptRunnerToolResponse response) {
        String displayText = response == null ? "" : response.toDisplayText();
        ScriptRunnerToolOutput structuredOutput = ScriptRunnerToolOutput.builder()
                .skillName(response == null ? null : response.getSkillName())
                .scriptName(response == null ? null : response.getScriptName())
                .runtime(response == null ? null : response.getRuntime())
                .success(response != null && Boolean.TRUE.equals(response.getSuccess()))
                .exitCode(response == null ? null : response.getExitCode())
                .stdout(response == null ? "" : StringUtils.defaultString(response.getStdout()))
                .stderr(response == null ? "" : StringUtils.defaultString(response.getStderr()))
                .summary(response == null ? "" : StringUtils.defaultString(response.getSummary()))
                .fileRefs(ToolFileRefMapper.fromScriptRunnerFileInfo(response == null ? null : response.getFileInfo()))
                .build();
        return ToolResultPayload.structured(displayText, displayText, structuredOutput);
    }

    /**
     * 参数校验或执行失败时，同样返回最小 typed output。
     */
    private ToolResultPayload buildFailurePayload(String message) {
        return ToolResultPayload.failure(
                message,
                message,
                ScriptRunnerToolOutput.builder()
                        .summary(message)
                        .success(Boolean.FALSE)
                        .build(),
                message
        );
    }
}
