package org.wwz.ai.domain.agent.runtime.tool.common.skill;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.skill.SkillToolResult;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillDefinition;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillLoadException;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按需读取 skill 正文、目录与脚本摘要的工具。
 */
@Slf4j
@Data
@RequiredArgsConstructor
public class SkillTool implements BaseTool {

    private final SkillRegistry skillRegistry;

    private AgentContext agentContext;

    @Override
    public String getName() {
        return "skill_tool";
    }

    @Override
    public String getDescription() {
        return "这是一个 skill 读取工具，用于按技能名称加载 SKILL.md 正文、技能目录和脚本摘要。\n"
                + "调用时只需要传入 skill_name；返回的 basePath 可用 workspace_read/list/glob/grep 继续浏览（skill 目录已并入可读根）。\n"
                + "如需执行 skill 内脚本，请用 Bash 或 PowerShell，在 basePath 下直接运行（不要再调用 script_runner_tool）。\n"
                + skillRegistry.buildSkillDescription();
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> skillName = new LinkedHashMap<>();
        skillName.put("type", "string");
        skillName.put("description", "要加载的 skill 名称，例如：sql-analysis");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("skill_name", skillName);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", Collections.singletonList("skill_name"));
        return parameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        try {
            if (!(input instanceof Map<?, ?> rawInput)) {
                return ToolResultPayload.failureFrom("skill_tool 参数格式错误，必须传入对象类型参数。", null);
            }
            Object skillNameValue = rawInput.get("skill_name");
            String skillName = skillNameValue == null ? "" : String.valueOf(skillNameValue).trim();
            if (skillName.isBlank()) {
                return ToolResultPayload.failureFrom("skill_name is required", null);
            }

            // 工具只读取已注册快照，不在调用时重新扫描文件系统，保证正文和脚本摘要来自同一版本。
            SkillDefinition skillDefinition = skillRegistry.getRequiredSkill(skillName);
            SkillToolResult result = SkillToolResult.builder()
                    .name(skillDefinition.getName())
                    .description(skillDefinition.getDescription())
                    .basePath(skillDefinition.getBasePath())
                    .content(skillDefinition.getContent())
                    .availableScripts(skillDefinition.buildScriptSummaries())
                    .build();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tool", "skill_tool");
            data.put("ok", Boolean.TRUE);
            data.put("name", result.getName());
            data.put("description", result.getDescription());
            data.put("basePath", result.getBasePath() == null ? null : result.getBasePath().toString());
            data.put("availableScripts", result.getAvailableScripts());
            data.put("content", result.getContent());
            return ToolResultPayload.fromData(data);
        } catch (SkillLoadException e) {
            log.warn("{} skill_tool load failed, input={}",
                    agentContext == null ? "unknown" : agentContext.getRequestId(),
                    input,
                    e);
            return ToolResultPayload.failureFrom(e.getMessage(), null);
        } catch (Exception e) {
            log.error("{} skill_tool execute error, input={}",
                    agentContext == null ? "unknown" : agentContext.getRequestId(),
                    input,
                    e);
            return ToolResultPayload.failureFrom("skill_tool execute failed", null);
        }
    }
}
