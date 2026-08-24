package org.wwz.ai.domain.agent.runtime.prompt;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.tool.ToolCollection;

import java.util.Locale;
import java.util.Set;

/**
 * 按用户意图追加重型领域策略，避免把文档/图表规则常驻到所有 Agent 回合。
 */
public final class IntentGatedPrompt {

    private static final Set<String> DOCUMENT_HINTS = Set.of(
            "pdf", "docx", "word", "report", "whitepaper", "proposal",
            "报告", "文档", "白皮书", "方案书", "导出", "生成文件");
    private static final Set<String> CHART_HINTS = Set.of(
            "chart", "plot", "graph", "visualiz", "bar", "line", "pie", "scatter",
            "图表", "画图", "绘图", "可视化", "柱状图", "折线图", "饼图", "散点图", "趋势图");
    private static final Set<String> CANVAS_HINTS = Set.of(
            "canvas", "genui", "gen ui", "dashboard", "kpi", "webpage", "landing page", "html 页面",
            "网页", "页面", "看板", "仪表盘", "卡片", "交互式", "画布", "落地页");

    private static final String DOCUMENT_POLICY = """
            # 文档生成策略
            - 用户要求 PDF、DOCX、HTML 或 Markdown 交付时，使用 document_generate；优先传完整 markdown content，只有需要精确布局时才使用 blocks。
            - 正式报告骨架：封面（cover）→ 目录（toc，≥4 级标题时）→ 执行摘要 → 分节正文 → 结论/建议 → 附录。
            - 正文用真实标题层级（## / ###，勿跳级）；可枚举内容用 **GFM 表格（≤6 列）**，趋势对比用 ```chart 围栏。
            - GFM 表：表前后空行；表头+`| --- |`+数据行；列数一致；禁止 Tab 伪表。
            - 用户要求可编辑文档时选 docx；要求打印保真或正式交付时选 pdf。
            - 主题可选 professional / corporate / academic / minimal / modern，或 theme_designer 自定义名。
            - 文档生成完成后必须检查 warnings 和 content_stats；图表、图片或字体有告警时不得静默交付，应修正参数后重新生成或明确说明降级结果。
            - 仅在用户要求保存、导出或下载时生成文件；没有明确文件交付需求时直接在对话中回答。
            """;

    private static final String DOCUMENT_CHART_POLICY = """
            # 文档图表策略
            - 需要插入 PDF/DOCX 的柱状图、折线图、饼图、散点图或面积图时，使用 document_generate 的 ```chart 围栏，或 blocks 中的 chart 对象。
            - 文档图表结构固定为 {type:"chart", chart_type:"bar|line|pie|scatter|area|barh", title:"...", categories:["..."], series:[{name:"...", values:[1,2]}]}。
            - 严禁把 ECharts option JSON 传给 document_generate：xAxis、yAxis、series.data 和 title.text 会被当作代码文本，不会渲染为图表。
            - 每张图必须有独立标题；非饼图应在需要时设置 x_label、y_label 和单位。无真实数据时不要生成占位图。
            - 生成后确认 content_stats.charts 大于 0 且 warnings 为空；否则根据告警修复图表数据后重试。
            """;

    private static final String CHART_POLICY = """
            # 图表生成策略
            - 先按交付形态选择路径：需要交互式看板或会话内可视化时使用 GenUI；需要 PDF/DOCX 中的静态图表时使用 document_generate；仅解释数据时直接用文本或表格，不要无故生成图。
            - ECharts option JSON 只适用于支持 ECharts 的交互式 GenUI 图表，绝不能传给 document_generate。
            - 每张生成图必须有独立标题，并选择与问题匹配的类型：趋势用 line/area，分类对比用 bar，组成占比用 pie（不超过 8 类），关系分析用 scatter。
            - 没有真实数据时不要生成占位图；多序列图保留清晰图例，必要时标注坐标轴含义和单位。
            """;

    private static final String CANVAS_POLICY = """
            # Canvas 与 GenUI 策略
            - 普通解释、段落、列表和简单表格默认使用 Markdown；只在用户明确需要交互界面、看板、卡片、网页或页面级交付时生成可视化界面。
            - 图表、KPI、数据表、多卡片布局和结构化 3D 使用 emit_ui_tree；非简单树必须先调用 get_genui_guide，再调用 list_ui_components，最后提交 schemaVersion 1 的 tree。
            - 小范围修改已有 GenUI 时使用 emit_ui_patch，不要重新发送整棵 tree。
            - 完整网页、落地页、打印型 HTML 报告或 GenUI 无法表达的自由布局才使用 canvas_publish(mode=html)；复杂页面先调用 get_html_canvas_guide。
            - canvas_publish 的小型内容可传 inline html；较大页面先写入 workspace，再传 html_path。不要用 canvas_publish 绘制普通图表或 KPI 看板。
            """;

    private IntentGatedPrompt() {
    }

    public static Selection select(String query, ToolCollection tools) {
        if (StringUtils.isBlank(query)) {
            return Selection.NONE;
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        boolean document = hasTool(tools, "document_generate") && containsAny(normalizedQuery, DOCUMENT_HINTS);
        boolean chart = hasChartCapability(tools) && containsAny(normalizedQuery, CHART_HINTS);
        boolean canvas = hasCanvasCapability(tools) && containsAny(normalizedQuery, CANVAS_HINTS);
        if (document && chart) {
            return Selection.DOCUMENT_WITH_CHARTS;
        }
        if (document) {
            return Selection.DOCUMENT;
        }
        if (canvas && chart) {
            return Selection.CANVAS_WITH_CHARTS;
        }
        if (canvas) {
            return Selection.CANVAS;
        }
        return chart ? Selection.CHART : Selection.NONE;
    }

    private static boolean hasTool(ToolCollection tools, String name) {
        return tools != null && tools.getToolMap() != null && tools.getToolMap().containsKey(name);
    }

    private static boolean hasChartCapability(ToolCollection tools) {
        return hasTool(tools, "document_generate") || hasTool(tools, "emit_ui_tree");
    }

    private static boolean hasCanvasCapability(ToolCollection tools) {
        return hasTool(tools, "emit_ui_tree") || hasTool(tools, "canvas_publish");
    }

    private static boolean containsAny(String query, Set<String> hints) {
        return hints.stream().anyMatch(query::contains);
    }

    public enum Selection {
        NONE("none", ""),
        CHART("chart", CHART_POLICY),
        CANVAS("canvas", CANVAS_POLICY),
        CANVAS_WITH_CHARTS("canvas-chart", CANVAS_POLICY + "\n" + CHART_POLICY),
        DOCUMENT("document", DOCUMENT_POLICY),
        DOCUMENT_WITH_CHARTS("document-chart", DOCUMENT_POLICY + "\n" + CHART_POLICY + "\n" + DOCUMENT_CHART_POLICY);

        private final String cacheKey;
        private final String policy;

        Selection(String cacheKey, String policy) {
            this.cacheKey = cacheKey;
            this.policy = policy;
        }

        public String appendTo(String systemPrompt) {
            if (policy.isEmpty()) {
                return systemPrompt;
            }
            return systemPrompt.trim() + "\n\n" + policy;
        }

        public String getCacheKey() {
            return cacheKey;
        }
    }
}
