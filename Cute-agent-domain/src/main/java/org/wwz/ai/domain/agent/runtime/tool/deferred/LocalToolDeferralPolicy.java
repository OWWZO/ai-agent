package org.wwz.ai.domain.agent.runtime.tool.deferred;

import java.util.Set;

/**
 * 默认的本地工具延迟候选名单。
 * 只有实际装配进当前 ToolCollection 的工具才会被移动到 catalog。
 */
public final class LocalToolDeferralPolicy {

    public static final Set<String> DEFERRED_LOCAL_TOOL_NAMES = Set.of(
            "chart_generator",
            "checklist_generate",
            "document_generate",
            "document_template",
            "excel_generator",
            "slides_generate",
            "template_filler",
            "theme_designer",
            "citation_extractor",
            "csv_processor",
            "excel_reader",
            "html_processor",
            "markdown_processor",
            "pdf_reader",
            "pdf_structure",
            "text_processor",
            "data_aggregate",
            "data_clean",
            "data_merge",
            "data_transform",
            "data_validate",
            "sql_query",
            "deep_search",
            "data_analysis"
    );

    private LocalToolDeferralPolicy() {
    }

    public static boolean isCandidate(String name) {
        return name != null && DEFERRED_LOCAL_TOOL_NAMES.contains(name);
    }
}
