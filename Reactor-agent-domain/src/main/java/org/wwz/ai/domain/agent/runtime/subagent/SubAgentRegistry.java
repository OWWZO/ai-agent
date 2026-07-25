package org.wwz.ai.domain.agent.runtime.subagent;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 内置同步子 Agent 注册表（对标 cc-haha builtInAgents）。
 */
@Component
public class SubAgentRegistry {

    public static final String TYPE_EXPLORE = "Explore";
    public static final String TYPE_GENERAL_PURPOSE = "general-purpose";

    private final Map<String, SubAgentDefinition> agents = new LinkedHashMap<>();

    public SubAgentRegistry() {
        register(buildExplore());
        register(buildGeneralPurpose());
    }

    public void register(SubAgentDefinition definition) {
        if (definition == null || definition.getAgentType() == null || definition.getAgentType().isBlank()) {
            throw new IllegalArgumentException("SubAgentDefinition.agentType 不能为空");
        }
        agents.put(definition.getAgentType(), definition);
    }

    public Optional<SubAgentDefinition> find(String agentType) {
        if (agentType == null || agentType.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(agents.get(agentType));
    }

    public SubAgentDefinition require(String agentType) {
        return find(agentType).orElseThrow(() -> new IllegalArgumentException(
                "未知 subagent_type: " + agentType + "。可用类型: " + String.join(", ", agents.keySet())));
    }

    public SubAgentDefinition resolveOrDefault(String agentType) {
        if (agentType == null || agentType.isBlank()) {
            return require(TYPE_GENERAL_PURPOSE);
        }
        return require(agentType);
    }

    public Collection<SubAgentDefinition> list() {
        return agents.values();
    }

    private static SubAgentDefinition buildExplore() {
        return SubAgentDefinition.builder()
                .agentType(TYPE_EXPLORE)
                .whenToUse("快速只读探索代码库/工作区：按模式找文件、搜索关键词、回答结构问题。不修改任何文件。")
                .systemPrompt("""
                        你是只读探索子代理。严格禁止修改文件或改变系统状态。
                        你的职责仅限于：搜索、读取、分析现有内容，并输出精简报告。
                        规则：
                        - 只使用只读工具（读文件、列表、glob、grep、搜索、抓取网页）。
                        - 不要创建/编辑/删除文件，不要运行会改状态的命令。
                        - 尽可能并行调用只读工具以加快速度。
                        - 完成后用简洁报告回复：关键路径、发现结论、不确定点。不要寒暄。
                        """)
                .allowedTools(Set.of(
                        "workspace_read",
                        "workspace_list",
                        "workspace_glob",
                        "workspace_grep",
                        "deep_search",
                        "web_fetch",
                        "skill_tool"
                ))
                .disallowedTools(Set.of(
                        "workspace_write",
                        "workspace_edit",
                        "file_tool",
                        "code_interpreter",
                        "report_tool",
                        "script_runner_tool",
                        "image_generation",
                        "data_analysis",
                        "multimodalagent_tool"
                ))
                .maxSteps(8)
                .build();
    }

    private static SubAgentDefinition buildGeneralPurpose() {
        return SubAgentDefinition.builder()
                .agentType(TYPE_GENERAL_PURPOSE)
                .whenToUse("通用多步骤研究与执行：复杂搜索、跨文件分析、需要较全工具的子任务。不确定类型时用它。")
                .systemPrompt("""
                        你是通用子代理。根据任务使用可用工具完整完成工作，不要半途而废，也不要过度发挥。
                        完成后用简洁报告回复：做了什么、关键发现、产物路径（如有）。调用方会转达给用户，只写要点。
                        规则：
                        - 优先编辑已有文件，非必要不新建文件。
                        - 不要主动写文档/README，除非任务明确要求。
                        - 不要再派发其他子代理。
                        """)
                .allowedTools(Set.of("*"))
                .disallowedTools(Set.of())
                .maxSteps(15)
                .build();
    }
}
