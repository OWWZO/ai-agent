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
                + "For fill_missing with a literal value, pass fill_value (not value). "
                + "Input via data / source_path / artifact.";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> operationProperties = new LinkedHashMap<>();
        operationProperties.put("type", Map.of(
                "type", "string",
                "enum", List.of(
                        "remove_duplicates",
                        "fill_missing",
                        "drop_missing",
                        "trim_whitespace",
                        "normalize_types"
                ),
                "description", "Cleaning operation type"
        ));
        operationProperties.put("columns", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "description", "Columns to apply the operation to; empty means all columns"
        ));
        operationProperties.put("keep", Map.of(
                "type", "string",
                "enum", List.of("first", "last", "none"),
                "description", "For remove_duplicates: which duplicate to keep",
                "default", "first"
        ));
        operationProperties.put("fill_value", Map.of(
                "description", "For fill_missing with fill_strategy=value: literal value to use; do not use value"
        ));
        operationProperties.put("fill_strategy", Map.of(
                "type", "string",
                "enum", List.of("value", "mean", "median", "mode", "ffill", "bfill"),
                "description", "For fill_missing: strategy; value requires fill_value",
                "default", "value"
        ));
        operationProperties.put("axis", Map.of(
                "type", "string",
                "enum", List.of("rows", "columns"),
                "description", "For drop_missing: drop rows or columns",
                "default", "rows"
        ));
        operationProperties.put("how", Map.of(
                "type", "string",
                "enum", List.of("any", "all"),
                "description", "For drop_missing: drop if any or all values are missing",
                "default", "any"
        ));
        operationProperties.put("thresh", Map.of(
                "type", "integer",
                "description", "For drop_missing: minimum non-null values required"
        ));
        Map<String, Object> typeMapSchema = new LinkedHashMap<>();
        typeMapSchema.put("type", "object");
        typeMapSchema.put("description", "For normalize_types: column to type mapping");
        typeMapSchema.put("properties", new LinkedHashMap<String, Object>());
        typeMapSchema.put("required", List.of());
        typeMapSchema.put("additionalProperties", Map.of(
                "type", "string",
                "enum", List.of("int", "float", "str", "bool", "datetime")
        ));
        operationProperties.put("type_map", typeMapSchema);

        Map<String, Object> operationSchema = new LinkedHashMap<>();
        operationSchema.put("type", "object");
        operationSchema.put("properties", operationProperties);
        operationSchema.put("required", List.of("type"));
        operationSchema.put("additionalProperties", false);
        properties.put("operations", Map.of(
                "type", "array",
                "description", "Cleaning operations to apply in order",
                "items", operationSchema
        ));
        properties.put("data", Map.of("type", "array", "description", "Inline rows", "items", objectProp("Row object")));
        properties.put("source_path", stringProp("Input file path under workspace"));
        properties.put("output_format", stringProp("records | dict"));
        return objectSchema(properties, List.of("operations"));
    }
}
