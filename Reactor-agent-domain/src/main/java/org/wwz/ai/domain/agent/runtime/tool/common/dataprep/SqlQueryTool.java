package org.wwz.ai.domain.agent.runtime.tool.common.dataprep;

import org.wwz.ai.domain.agent.runtime.tool.common.docread.AbstractDocReadTool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表格数据只读 SQL 查询工具。
 * <p>领域侧声明 SELECT 查询契约，远端执行器负责加载数据并拒绝破坏性 SQL。</p>
 */
public class SqlQueryTool extends AbstractDocReadTool {

    public static final String TOOL_NAME = "sql_query";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/sql_query";
    }

    @Override
    protected String defaultDescription() {
        return "Read-only SQL SELECT on tabular data (JOIN/WHERE/GROUP BY/ORDER BY/aggregates). "
                + "Provide query + tables map or data/source_path (registered as table 'data'). "
                + "Destructive SQL is rejected.";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", stringProp("SELECT-only SQL query"));
        properties.put("tables", objectProp("name -> records|artifact|path"));
        properties.put("data", Map.of("type", "array", "items", objectProp("Row object")));
        properties.put("source_path", stringProp("Single table file path (alias table name: data)"));
        properties.put("limit", intProp("Max result rows (default 1000)"));
        properties.put("explain", boolProp("If true, only explain query without executing"));
        properties.put("output_format", stringProp("records | dict"));
        return objectSchema(properties, List.of("query"));
    }
}
