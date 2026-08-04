package org.wwz.ai.domain.agent.runtime.tool.common.docread;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文本文件读取、编辑、搜索、转换、统计和差异比较工具。
 */
public class TextProcessorTool extends AbstractDocReadTool {

    public static final String TOOL_NAME = "text_processor";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/text_processor";
    }

    @Override
    protected String defaultDescription() {
        return "Text file processor: read/write/search/replace/insert/transform/stats/diff with encoding detection.";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("operation", stringProp(
                "read|write|search|replace|insert|append|prepend|transform|extract|split|join|stats|diff|detect_encoding"));
        properties.put("file_path", stringProp("Text file path under workspace"));
        properties.put("data", stringProp("Content for write/append/insert"));
        properties.put("pattern", stringProp("Regex for search/replace/extract"));
        properties.put("replacement", stringProp("Replacement for replace"));
        properties.put("encoding", stringProp("Optional encoding"));
        properties.put("transform_type", stringProp("For transform: upper|lower|title|trim|wrap|..."));
        return objectSchema(properties, List.of("operation", "file_path"));
    }
}
