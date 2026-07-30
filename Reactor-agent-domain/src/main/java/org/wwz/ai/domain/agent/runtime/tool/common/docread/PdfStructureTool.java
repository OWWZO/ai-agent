package org.wwz.ai.domain.agent.runtime.tool.common.docread;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PdfStructureTool extends AbstractDocReadTool {

    public static final String TOOL_NAME = "pdf_structure";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/pdf_structure";
    }

    @Override
    protected String defaultDescription() {
        return "Analyze PDF structure: page count, title, outline/bookmarks, heuristic section headings, "
                + "figures/tables. Use before summarizing academic papers.";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("file_path", stringProp("Path to the PDF file under workspace"));
        return objectSchema(properties, List.of("file_path"));
    }
}
