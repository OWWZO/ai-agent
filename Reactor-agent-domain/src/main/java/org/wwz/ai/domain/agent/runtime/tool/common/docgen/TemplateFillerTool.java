package org.wwz.ai.domain.agent.runtime.tool.common.docgen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * template_filler 工具契约。
 *
 * Reactor 只负责把模板来源、变量和输出格式传递给共享服务；Jinja2 的语法执行、
 * 未定义变量策略以及文件读取边界由 reactor-tool 统一处理。
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
        // 三种 template_source 共享一套参数描述，具体必填关系由服务按 source 校验。
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("template_source", stringProp("Source type: string | file | url. Default string."));
        properties.put("template_string", stringProp("Template content when source=string."));
        properties.put("template_path", stringProp("Template file path when source=file."));
        properties.put("template_url", stringProp("Template URL when source=url."));
        properties.put("variables", objectProp("Template variables object."));
        properties.put("output_path", stringProp("Optional output file name."));
        properties.put("output_format", stringProp("text | html | json | yaml | markdown."));
        properties.put("strict_mode", boolProp("Raise on undefined variables. Default false."));
        properties.put("fileName", stringProp("Alias of output_path."));
        return objectSchema(properties, List.of());
    }
}
