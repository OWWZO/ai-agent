package org.wwz.ai.domain.agent.runtime.tool.common.docread;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Word 文档结构读取工具，支持段落、标题、表格以及可选页眉页脚提取。
 */
public class WordReaderTool extends AbstractDocReadTool {

    public static final String TOOL_NAME = "word_reader";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/word_reader";
    }

    @Override
    protected String defaultDescription() {
        return "Read Word documents (.docx/.docm structure; legacy .doc plain text). "
                + "Extract paragraphs, headings, tables, optional headers/footers.";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("file_path", stringProp("Path to .docx/.docm/.doc under workspace"));
        properties.put("extract_tables", boolProp("Extract tables, default true"));
        properties.put("extract_headers", boolProp("Extract headers/footers, default false"));
        properties.put("preserve_structure", boolProp("Preserve structure, default true"));
        return objectSchema(properties, List.of("file_path"));
    }
}
