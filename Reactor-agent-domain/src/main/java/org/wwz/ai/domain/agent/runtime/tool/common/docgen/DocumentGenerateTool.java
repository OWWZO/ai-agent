package org.wwz.ai.domain.agent.runtime.tool.common.docgen;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * document_generate 工具契约。
 *
 * 该适配器把 Markdown、结构化 blocks 以及输出格式发布给共享文档生成服务；
 * 复杂的图表、分页、字体和文件落盘逻辑不在 domain 层重复实现。
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
                + "footnotes, definition lists, callouts, chart/metrics/checklist blocks, TOC, cover, headers/footers, "
                + "watermark, page setup, encryption, merging, and themes. "
                + "For charts, put a document chart JSON object inside a ```chart fence: "
                + "{\"type\":\"chart\",\"chart_type\":\"bar|line|pie|scatter|area|barh\","
                + "\"title\":\"...\",\"categories\":[\"...\"],\"series\":[{\"name\":\"...\",\"values\":[1,2]}]}. "
                + "Do not use ECharts option JSON such as xAxis/yAxis/series.data/title.text; it is rendered as code, not a chart. "
                 + "Chinese/CJK text is font-safe.";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        // output_path 是唯一必填项，其余字段用于覆盖格式推断和文档排版选项。
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("output_path", stringProp("Output file name, e.g. report.pdf / memo.docx. Extension decides format if format omitted."));
        properties.put("format", stringProp("Output format: pdf | docx | html | markdown. Defaults from output_path extension, else pdf."));
        properties.put("content", stringProp("Markdown body (preferred). Supports GFM tables, task lists, images, blockquotes, LaTeX math, footnotes, definition lists, YAML front matter, ```chart/```metrics/```checklist JSON fences, ::: callout, [TOC], and \\newpage. For a chart, use {type:'chart', chart_type:'bar|line|pie|scatter|area|barh', title:'...', categories:['...'], series:[{name:'...', values:[1,2]}]}; never use ECharts xAxis/yAxis/series.data JSON."));
        properties.put("blocks", arrayProp("Typed content blocks, appended after content when both are given: heading{text,level}, paragraph{text,alignment}, list{ordered,items}, table{columns,rows,align,caption,style,total_row,zebra,widths,number_format}, image{path|url|file_id|base64_data,caption,width_pct}, code{code,language}, quote{text,attribution}, callout{variant,title,text}, chart{chart_type,categories,series,title}, metrics{items}, checklist{title,groups|items}, divider, page_break, spacer{height_pt}, toc, math{latex,caption}, definition_list{items}, footnotes{items}, columns{columns,widths,gap_pt}.", objectProp("Typed content block")));
        properties.put("title", stringProp("Document title (metadata + cover)."));
        properties.put("subtitle", stringProp("Subtitle under title."));
        properties.put("author", stringProp("Author."));
        properties.put("date", stringProp("Date string on cover."));
        properties.put("subject", stringProp("Subject metadata."));
        properties.put("keywords", arrayProp("Keyword metadata.", stringProp("Keyword.")));
        properties.put("theme", stringProp("Theme name, e.g. professional."));
        properties.put("toc", boolProp("Include table of contents."));
        properties.put("cover", Map.of("description", "true for a cover page, or an object with title, subtitle, author, date, organization, and logo_path.", "anyOf", List.of(Map.of("type", "boolean"), objectProp("Cover object"))));
        properties.put("numbered_headings", boolProp("Number headings 1 / 1.1 / 1.1.1 (PDF)."));
        properties.put("justify", boolProp("Justify body text for formal reports."));
        properties.put("numbered_figures", boolProp("Auto-number table, image, and chart captions."));
        properties.put("section_pages", boolProp("Start every H1 section on a new page."));
        properties.put("header", objectProp("Running page header: text, show_page_number, alignment. PDF text supports {page}, {pages}, {title}, {author}, {date}, and {section}."));
        properties.put("footer", objectProp("Running page footer: text, show_page_number, alignment."));
        properties.put("watermark", objectProp("PDF watermark: text, color, opacity, angle, font_size."));
        properties.put("page", objectProp("Page setup: size (A4, LETTER, LEGAL, A3, A5), orientation, and point-based margins."));
        properties.put("encryption", objectProp("PDF password protection: user_password and optional owner_password."));
        properties.put("merge_sources", arrayProp("Existing PDF paths appended after the generated content (PDF only).", stringProp("PDF path.")));
        properties.put("fileName", stringProp("Alias of output_path for Reactor file naming."));
        return objectSchema(properties, List.of("output_path"));
    }
}
