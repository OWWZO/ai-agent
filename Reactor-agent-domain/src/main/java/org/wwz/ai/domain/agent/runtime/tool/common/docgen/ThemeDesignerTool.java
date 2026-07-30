package org.wwz.ai.domain.agent.runtime.tool.common.docgen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * theme_designer — create/list/get/delete custom docgen themes from brand colors.
 */
public class ThemeDesignerTool extends AbstractDocGenTool {

    public static final String TOOL_NAME = "theme_designer";

    @Override
    public String getName() {
        return TOOL_NAME;
    }

    @Override
    protected String endpointPath() {
        return "/v1/tool/theme_designer";
    }

    @Override
    protected String defaultDescription() {
        return "Design custom themes for document_generate / slides_generate. "
                + "action=create|save|list|get|delete. create derives a contrast-checked theme "
                + "from primary brand color; saved themes are selectable via the theme parameter.";
    }

    @Override
    protected Map<String, Object> defaultParams() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("action", stringProp("create | save | list | get | delete"));
        properties.put("name", stringProp("Theme name (required except list)"));
        properties.put("kind", stringProp("document | deck"));
        properties.put("primary", stringProp("create: brand primary color #RRGGBB"));
        properties.put("accent", stringProp("create: optional accent #RRGGBB"));
        properties.put("mode", stringProp("create: light | dark"));
        properties.put("heading_font", stringProp("create: heading font name"));
        properties.put("body_font", stringProp("create: body font name"));
        properties.put("east_asia_font", stringProp("create: CJK font e.g. Microsoft YaHei"));
        properties.put("overrides", Map.of("type", "object", "description", "create: deep-merge overrides"));
        properties.put("payload", Map.of("type", "object", "description", "save: explicit theme payload"));
        properties.put("dry_run", boolProp("create/save: return payload without persisting"));
        return objectSchema(properties, List.of("action"));
    }
}
