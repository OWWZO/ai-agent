package org.wwz.ai.domain.agent.runtime.tool.common.docgen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * slides_generate — PPTX (LeAgent aligned).
 */
public class SlidesGenerateTool extends AbstractDocGenTool {

    public static final String TOOL_NAME = "slides_generate";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/slides_generate";
    }

    @Override
    protected String defaultDescription() {
        return "Generate a professional PPTX presentation. Each slide picks a layout and carries markdown body text. "
                + "CJK/Chinese fonts are set explicitly so text never falls back to Latin-only faces. "
                + "Use when the user asks for PowerPoint / PPT / 幻灯片 / 演示文稿.";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        Map<String, Object> slideItem = new LinkedHashMap<>();
        slideItem.put("type", "object");
        slideItem.put("description", "One slide: layout, title, body markdown, optional image/chart/quote fields.");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("output_path", stringProp("Output file name, e.g. deck.pptx."));
        properties.put("title", stringProp("Deck title (auto title slide when first slide is not layout=title)."));
        properties.put("subtitle", stringProp("Deck subtitle."));
        properties.put("author", stringProp("Author."));
        properties.put("theme", stringProp("Theme, default executive_light."));
        properties.put("aspect", stringProp("Aspect ratio, default 16:9."));
        properties.put("slides", arrayProp("Non-empty array of slide objects.", slideItem));
        properties.put("fileName", stringProp("Alias of output_path."));
        return objectSchema(properties, List.of("slides"));
    }
}
