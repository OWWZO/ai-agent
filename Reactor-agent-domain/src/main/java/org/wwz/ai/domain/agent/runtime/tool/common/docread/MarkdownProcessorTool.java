package org.wwz.ai.domain.agent.runtime.tool.common.docread;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MarkdownProcessorTool extends AbstractDocReadTool {

    public static final String TOOL_NAME = "markdown_processor";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/markdown_processor";
    }

    @Override
    protected String defaultDescription() {
        return "Markdown processor: read/write/create/append/section edit/merge/convert/format/template. "
                + "Use for structured markdown authoring and TOC/link extraction.";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("operation", stringProp(
                "read|write|create|append|prepend|insert_section|replace_section|merge|convert|format|template"));
        properties.put("file_path", stringProp("Markdown path (read/write target)"));
        properties.put("content", stringProp("Markdown content for write/append/create"));
        properties.put("output_path", stringProp("Optional output path"));
        properties.put("heading", stringProp("Section heading for insert/replace"));
        properties.put("title", stringProp("Title for create/template"));
        properties.put("format", stringProp("convert target: html | plain_text"));
        return objectSchema(properties, List.of("operation"));
    }
}
