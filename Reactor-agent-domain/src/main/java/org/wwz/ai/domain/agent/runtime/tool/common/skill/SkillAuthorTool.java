package org.wwz.ai.domain.agent.runtime.tool.common.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillLoadException;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillMaterializer;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPackageService;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 创作/优化技能：写入全局 skill 库，并同步 re-materialize 到当前会话沙箱。
 *
 * <p>写全局 → registry refresh → 会话 .skills/ 强制重灌，使 bash 立即执行到新版本。
 */
@Slf4j
@RequiredArgsConstructor
public class SkillAuthorTool implements BaseTool {

    public static final String TOOL_NAME = "skill_author";

    private final SkillPackageService skillPackageService;
    private final SkillMaterializer skillMaterializer;

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
        return "创建或优化**全局** skill（写入 skill 库目录，所有会话可见），并同步到当前会话沙箱 .skills/ 以便 bash 立即执行。\n"
                + "动作 action：\n"
                + "- upsert：创建/覆盖 SKILL.md（参数 skill_name, description?, content）\n"
                + "- write_file：写技能包内文件如 scripts/xxx.py（skill_name, path, content）\n"
                + "- delete_file：删包内文件（skill_name, path；不可删 SKILL.md）\n"
                + "- list_files：列出技能包内相对路径（skill_name）\n"
                + "手册中脚本请用 ${SKILL_DIR}/scripts/... 与 ${PYTHON}；执行用 bash 工具。\n"
                + "注意：这是全局生效的写操作，请确认 skill_name 与内容后再写。";
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("action", Map.of(
                "type", "string",
                "description", "upsert | write_file | delete_file | list_files",
                "enum", List.of("upsert", "write_file", "delete_file", "list_files")
        ));
        properties.put("skill_name", Map.of(
                "type", "string",
                "description", "技能名（全局唯一 refId）"
        ));
        properties.put("description", Map.of(
                "type", "string",
                "description", "upsert 时的技能短描述（写入 frontmatter）"
        ));
        properties.put("content", Map.of(
                "type", "string",
                "description", "upsert 时 SKILL.md 正文；write_file 时文件内容"
        ));
        properties.put("path", Map.of(
                "type", "string",
                "description", "write_file/delete_file 的相对路径，如 scripts/run.py"
        ));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("action", "skill_name"));
        return parameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object execute(Object input) {
        try {
            if (!(input instanceof Map<?, ?> raw)) {
                return ToolResultPayload.failureFrom("skill_author 参数必须是对象", null);
            }
            String action = text(raw.get("action")).toLowerCase();
            String skillName = text(raw.get("skill_name"));
            if (skillName.isBlank()) {
                return ToolResultPayload.failureFrom("skill_name 不能为空", null);
            }
            if (agentContext != null
                    && agentContext.getDisabledSkillNames() != null
                    && agentContext.getDisabledSkillNames().contains(skillName)
                    && !"list_files".equals(action)) {
                return ToolResultPayload.failureFrom(
                        "skill 「" + skillName + "」已在本会话关闭，无法写入；请先在能力面板启用。", null);
            }

            Map<String, Object> result = switch (action) {
                case "upsert" -> skillPackageService.upsertManual(
                        skillName,
                        blankToNull(text(raw.get("description"))),
                        rawText(raw.get("content")));
                case "write_file" -> skillPackageService.writeRelativeFile(
                        skillName,
                        text(raw.get("path")),
                        rawText(raw.get("content")));
                case "delete_file" -> skillPackageService.deleteRelativeFile(
                        skillName,
                        text(raw.get("path")));
                case "list_files" -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", skillName);
                    m.put("files", skillPackageService.listRelativeFiles(skillName));
                    yield m;
                }
                default -> throw new SkillLoadException(
                        "未知 action: " + action + "（支持 upsert|write_file|delete_file|list_files）");
            };

            boolean rematerialized = rematerializeSession(skillName);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tool", TOOL_NAME);
            data.put("ok", Boolean.TRUE);
            data.put("action", action);
            data.put("global", Boolean.TRUE);
            data.put("rematerialized", rematerialized);
            data.putAll(result);
            data.put("hint", rematerialized
                    ? "已写入全局库并同步到本会话 .skills/" + skillName + "，可用 bash 执行脚本"
                    : "已写入全局库；当前无 workspace，新会话 materialize 后可用");
            return ToolResultPayload.fromData(data);
        } catch (SkillLoadException e) {
            log.warn("{} skill_author failed: {}",
                    agentContext == null ? "unknown" : agentContext.getRequestId(), e.getMessage());
            return ToolResultPayload.failureFrom(e.getMessage(), null);
        } catch (Exception e) {
            log.error("{} skill_author error", agentContext == null ? "unknown" : agentContext.getRequestId(), e);
            return ToolResultPayload.failureFrom("skill_author 失败: " + e.getMessage(), null);
        }
    }

    private boolean rematerializeSession(String skillName) {
        if (skillMaterializer == null || agentContext == null
                || agentContext.getWorkspaceRoot() == null
                || agentContext.getWorkspaceRoot().isBlank()) {
            return false;
        }
        try {
            skillMaterializer.rematerialize(
                    Path.of(agentContext.getWorkspaceRoot()),
                    List.of(skillName),
                    agentContext.getDisabledSkillNames());
            return true;
        } catch (Exception e) {
            log.warn("{} rematerialize after skill_author failed skill={}",
                    agentContext.getRequestId(), skillName, e);
            return false;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /** 文件/手册正文不 trim，避免吃掉末尾换行。 */
    private static String rawText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
