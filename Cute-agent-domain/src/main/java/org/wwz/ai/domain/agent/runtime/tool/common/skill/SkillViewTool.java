package org.wwz.ai.domain.agent.runtime.tool.common.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillCatalog;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillDescriptor;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillDocument;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillFile;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillFileDescriptor;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillFileNotFoundException;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillLoadException;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillLoader;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPromptIndexBuilder;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRef;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillRuntimeLayout;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillScriptDefinition;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillScriptDiscoverer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 加载指定 skill 的完整 SKILL.md 或其附属文件。
 */
@Slf4j
@RequiredArgsConstructor
public class SkillViewTool implements BaseTool {

    public static final String NAME = "skill_view";
    public static final String DESCRIPTION =
            "加载指定 skill 的完整 SKILL.md 内容或其附属文件。如果不知道 skill 名称，先使用 skills_search。";

    private final SkillCatalog skillCatalog;
    private final SkillLoader skillLoader;
    private final SkillRuntimeLayout skillRuntimeLayout;
    private final SkillScriptDiscoverer skillScriptDiscoverer;

    private AgentContext agentContext;

    public void setAgentContext(AgentContext agentContext) {
        this.agentContext = agentContext;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public Map<String, Object> toParams() {
        Map<String, Object> name = new LinkedHashMap<>();
        name.put("type", "string");
        name.put("description", "要加载的 skill 名称或限定 id，例如 sql-analysis");

        Map<String, Object> filePath = new LinkedHashMap<>();
        filePath.put("type", "string");
        filePath.put("description", "可选。skill 根目录内的相对路径，例如 references/metrics.md");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("name", name);
        properties.put("file_path", filePath);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", Collections.singletonList("name"));
        return parameters;
    }

    @Override
    public Object execute(Object input) {
        try {
            if (!(input instanceof Map<?, ?> rawInput)) {
                return ToolResultPayload.failureFrom("skill_view 参数格式错误，必须传入对象类型参数。", null);
            }
            String skillName = readName(rawInput);
            if (skillName.isBlank()) {
                return ToolResultPayload.failureFrom("name is required", null);
            }
            if (isDisabled(skillName)) {
                return ToolResultPayload.failureFrom(
                        "skill 「" + skillName + "」已在本会话关闭，请在能力面板中重新启用。", null);
            }
            SkillRef ref = skillCatalog.resolve(skillName);
            String filePath = readString(rawInput, "file_path");
            if (filePath != null && !filePath.isBlank()) {
                return loadLinkedFile(ref, filePath.trim());
            }
            return loadDocument(ref);
        } catch (SkillFileNotFoundException e) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("error", e.getMessage());
            detail.put("available_files", e.getAvailableFiles());
            detail.put("hint", "Use one of the available file paths listed above");
            return ToolResultPayload.failureFrom(e.getMessage(), detail);
        } catch (SkillLoadException e) {
            log.warn("{} skill_view load failed, input={}", requestId(), input, e);
            return ToolResultPayload.failureFrom(e.getMessage(), null);
        } catch (Exception e) {
            log.error("{} skill_view execute error, input={}", requestId(), input, e);
            return ToolResultPayload.failureFrom("skill_view execute failed", null);
        }
    }

    public String promptIndex() {
        Set<String> disabled = agentContext == null || agentContext.getDisabledSkillNames() == null
                ? Set.of()
                : agentContext.getDisabledSkillNames();
        return SkillPromptIndexBuilder.build(skillCatalog.list(), disabled);
    }

    private Object loadDocument(SkillRef ref) {
        SkillDocument document = skillLoader.load(ref);
        SkillDescriptor descriptor = document.getDescriptor();
        String skillName = descriptor.getName();
        String skillDir = skillRuntimeLayout == null
                ? "skills/" + skillName
                : skillRuntimeLayout.dirOf(skillName);
        String content = document.getContent() == null ? "" : document.getContent();
        if (skillRuntimeLayout != null) {
            content = skillRuntimeLayout.render(skillName, content);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tool", NAME);
        data.put("ok", Boolean.TRUE);
        data.put("name", skillName);
        data.put("id", descriptor.getRef() == null ? null : descriptor.getRef().id());
        data.put("description", descriptor.getDescription());
        data.put("skillDir", skillDir);
        data.put("linkedFiles", toLinkedFileRows(document.getLinkedFiles()));
        data.put("availableScripts", buildScriptSummaries(descriptor, skillDir));
        data.put("content", content);
        return ToolResultPayload.fromData(data);
    }

    private Object loadLinkedFile(SkillRef ref, String filePath) {
        SkillFile file = skillLoader.loadFile(ref, filePath);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tool", NAME);
        data.put("ok", Boolean.TRUE);
        data.put("name", file.getName());
        data.put("file", file.getPath());
        data.put("content", file.getContent());
        if (file.isBinary()) {
            data.put("is_binary", Boolean.TRUE);
        }
        return ToolResultPayload.fromData(data);
    }

    private List<Map<String, Object>> toLinkedFileRows(List<SkillFileDescriptor> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SkillFileDescriptor file : files) {
            if (file == null || file.path() == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("path", file.path());
            row.put("kind", file.kind());
            rows.add(row);
        }
        return rows;
    }

    private List<String> buildScriptSummaries(SkillDescriptor descriptor, String skillDir) {
        if (skillScriptDiscoverer == null || descriptor.getBasePath() == null) {
            return List.of();
        }
        Map<String, SkillScriptDefinition> scripts = skillScriptDiscoverer.discover(descriptor.getBasePath());
        if (scripts == null || scripts.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        String python = skillRuntimeLayout == null ? "python" : skillRuntimeLayout.getPython();
        String dir = skillDir == null ? "skills" : skillDir;
        for (SkillScriptDefinition script : scripts.values()) {
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

    static String readName(Map<?, ?> rawInput) {
        Object name = rawInput.get("name");
        return name == null ? "" : String.valueOf(name).trim();
    }

    private static String readString(Map<?, ?> rawInput, String key) {
        Object value = rawInput.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private boolean isDisabled(String skillName) {
        return agentContext != null
                && agentContext.getDisabledSkillNames() != null
                && agentContext.getDisabledSkillNames().contains(skillName);
    }

    private String requestId() {
        return agentContext == null ? "unknown" : agentContext.getRequestId();
    }
}
