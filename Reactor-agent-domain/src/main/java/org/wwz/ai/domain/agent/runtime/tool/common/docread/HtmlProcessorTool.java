package org.wwz.ai.domain.agent.runtime.tool.common.docread;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HtmlProcessorTool extends AbstractDocReadTool {

    public static final String TOOL_NAME = "html_processor";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/html_processor";
    }

    @Override
    protected String defaultDescription() {
        return "HTML processor: operation=read|links|tables|metadata|convert. "
                + "Extract text/links/tables/meta or convert to markdown/plain_text.";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("operation", stringProp("read | links | tables | metadata | convert"));
        properties.put("file_path", stringProp("HTML file path under workspace"));
        properties.put("output_path", stringProp("Output path for convert"));
        properties.put("format", stringProp("For convert: markdown | plain_text"));
        return objectSchema(properties, List.of("operation", "file_path"));
    }
}
