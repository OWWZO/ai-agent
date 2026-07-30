package org.wwz.ai.domain.agent.runtime.tool.common.docread;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CsvProcessorTool extends AbstractDocReadTool {

    public static final String TOOL_NAME = "csv_processor";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/csv_processor";
    }

    @Override
    protected String defaultDescription() {
        return "CSV/TSV processor: operation=read|query|write|stats|convert. "
                + "Read rows, filter/query, write output, compute stats, or convert formats.";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("operation", stringProp("read | query | write | stats | convert"));
        properties.put("file_path", stringProp("CSV/TSV file path under workspace"));
        properties.put("output_path", stringProp("Output path for write/convert"));
        properties.put("delimiter", stringProp("Delimiter, default auto-detect"));
        properties.put("encoding", stringProp("Encoding, default auto"));
        properties.put("limit", intProp("Max rows to return for read"));
        properties.put("offset", intProp("Row offset for read"));
        properties.put("query", stringProp("Simple filter expression for query"));
        properties.put("columns", stringProp("Comma-separated columns to keep"));
        return objectSchema(properties, List.of("operation"));
    }
}
