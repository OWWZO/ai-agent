package org.wwz.ai.domain.agent.runtime.tool.common.docgen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * excel_generator — XLSX (LeAgent aligned).
 */
public class ExcelGeneratorTool extends AbstractDocGenTool {

    public static final String TOOL_NAME = "excel_generator";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/excel_generator";
    }

    @Override
    protected String defaultDescription() {
        return "Create Excel .xlsx files with multiple sheets, headers, formulas, charts, freeze panes, "
                + "auto-filter, data validation, cell styles and conditional formatting. "
                + "Use when the user needs a real spreadsheet file.";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("output_path", stringProp("Output file name, e.g. data.xlsx."));
        properties.put("sheets", arrayProp("Array of sheet definitions (required).", objectProp("Sheet definition: name, headers, data (2D array), formulas, charts, styles, etc.")));
        properties.put("preset", stringProp("Optional preset, e.g. financial."));
        properties.put("workbook_properties", objectProp("Workbook metadata: title/author/subject/company."));
        properties.put("fileName", stringProp("Alias of output_path."));
        return objectSchema(properties, List.of("sheets"));
    }
}
