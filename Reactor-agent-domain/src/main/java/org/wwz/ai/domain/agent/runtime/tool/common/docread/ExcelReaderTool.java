package org.wwz.ai.domain.agent.runtime.tool.common.docread;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExcelReaderTool extends AbstractDocReadTool {

    public static final String TOOL_NAME = "excel_reader";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/excel_reader";
    }

    @Override
    protected String defaultDescription() {
        return "Read Excel .xlsx/.xlsm: sheet by name/index, optional cell_range, "
                + "output_format=records|dict|list.";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("file_path", stringProp("Path to .xlsx/.xlsm under workspace"));
        properties.put("sheet_name", stringProp("Sheet name (preferred)"));
        properties.put("sheet_index", intProp("0-based sheet index if name omitted"));
        properties.put("cell_range", stringProp("Optional range e.g. A1:D10"));
        properties.put("has_header", boolProp("First row is header, default true"));
        properties.put("output_format", stringProp("records | dict | list, default records"));
        return objectSchema(properties, List.of("file_path"));
    }
}
