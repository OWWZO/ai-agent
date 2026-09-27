package org.wwz.ai.domain.agent.runtime.tool.common.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;
import org.wwz.ai.domain.agent.runtime.tool.BaseTool;
import org.wwz.ai.domain.agent.runtime.tool.ToolResultPayload;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillCatalog;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillDescriptor;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPromptIndexBuilder;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillQuery;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillSearchHit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按关键词检索 skill metadata，不返回正文。
 */
@Slf4j
@RequiredArgsConstructor
public class SkillsSearchTool implements BaseTool {

    public static final String NAME = "skills_search";
    public static final String DESCRIPTION =
            "按关键词搜索可用 skill，只返回名称、短描述和分类。找到候选后用 skill_view 加载完整 SKILL.md。";

    private final SkillCatalog skillCatalog;

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
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("type", "string");
        query.put("description", "搜索关键词，例如：SQL 销售趋势分析");

        Map<String, Object> category = new LinkedHashMap<>();
        category.put("type", "string");
        category.put("description", "可选分类，例如 data-analysis");

        Map<String, Object> limit = new LinkedHashMap<>();
        limit.put("type", "integer");
        limit.put("description", "返回条数，默认 10，最大 25");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", query);
        properties.put("category", category);
        properties.put("limit", limit);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", Collections.singletonList("query"));
        return parameters;
    }

    @Override
    public Object execute(Object input) {
        try {
            if (!(input instanceof Map<?, ?> rawInput)) {
                return ToolResultPayload.failureFrom("skills_search 参数格式错误，必须传入对象类型参数。", null);
            }
            String text = readString(rawInput, "query");
            if (text == null || text.isBlank()) {
                text = readString(rawInput, "text");
            }
            SkillQuery query = SkillQuery.builder()
                    .text(text)
                    .category(readString(rawInput, "category"))
                    .limit(readInt(rawInput.get("limit")))
                    .disabledNames(disabledNames())
                    .tags(new LinkedHashSet<>())
                    .build();
            List<SkillSearchHit> hits = skillCatalog.search(query);
            List<Map<String, Object>> items = new ArrayList<>();
            for (SkillSearchHit hit : hits) {
                SkillDescriptor descriptor = hit.descriptor();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", descriptor.getRef() == null ? descriptor.getName() : descriptor.getRef().id());
                row.put("name", descriptor.getName());
                row.put("description", SkillPromptIndexBuilder.truncate(
                        descriptor.getDescription(), SkillPromptIndexBuilder.SEARCH_DESCRIPTION_CHARS));
                row.put("category", descriptor.getCategory());
                row.put("tags", descriptor.getTags() == null ? List.of() : List.copyOf(descriptor.getTags()));
                row.put("score", hit.score());
                items.add(row);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tool", NAME);
            data.put("ok", Boolean.TRUE);
            data.put("items", items);
            data.put("total", items.size());
            data.put("hint", "Use skill_view(name) to load full SKILL.md");
            return ToolResultPayload.fromData(data);
        } catch (Exception e) {
            log.error("{} skills_search execute error, input={}",
                    agentContext == null ? "unknown" : agentContext.getRequestId(),
                    input,
                    e);
            return ToolResultPayload.failureFrom("skills_search execute failed", null);
        }
    }

    private Set<String> disabledNames() {
        if (agentContext == null || agentContext.getDisabledSkillNames() == null) {
            return Set.of();
        }
        return agentContext.getDisabledSkillNames();
    }

    private static String readString(Map<?, ?> rawInput, String key) {
        Object value = rawInput.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    private static Integer readInt(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
