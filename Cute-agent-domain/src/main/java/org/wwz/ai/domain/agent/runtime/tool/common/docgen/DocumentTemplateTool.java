package org.wwz.ai.domain.agent.runtime.tool.common.docgen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * document_template 工具契约。
 *
 * 模板的保存、变量校验、预览和最终渲染都在文档生成服务中完成；domain 只负责
 * 向 Agent 发布统一的 action、变量和输出格式描述。
 */
public class DocumentTemplateTool extends AbstractDocGenTool {

    public static final String TOOL_NAME = "document_template";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/document_template";
    }

    @Override
    protected String defaultDescription() {
        return "Manage reusable document/deck templates. action=save|list|get|delete|preview|generate. "
                + "save stores markdown content or slides with Jinja2 {{variables}}; "
                + "generate instantiates with values and renders PDF/DOCX/HTML/MD or PPTX.";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        // 文档和幻灯片共用一份 schema，通过 kind 区分 content 与 slides 的解释方式。
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("action", stringProp("save | list | get | delete | preview | generate"));
        properties.put("name", stringProp("Template name (required except list)"));
        properties.put("kind", stringProp("document | deck"));
        properties.put("description", stringProp("One-line description for list"));
        properties.put("content", stringProp("Document markdown body with Jinja2 placeholders"));
        properties.put("slides", arrayProp("Deck slides list (objects with Jinja2 text fields)", objectProp("Deck slide")));
        properties.put("theme", stringProp("Theme name (built-in or theme_designer)"));
        properties.put("variables", arrayProp("Declared variables [{name,description,default,required}]", objectProp("Variable declaration")));
        properties.put("defaults", objectProp("Extra generate defaults (toc, cover, header, ...)"));
        properties.put("values", objectProp("Variable values for preview/generate"));
        properties.put("output_path", stringProp("generate: output file name e.g. report.pdf / deck.pptx"));
        properties.put("format", stringProp("generate document: pdf|docx|html|markdown"));
        properties.put("overwrite", boolProp("save: replace existing template, default true"));
        return objectSchema(properties, List.of("action"));
    }
}
