package org.wwz.ai.domain.agent.runtime.tool.common.shell;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpRequest;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillVirtualPaths;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspacePaths;
import org.wwz.ai.domain.agent.runtime.tool.workspace.WorkspaceService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * bash 工具：只负责发请求到 reactor-tool 沙箱（与 code_execution 同形态）。
 *
 * <p>本机不执行命令。skill 物化 / 执行 / skills/** 回写均在 reactor-tool 完成。
 */
@Slf4j
@RequiredArgsConstructor
public class BashTool implements BaseTool {

    public static final String TOOL_NAME = "bash";

    private final SkillRuntimeOptions skillRuntimeOptions;
    private final SkillVirtualPaths skillVirtualPaths;

    private AgentContext agentContext;

    public void setAgentContext(AgentContext agentContext) {
        this.agentContext = agentContext;
    }

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    public String getDescription() {
        int defaultSec = skillRuntimeOptions == null ? 120 : skillRuntimeOptions.getBashTimeoutSec();
        int maxSec = skillRuntimeOptions == null ? 600 : skillRuntimeOptions.getBashMaxTimeoutSec();
        return "在远端沙箱执行 shell（reactor-tool /v1/tool/bash；"
                + "与 code_execution 相同 CODE_SANDBOX_BACKEND：local 或 e2b）。"
                + "命令含 skills/ 时才会把全局 skill 库物化到沙箱 skills/<name>/（此后本会话 bash 保持 skill 沙箱）；"
                + "命令示例：python skills/<name>/scripts/xxx.py。"
                + "沙箱内对 skills/** 的修改会回写全局 skill 库（注册表本轮不刷新）。"
                + "路径：无前缀=会话工作区相对路径，skills/=技能库；不要用宿主绝对路径。"
                + "默认超时 " + defaultSec + "s，上限 " + maxSec + "s。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("type", "string");
        command.put("description", "要执行的 shell 命令（沙箱 cwd 为会话工作区）");

        Map<String, Object> timeoutSec = new LinkedHashMap<>();
        timeoutSec.put("type", "integer");
        timeoutSec.put("description", "超时秒数，默认 "
                + (skillRuntimeOptions == null ? 120 : skillRuntimeOptions.getBashTimeoutSec())
                + "，上限 "
                + (skillRuntimeOptions == null ? 600 : skillRuntimeOptions.getBashMaxTimeoutSec()));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("command", command);
        properties.put("timeout_sec", timeoutSec);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("command"));
        return parameters;
    }

    @Override
    public Object execute(Object input) {
        try {
            if (!(input instanceof Map<?, ?> raw)) {
                return ToolResultPayload.failureFrom("bash 参数必须是对象", null);
            }
            String command = raw.get("command") == null ? "" : String.valueOf(raw.get("command")).trim();
            if (command.isBlank()) {
                return ToolResultPayload.failureFrom("command 不能为空", null);
            }
            if (agentContext == null || agentContext.getRuntimeDependencies() == null) {
                return ToolResultPayload.failureFrom("AgentContext 未就绪", null);
            }

            ReactorConfig config = agentContext.getRuntimeDependencies().requireReactorConfig();
            if (StringUtils.isBlank(config.getCodeInterpreterUrl())) {
                return ToolResultPayload.failureFrom("codeInterpreterUrl 未配置，无法调用沙箱 bash", null);
            }

            String sessionId = StringUtils.defaultIfBlank(agentContext.getSessionId(), agentContext.getRequestId());
            String workspaceRoot = WorkspacePaths.skillOutputSessionRoot(sessionId).toString();
            String skillLibraryRoot = null;
            if (skillVirtualPaths != null && skillVirtualPaths.isEnabled()) {
                try {
                    skillLibraryRoot = skillVirtualPaths.requireLibraryRoot().toString();
                } catch (Exception e) {
                    log.warn("{} skill library root unavailable: {}", agentContext.getRequestId(), e.getMessage());
                }
            }

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("requestId", sessionId);
            request.put("command", command);
            request.put("workspaceRoot", workspaceRoot);
            if (skillLibraryRoot != null) {
                request.put("skillLibraryRoot", skillLibraryRoot);
            }
            request.put("disabledSkillNames", disabledSkillNames());
            request.put("timeoutSeconds", resolveTimeoutSec(raw.get("timeout_sec")));
            request.put("maxOutputChars",
                    skillRuntimeOptions == null ? 64000 : skillRuntimeOptions.getBashOutputMaxChars());

            String body = agentContext.getRuntimeDependencies().requireRemoteHttpPort().execute(
                    RemoteHttpRequest.builder()
                            .method("POST")
                            .url(config.getCodeInterpreterUrl() + "/v1/tool/bash")
                            .headers(Map.of("Content-Type", "application/json"))
                            .body(JSON.toJSONString(request))
                            .connectTimeoutSeconds(60L)
                            .readTimeoutSeconds(660L)
                            .writeTimeoutSeconds(60L)
                            .callTimeoutSeconds(660L)
                            .build());

            JSONObject response = JSON.parseObject(body);
            if (response == null) {
                return ToolResultPayload.failureFrom("bash 沙箱返回空响应", null);
            }
            if (StringUtils.isNotBlank(response.getString("message"))
                    && response.get("exitCode") == null
                    && response.get("exit_code") == null) {
                // FastAPI 错误体 {"message": "..."}
                return ToolResultPayload.failureFrom(
                        WorkspaceService.redactHostPaths(response.getString("message")), null);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tool", TOOL_NAME);
            data.put("ok", Boolean.TRUE);
            data.put("exit_code", firstNonNull(response.get("exitCode"), response.get("exit_code")));
            data.put("stdout", WorkspaceService.redactHostPaths(
                    StringUtils.defaultString(response.getString("stdout"))));
            data.put("stderr", WorkspaceService.redactHostPaths(
                    StringUtils.defaultString(response.getString("stderr"))));
            data.put("truncated", Boolean.TRUE.equals(response.getBoolean("truncated")));
            data.put("timed_out", Boolean.TRUE.equals(
                    firstNonNull(response.getBoolean("timedOut"), response.getBoolean("timed_out"))));
            data.put("duration_ms", firstNonNull(response.get("durationMs"), response.get("duration_ms"), 0));
            data.put("cwd", ".");
            data.put("skills_materialized", firstNonNull(
                    response.get("skillsMaterialized"), response.get("skills_materialized"), List.of()));
            data.put("skills_synced_back", firstNonNull(
                    response.get("skillsSyncedBack"), response.get("skills_synced_back"), List.of()));
            return ToolResultPayload.fromData(data);
        } catch (Exception e) {
            log.error("{} bash remote execute error, input={}",
                    agentContext == null ? "unknown" : agentContext.getRequestId(),
                    input,
                    e);
            return ToolResultPayload.failureFrom(
                    "bash 执行失败: " + WorkspaceService.redactHostPaths(e.getMessage()),
                    null);
        }
    }

    private List<String> disabledSkillNames() {
        if (agentContext == null || agentContext.getDisabledSkillNames() == null) {
            return List.of();
        }
        return new ArrayList<>(agentContext.getDisabledSkillNames());
    }

    private int resolveTimeoutSec(Object timeoutSecValue) {
        int defaultSec = skillRuntimeOptions == null ? 120 : Math.max(1, skillRuntimeOptions.getBashTimeoutSec());
        int maxSec = skillRuntimeOptions == null ? 600 : Math.max(defaultSec, skillRuntimeOptions.getBashMaxTimeoutSec());
        if (timeoutSecValue == null) {
            return defaultSec;
        }
        try {
            int requested = Integer.parseInt(String.valueOf(timeoutSecValue).trim());
            if (requested <= 0) {
                return defaultSec;
            }
            return Math.min(requested, maxSec);
        } catch (NumberFormatException e) {
            return defaultSec;
        }
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
