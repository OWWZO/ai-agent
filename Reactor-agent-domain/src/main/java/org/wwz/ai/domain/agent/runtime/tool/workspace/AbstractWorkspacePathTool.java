package org.wwz.ai.domain.agent.runtime.tool.workspace;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;

import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 工作区路径类工具公共支持（对齐 cchaha File/Glob/Grep 的 cwd 约束）。
 */
public abstract class AbstractWorkspacePathTool implements BaseTool {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final WorkspaceService workspaceService;
    protected final WorkspaceRuntimeOptions workspaceRuntimeOptions;

    protected AgentContext agentContext;

    protected AbstractWorkspacePathTool(WorkspaceService workspaceService,
                                        WorkspaceRuntimeOptions workspaceRuntimeOptions) {
        this.workspaceService = workspaceService;
        this.workspaceRuntimeOptions = workspaceRuntimeOptions;
    }

    public void setAgentContext(AgentContext agentContext) {
        this.agentContext = agentContext;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> requireInputMap(Object input) {
        if (!(input instanceof Map<?, ?> rawMap)) {
            throw new WorkspaceAccessException(getName() + " 参数格式错误，必须传入对象类型参数。");
        }
        return (Map<String, Object>) rawMap;
    }

    protected Path requireWorkspaceRoot() {
        if (agentContext != null && StringUtils.isNotBlank(agentContext.getWorkspaceRoot())) {
            return Path.of(agentContext.getWorkspaceRoot()).toAbsolutePath().normalize();
        }
        String sessionId = agentContext == null ? null : agentContext.getSessionId();
        return workspaceService.resolveAndEnsureRoot(sessionId);
    }

    protected Path requireAllowedPath(Map<String, Object> params) {
        Object pathValue = params.containsKey("path") ? params.get("path") : params.get("file_path");
        if (pathValue == null || String.valueOf(pathValue).isBlank()) {
            throw new WorkspaceAccessException("path is required");
        }
        return workspaceService.resolveAllowedPath(requireWorkspaceRoot(), String.valueOf(pathValue).trim());
    }

    protected Path requireWritablePath(Map<String, Object> params) {
        Object pathValue = params.get("path");
        if (pathValue == null || String.valueOf(pathValue).isBlank()) {
            throw new WorkspaceAccessException("path is required");
        }
        return workspaceService.resolveWritablePath(requireWorkspaceRoot(), String.valueOf(pathValue).trim());
    }

    /**
     * 工具描述尾部追加 cwd，对齐 cchaha env 注入思路。
     */
    /** 成功：结构化 llmData，由中央 serialize_for_llm 输出 JSON。 */
    protected ToolResultPayload okResult(Map<String, Object> fields) {
        return ToolResultPayload.okData(getName(), fields);
    }

    /** 失败：LeAgent 风格 Error 前缀。 */
    protected ToolResultPayload failResult(String message) {
        return ToolResultPayload.failureFrom(message, null);
    }

    protected String withWorkspaceHint(String description) {
        String root;
        try {
            root = requireWorkspaceRoot().toString();
        } catch (Exception e) {
            root = agentContext == null ? null : agentContext.getWorkspaceRoot();
        }
        if (StringUtils.isBlank(root)) {
            return description;
        }
        String hint = description + "\nWorking directory: " + root;
        try {
            var roots = workspaceService.listReadableRoots(Path.of(root));
            if (roots != null && roots.size() > 1) {
                hint += "\nAlso readable skill directories: " + (roots.size() - 1);
            }
        } catch (Exception ignore) {
            // ignore hint enrichment failures
        }
        return hint;
    }

    protected int readInt(Map<String, Object> params, String fieldName, int defaultValue) {
        Object value = params.get(fieldName);
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(String.valueOf(value).trim());
    }

    protected boolean readBoolean(Map<String, Object> params, String fieldName, boolean defaultValue) {
        Object value = params.get(fieldName);
        if (value == null || String.valueOf(value).isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }

    protected String requestId() {
        return agentContext == null ? "unknown" : agentContext.getRequestId();
    }

    protected Pattern buildGlobPattern(String pattern) {
        String normalized = pattern.replace("\\", "/");
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < normalized.length(); index++) {
            char currentChar = normalized.charAt(index);
            if (currentChar == '*') {
                boolean doubleStar = index + 1 < normalized.length() && normalized.charAt(index + 1) == '*';
                if (doubleStar) {
                    boolean followedBySlash = index + 2 < normalized.length() && normalized.charAt(index + 2) == '/';
                    if (followedBySlash) {
                        regex.append("(?:.*/)?");
                        index++;
                    } else {
                        regex.append(".*");
                    }
                    index++;
                } else {
                    regex.append("[^/]*");
                }
            } else if (currentChar == '?') {
                regex.append("[^/]");
            } else if ("\\.[]{}()+-^$|".indexOf(currentChar) >= 0) {
                regex.append('\\').append(currentChar);
            } else {
                regex.append(currentChar);
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
    }

    protected String toRelativePath(Path basePath, Path filePath) {
        return basePath.relativize(filePath).toString().replace('\\', '/');
    }
}
