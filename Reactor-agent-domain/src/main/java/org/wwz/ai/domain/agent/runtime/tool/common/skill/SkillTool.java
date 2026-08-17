package org.wwz.ai.domain.agent.runtime.tool.common.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillDefinition;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillLoadException;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeLayout;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillScriptDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按需读取 skill 手册（注册表缓存）。路径契约：{@code skills/&lt;name&gt;}。
 * 增删改文件请用 workspace_*（虚拟 skills/）；执行脚本请用 bash（沙箱物化 + sync-back）。
 */
@Slf4j
@RequiredArgsConstructor
public class SkillTool implements BaseTool {

    private final SkillRegistry skillRegistry;
    private final SkillRuntimeLayout skillRuntimeLayout;

    private AgentContext agentContext;

    public void setAgentContext(AgentContext agentContext) {
        this.agentContext = agentContext;
    }

    @Override
    public String getName() {
        return "skill_tool";
    }

    @Override
    public String getDescription() {
        return "按技能名加载 SKILL.md 正文与脚本摘要（来自注册表缓存；本轮创建的 skill 可能需下轮才出现在列表）。\n"
                + "路径契约：skills/<name>/... — workspace_read/write/edit/list/glob/grep 可直接操作（映射到全局 skill 库）。\n"
                + "执行脚本：bash 工具会在沙箱 cwd 物化 skills/，命令示例 python skills/<name>/scripts/xxx.py；"
                + "沙箱内对 skills/** 的修改会回写全局库（注册表本轮不刷新）。\n"
                + "新建 skill：可在沙箱跑 Skill Creator 脚本，或 workspace_write skills/<new>/SKILL.md 等。\n"
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
            if (agentContext != null
                    && agentContext.getDisabledSkillNames() != null
                    && agentContext.getDisabledSkillNames().contains(skillName)) {
                return ToolResultPayload.failureFrom(
                        "skill 「" + skillName + "」已在本会话关闭，请在能力面板中重新启用。", null);
            }

            SkillDefinition skillDefinition = skillRegistry.getRequiredSkill(skillName);
            String skillDir = skillRuntimeLayout == null
                    ? "skills/" + skillName
                    : skillRuntimeLayout.dirOf(skillName);
            String content = skillDefinition.getContent() == null ? "" : skillDefinition.getContent();
            if (skillRuntimeLayout != null) {
                content = skillRuntimeLayout.render(skillName, content);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tool", "skill_tool");
            data.put("ok", Boolean.TRUE);
            data.put("name", skillDefinition.getName());
            data.put("description", skillDefinition.getDescription());
            data.put("skillDir", skillDir);
            data.put("availableScripts", buildScriptSummaries(skillDefinition, skillDir));
            data.put("content", content);
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

    private List<String> buildScriptSummaries(SkillDefinition skillDefinition, String skillDir) {
        if (skillDefinition.getScripts() == null || skillDefinition.getScripts().isEmpty()) {
            return skillDefinition.buildScriptSummaries();
        }
        List<String> lines = new ArrayList<>();
        String python = skillRuntimeLayout == null ? "python" : skillRuntimeLayout.getPython();
        String dir = skillDir == null ? "skills" : skillDir;
        for (SkillScriptDefinition script : skillDefinition.getScripts().values()) {
            if (script == null) {
                continue;
            }
            String rel = script.getRelativePath() == null ? script.getScriptName() : script.getRelativePath();
            String runtime = script.getRuntime() == null ? "python" : script.getRuntime();
            String example;
            if ("node".equalsIgnoreCase(runtime)) {
                example = "node " + dir + "/" + rel;
            } else if ("shell".equalsIgnoreCase(runtime) || "bash".equalsIgnoreCase(runtime)) {
                example = "bash " + dir + "/" + rel;
            } else if ("powershell".equalsIgnoreCase(runtime)) {
                example = "powershell -File " + dir + "/" + rel;
            } else {
                example = python + " " + dir + "/" + rel;
            }
            String desc = script.getDescription() == null || script.getDescription().isBlank()
                    ? "未提供说明" : script.getDescription();
            lines.add(String.format("- %s | runtime=%s | path=%s | run: %s | %s",
                    script.getScriptName(), runtime, rel, example, desc));
        }
        return lines;
    }
}
