package org.wwz.ai.domain.agent.runtime.tool.common.dataprep;

import org.wwz.ai.domain.agent.runtime.tool.common.docread.AbstractDocReadTool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DataAggregateTool extends AbstractDocReadTool {

    public static final String TOOL_NAME = "data_aggregate";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/data_aggregate";
    }

    @Override
    protected String defaultDescription() {
        return "Aggregate tabular data: operation=groupby|pivot|describe|value_counts|rolling. "
                + "Supports sum/avg/count/min/max and pivot tables. "
                + "Input via data (records), source_path, or artifact.";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("operation", stringProp("groupby | pivot | describe | value_counts | rolling"));
        properties.put("data", Map.of("type", "array", "description", "Inline list of row objects", "items", objectProp("Row object")));
        properties.put("source_path", stringProp("CSV/JSON/JSONL/Parquet path under workspace"));
        properties.put("group_by", Map.of("type", "array", "items", Map.of("type", "string"), "description", "Group-by columns"));
        properties.put("aggregations", objectProp("column -> sum|mean|avg|count|min|max|..."));
        properties.put("pivot_index", Map.of("type", "array", "items", Map.of("type", "string")));
        properties.put("pivot_columns", Map.of("type", "array", "items", Map.of("type", "string")));
        properties.put("pivot_values", Map.of("type", "array", "items", Map.of("type", "string")));
        properties.put("pivot_aggfunc", stringProp("Pivot agg: sum|mean|count|min|max|..."));
        properties.put("output_format", stringProp("records | dict"));
        return objectSchema(properties, List.of("operation"));
    }
}
