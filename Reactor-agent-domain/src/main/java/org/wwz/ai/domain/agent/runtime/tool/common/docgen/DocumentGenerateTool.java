package org.wwz.ai.domain.agent.runtime.tool.common.docgen;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * document_generate — PDF / DOCX / HTML / Markdown (LeAgent aligned).
 */
public class DocumentGenerateTool extends AbstractDocGenTool {

    public static final String TOOL_NAME = "document_generate";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/document_generate";
    }

    @Override
    protected String defaultDescription() {
        return "Generate a professional document (PDF, DOCX, HTML, or Markdown) from markdown content. "
                + "Supports headings, tables, images, task lists, code blocks, quotes, LaTeX math, "
                + "footnotes, callouts, chart/metrics blocks, TOC, cover, watermark, themes. "
                + "Chinese/CJK text is font-safe. Prefer this over report_tool when the user needs a real .pdf/.docx file.";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("output_path", stringProp("Output file name, e.g. report.pdf / memo.docx. Extension decides format if format omitted."));
        properties.put("format", stringProp("Output format: pdf | docx | html | markdown. Defaults from output_path extension, else pdf."));
        properties.put("content", stringProp("Markdown body (preferred). Supports GFM tables, task lists, ```chart/```metrics fences, ::: callout, [TOC], \\newpage."));
        properties.put("blocks", arrayProp("Typed block array escape hatch (optional if content provided).", Map.of("type", "object")));
        properties.put("title", stringProp("Document title (metadata + cover)."));
        properties.put("subtitle", stringProp("Subtitle under title."));
        properties.put("author", stringProp("Author."));
        properties.put("date", stringProp("Date string on cover."));
        properties.put("subject", stringProp("Subject metadata."));
        properties.put("theme", stringProp("Theme name, e.g. professional."));
        properties.put("toc", boolProp("Include table of contents."));
        properties.put("cover", boolProp("Include cover page."));
        properties.put("numbered_headings", boolProp("Number headings 1 / 1.1 / 1.1.1 (PDF)."));
        properties.put("fileName", stringProp("Alias of output_path for Reactor file naming."));
        return objectSchema(properties, List.of("content"));
    }
}
