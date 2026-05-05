package org.wwz.ai.domain.agent.runtime.tool.common.skill;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.dto.skill.SkillToolResult;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
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
                + "调用时只需要传入 skill_name，返回结果中的技能目录可继续配合其他 skill 工具使用。\n"
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
                return "skill_tool 参数格式错误，必须传入对象类型参数。";
            }
            Object skillNameValue = rawInput.get("skill_name");
            String skillName = skillNameValue == null ? "" : String.valueOf(skillNameValue).trim();
            if (skillName.isBlank()) {
                return "skill_name is required";
            }

            SkillDefinition skillDefinition = skillRegistry.getRequiredSkill(skillName);
            SkillToolResult result = SkillToolResult.builder()
                    .name(skillDefinition.getName())
                    .description(skillDefinition.getDescription())
                    .basePath(skillDefinition.getBasePath())
                    .content(skillDefinition.getContent())
                    .availableScripts(skillDefinition.buildScriptSummaries())
                    .build();
            return result.toDisplayText();
        } catch (SkillLoadException e) {
            log.warn("{} skill_tool load failed, input={}",
                    agentContext == null ? "unknown" : agentContext.getRequestId(),
                    input,
                    e);
            return e.getMessage();
        } catch (Exception e) {
            log.error("{} skill_tool execute error, input={}",
                    agentContext == null ? "unknown" : agentContext.getRequestId(),
                    input,
                    e);
            return "skill_tool execute failed";
        }
    }
}
