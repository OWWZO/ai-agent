package org.wwz.ai.domain.agent.runtime.tool.common.docgen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * checklist_generate 工具契约。
 *
 * 当前类只负责声明 Reactor 到 reactor-tool 的 endpoint 和参数 schema，清单解析、
 * 状态跟踪以及 Markdown/PDF/DOCX 导出由共享的文档生成适配层执行。
 */
public class ChecklistGenerateTool extends AbstractDocGenTool {

    public static final String TOOL_NAME = "checklist_generate";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/checklist_generate";
    }

    @Override
    protected String defaultDescription() {
        return "Generate a status-tracked checklist and export to Markdown, JSON, HTML, PDF, or DOCX. "
                + "Supports grouped/flat items with status, priority, assignee, due date, notes and nested sub-items.";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        // 保持平面 items 与分组 groups 两种输入形态，兼容简单清单和层级化清单。
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("output_path", stringProp("Output file name, e.g. checklist.md / checklist.pdf."));
        properties.put("format", stringProp("markdown | json | html | pdf | docx."));
        properties.put("title", stringProp("Checklist title."));
        properties.put("description", stringProp("Checklist description."));
        properties.put("items", arrayProp("Flat list of items.", objectProp("Checklist item: text, status, priority, assignee, due_date, notes, sub_items.")));
        properties.put("groups", arrayProp("Grouped items: [{name, description, items:[...]}].", objectProp("Checklist group")));
        properties.put("theme", stringProp("Theme for HTML/PDF/DOCX."));
        properties.put("fileName", stringProp("Alias of output_path."));
        return objectSchema(properties, List.of());
    }
}
