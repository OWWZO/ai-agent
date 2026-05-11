package org.wwz.ai.domain.agent.runtime.tool.common.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillLoadException;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeOptions;

import java.nio.file.Path;
import java.util.Map;

/**
 * skill 文件类工具的公共支持。
 */
public abstract class AbstractSkillPathTool implements BaseTool {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final SkillRegistry skillRegistry;

    protected final SkillRuntimeOptions skillRuntimeOptions;

    protected AgentContext agentContext;

    protected AbstractSkillPathTool(SkillRegistry skillRegistry, SkillRuntimeOptions skillRuntimeOptions) {
        this.skillRegistry = skillRegistry;
        this.skillRuntimeOptions = skillRuntimeOptions;
    }

    public void setAgentContext(AgentContext agentContext) {
        this.agentContext = agentContext;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> requireInputMap(Object input) {
        if (!(input instanceof Map<?, ?> rawMap)) {
            throw new SkillLoadException(getName() + " 参数格式错误，必须传入对象类型参数。");
        }
        return (Map<String, Object>) rawMap;
    }

    protected Path requireAllowedPath(Map<String, Object> params) {
        Object pathValue = params.get("path");
        if (pathValue == null || String.valueOf(pathValue).isBlank()) {
            throw new SkillLoadException("path is required");
        }
        return skillRegistry.assertPathAllowed(Path.of(String.valueOf(pathValue).trim()));
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
}
