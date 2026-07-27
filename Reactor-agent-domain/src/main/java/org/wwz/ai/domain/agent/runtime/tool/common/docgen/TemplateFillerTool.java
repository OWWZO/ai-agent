package org.wwz.ai.domain.agent.runtime.tool.common.docgen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * template_filler — Jinja2 template fill (LeAgent aligned).
 */
public class TemplateFillerTool extends AbstractDocGenTool {

    public static final String TOOL_NAME = "template_filler";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/template_filler";
    }

    @Override
    protected String defaultDescription() {
        return "Fill templates using Jinja2 with variable substitution, conditionals, loops and filters. "
                + "template_source: string | file | url.";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("template_source", stringProp("Source type: string | file | url. Default string."));
        properties.put("template_string", stringProp("Template content when source=string."));
        properties.put("template_path", stringProp("Template file path when source=file."));
        properties.put("template_url", stringProp("Template URL when source=url."));
        properties.put("variables", Map.of("type", "object", "description", "Template variables object."));
        properties.put("output_path", stringProp("Optional output file name."));
        properties.put("output_format", stringProp("text | html | json | yaml | markdown."));
        properties.put("strict_mode", boolProp("Raise on undefined variables. Default false."));
        properties.put("fileName", stringProp("Alias of output_path."));
        return objectSchema(properties, List.of());
    }
}
