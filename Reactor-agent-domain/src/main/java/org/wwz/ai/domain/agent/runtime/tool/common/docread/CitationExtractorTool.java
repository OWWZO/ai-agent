package org.wwz.ai.domain.agent.runtime.tool.common.docread;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CitationExtractorTool extends AbstractDocReadTool {

    public static final String TOOL_NAME = "citation_extractor";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/citation_extractor";
    }

    @Override
    protected String defaultDescription() {
        return "Extract reference/bibliography list from an academic PDF "
                + "(marker, full text, DOI/URL when detected).";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("file_path", stringProp("Path to the PDF file under workspace"));
        return objectSchema(properties, List.of("file_path"));
    }
}
