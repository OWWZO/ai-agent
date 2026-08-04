package org.wwz.ai.domain.agent.runtime.tool.common.docread;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用 PDF 读取与文件操作工具。
 * <p>学术论文结构分析由 PdfStructureTool 负责，本类提供文本、页面和文件级操作。</p>
 */
public class PdfReaderTool extends AbstractDocReadTool {

    public static final String TOOL_NAME = "pdf_reader";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/pdf_reader";
    }

    @Override
    protected String defaultDescription() {
        return "PDF toolkit: operation=read|extract_tables|extract_images|extract_links|search|"
                + "page_info|outline|convert_to_images|split|merge|extract_pages|metadata. "
                + "Prefer read for text; use pdf_structure for academic outline.";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("operation", stringProp(
                "read|extract_tables|extract_images|extract_links|search|page_info|outline|"
                        + "convert_to_images|split|merge|extract_pages|metadata (default read)"));
        properties.put("file_path", stringProp("PDF path under workspace"));
        properties.put("start_page", intProp("1-based start page"));
        properties.put("end_page", intProp("1-based end page"));
        properties.put("query", stringProp("Search query for search operation"));
        properties.put("output_path", stringProp("Output path for merge/extract_pages/convert"));
        properties.put("output_dir", stringProp("Output directory for split/images"));
        properties.put("max_chars", intProp("Max characters for read, default 200000"));
        return objectSchema(properties, List.of());
    }
}
