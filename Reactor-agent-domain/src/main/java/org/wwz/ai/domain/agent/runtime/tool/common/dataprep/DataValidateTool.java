package org.wwz.ai.domain.agent.runtime.tool.common.dataprep;

import org.wwz.ai.domain.agent.runtime.tool.common.docread.AbstractDocReadTool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DataValidateTool extends AbstractDocReadTool {

    public static final String TOOL_NAME = "data_validate";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/data_validate";
    }

    @Override
    protected String defaultDescription() {
        return "Validate tabular data against schema/rules: types, ranges, required fields, "
                + "regex patterns, uniqueness. Returns validation report (not a transformed table).";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("schema", objectProp("Column schema rules"));
        properties.put("rules", Map.of("type", "array", "description", "Extra validation rules", "items", objectProp("Validation rule")));
        properties.put("data", Map.of("type", "array", "items", objectProp("Row object")));
        properties.put("source_path", stringProp("Input file path under workspace"));
        return objectSchema(properties, List.of());
    }
}
