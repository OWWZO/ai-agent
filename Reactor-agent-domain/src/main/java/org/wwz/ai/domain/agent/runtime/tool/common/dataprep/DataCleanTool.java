package org.wwz.ai.domain.agent.runtime.tool.common.dataprep;

import org.wwz.ai.domain.agent.runtime.tool.common.docread.AbstractDocReadTool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表格数据清洗工具，按操作列表执行去重、缺失值处理、空白清理和类型规范化。
 */
public class DataCleanTool extends AbstractDocReadTool {

    public static final String TOOL_NAME = "data_clean";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/data_clean";
    }

    @Override
    protected String defaultDescription() {
        return "Clean tabular data: dedupe, fill/drop missing, trim whitespace, normalize types. "
                + "Pass operations=[{type, columns, ...}]. Input via data / source_path / artifact.";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("operations", Map.of(
                "type", "array",
                "description", "Cleaning ops: remove_duplicates, fill_missing, drop_missing, trim_whitespace, normalize_types, ...",
                "items", objectProp("Cleaning operation")
        ));
        properties.put("data", Map.of("type", "array", "description", "Inline rows", "items", objectProp("Row object")));
        properties.put("source_path", stringProp("Input file path under workspace"));
        properties.put("output_format", stringProp("records | dict"));
        return objectSchema(properties, List.of("operations"));
    }
}
