package org.wwz.ai.domain.agent.runtime.tool.common.canvas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * GenUI 组件目录及其属性提示。
 *
 * <p>目录是模型的能力发现契约，也是 {@link GenUiSchema} 的允许 kind 白名单。
 * 后端只验证目录级别的种类，具体组件渲染和未知组件的 fallback 由前端承担。</p>
 */
public final class GenUiCatalog {

    private static final List<Map<String, Object>> CATALOG = buildCatalog();
    public static final Set<String> ALLOWED_KINDS = CATALOG.stream()
            .map(e -> String.valueOf(e.get("kind")))
            .collect(Collectors.toUnmodifiableSet());

    private GenUiCatalog() {
    }

    public static List<Map<String, Object>> listCatalog() {
        // 返回不可变列表，防止单次工具调用修改全局组件能力目录。
        return CATALOG;
    }

    public static boolean isAllowedKind(String kind) {
        return kind != null && ALLOWED_KINDS.contains(kind);
    }

    private static List<Map<String, Object>> buildCatalog() {
        List<Map<String, Object>> list = new ArrayList<>();
        // 按布局、排版、数据展示、卡片、交互和嵌入能力分组，保持提示目录可读且便于扩展。
        list.add(entry("Stack", "Vertical flex stack", Map.of("gap", "number", "align", "start|center|end|stretch", "padding", "number")));
        list.add(entry("Grid", "CSS grid", Map.of("columns", "1-6", "gap", "number", "minChildWidth", "string")));
        list.add(entry("Row", "Horizontal flex row", Map.of("gap", "number", "justify", "start|center|end|between|around")));
        list.add(entry("Spacer", "Vertical whitespace", Map.of("size", "number px")));
        list.add(entry("ScrollArea", "Scrollable area", Map.of("maxHeight", "number px")));
        list.add(entry("Tabs", "Tab container; children TabItem", Map.of("defaultTab", "string")));
        list.add(entry("TabItem", "Tab pane", Map.of("label", "string")));
        list.add(entry("Accordion", "Accordion container; children AccordionItem", Map.of()));
        list.add(entry("AccordionItem", "Accordion section", Map.of("title", "string", "defaultOpen", "boolean")));
        list.add(entry("AspectBox", "Fixed aspect frame", Map.of("ratio", "16:9|4:3|1:1|3:2", "maxWidth", "number")));
        list.add(entry("DesignSurface", "Themed wrapper", Map.of("preset", "minimal|editorial|card|slide|poster|brutalist|geek", "padding", "none|sm|md|lg")));
        // Typography
        list.add(entry("Text", "Body text", Map.of("value", "string", "size", "xs|sm|base|lg", "color", "muted|default|primary|success|warning|error", "bold", "boolean")));
        list.add(entry("Heading", "Section title", Map.of("level", "1-4", "value", "string")));
        list.add(entry("Divider", "Horizontal rule", Map.of("label", "optional string")));
        list.add(entry("Skeleton", "Loading placeholder", Map.of("lines", "number", "variant", "text|card|avatar")));
        // Data display
        list.add(entry("Badge", "Status badge", Map.of("value", "string", "variant", "default|primary|success|warning|error|info")));
        list.add(entry("Tag", "Tag label", Map.of("label", "string", "color", "gray|blue|green|red|yellow|purple")));
        list.add(entry("Stat", "KPI stat", Map.of("label", "string", "value", "string", "delta", "string", "trend", "up|down|neutral")));
        list.add(entry("Progress", "Progress bar", Map.of("value", "0-100", "label", "string", "color", "primary|success|warning|error")));
        list.add(entry("Avatar", "Avatar", Map.of("src", "url", "name", "string", "size", "sm|md|lg")));
        list.add(entry("Image", "Image", Map.of("src", "url", "alt", "string", "caption", "string", "fit", "cover|contain|fill")));
        list.add(entry("Video", "Video player", Map.of("src", "url", "poster", "url", "autoPlay", "boolean", "loop", "boolean", "controls", "boolean")));
        list.add(entry("Model3D", "GLB/GLTF 3D model viewer (OrbitControls). Requires reachable src URL.",
                Map.of(
                        "src", "glb/gltf url",
                        "height", "number",
                        "background", "css color",
                        "autoRotate", "boolean",
                        "rotateSpeed", "number",
                        "wireframe", "boolean",
                        "caption", "string"
                )));
        list.add(entry("LiveCamera", "Live camera preview", Map.of("facingMode", "user|environment", "mirrored", "boolean")));
        list.add(entry("Icon", "Lucide icon or emoji", Map.of("name", "string", "size", "number", "iconSet", "auto|lucide|emoji")));
        list.add(entry("Table", "Table; children TableRow", Map.of("headers", "string[]", "striped", "boolean", "compact", "boolean")));
        list.add(entry("TableRow", "Table row; children TableCell", Map.of("highlight", "boolean")));
        list.add(entry("TableCell", "Table cell", Map.of("value", "string", "align", "left|center|right", "bold", "boolean")));
        list.add(entry("List", "List container", Map.of("ordered", "boolean", "variant", "default|bordered|separated")));
        list.add(entry("ListItem", "List item", Map.of("value", "string", "icon", "string")));
        list.add(entry("CodeBlock", "Code block", Map.of("code", "string", "language", "string", "title", "string")));
        list.add(entry("Markdown", "Markdown block", Map.of("content", "string", "value", "string")));
        list.add(entry("Chart", "Interactive chart (ECharts). Prefer over canvas_publish for pie/bar/line.",
                Map.of(
                        "chart", "line|bar|area|pie",
                        "categories", "string[]",
                        "series", "[{name,values}]",
                        "title", "string",
                        "height", "number",
                        "stacked", "boolean",
                        "showLegend", "boolean",
                        "showGrid", "boolean"
                )));
        // Cards
        list.add(entry("Card", "Card container", Map.of("title", "string", "subtitle", "string", "variant", "default|elevated|outlined", "padding", "sm|md|lg")));
        list.add(entry("WeatherCard", "Weather card", Map.of("location", "string", "temperature", "string", "condition", "string", "icon", "string", "forecast", "array")));
        list.add(entry("DataCard", "Data summary card", Map.of("title", "string", "value", "string", "description", "string", "icon", "string")));
        list.add(entry("MetricCard", "KPI metric card", Map.of("title", "string", "value", "string", "delta", "string", "trend", "up|down|neutral", "period", "string")));
        list.add(entry("ProfileCard", "Profile card", Map.of("name", "string", "role", "string", "avatarUrl", "url", "bio", "string", "stats", "array")));
        list.add(entry("MediaCard", "Media card", Map.of("imageUrl", "url", "title", "string", "description", "string")));
        list.add(entry("AlertCard", "Alert card", Map.of("title", "string", "message", "string", "variant", "info|success|warning|error")));
        list.add(entry("TimelineCard", "Timeline card", Map.of("title", "string", "items", "array of {time,title,description}")));
        list.add(entry("SlideDeck", "Slide deck; children Slide", Map.of("title", "string")));
        list.add(entry("Slide", "Slide page", Map.of("title", "string", "subtitle", "string", "eyebrow", "string")));
        list.add(entry("KpiBoard", "KPI board grid", Map.of("title", "string")));
        list.add(entry("FeatureGrid", "Feature grid", Map.of("title", "string", "columns", "number")));
        list.add(entry("Stepper", "Stepper", Map.of("steps", "string[]", "active", "number")));
        list.add(entry("QuoteCard", "Quote card", Map.of("quote", "string", "author", "string", "role", "string")));
        list.add(entry("ImageGallery", "Image gallery", Map.of("images", "array of {src,alt}")));
        list.add(entry("KeyValueList", "Key-value list", Map.of("items", "array of {key,value}")));
        list.add(entry("SectionHeader", "Section header", Map.of("title", "string", "subtitle", "string", "eyebrow", "string")));
        // Interactive — props.action = {type,payload}; type: send_message|open_url|navigate|submit_form
        list.add(entry("Button", "Clickable button; set action to send_message/open_url/submit_form",
                Map.of("label", "string", "variant", "default|primary", "action", "{type,payload}", "actionId", "string legacy")));
        list.add(entry("InteractiveButton", "Primary interactive button with action",
                Map.of("label", "string", "action", "{type,payload}", "disabled", "boolean")));
        list.add(entry("ToggleButton", "Toggle button with action",
                Map.of("label", "string", "pressed", "boolean", "action", "{type,payload}")));
        list.add(entry("LinkButton", "Link or action button", Map.of("label", "string", "href", "url", "action", "{type,payload}")));
        list.add(entry("Input", "Text input (interactive inside Form with name)",
                Map.of("name", "string", "label", "string", "placeholder", "string", "value", "string")));
        list.add(entry("Select", "Select (interactive inside Form with name)",
                Map.of("name", "string", "label", "string", "options", "string[]", "value", "string")));
        list.add(entry("Chip", "Chip", Map.of("label", "string", "selected", "boolean")));
        list.add(entry("ChipGroup", "Chip group; children Chip", Map.of("label", "string")));
        // Forms
        list.add(entry("Form", "Form scope; named fields + Button action submit_form",
                Map.of("title", "string", "formId", "string", "submitLabel", "string")));
        list.add(entry("NumberInput", "Number input (interactive inside Form with name)",
                Map.of("name", "string", "label", "string", "value", "number", "min", "number", "max", "number")));
        list.add(entry("Switch", "Switch (interactive inside Form with name)",
                Map.of("name", "string", "label", "string", "checked", "boolean")));
        list.add(entry("Slider", "Slider (interactive inside Form with name)",
                Map.of("name", "string", "label", "string", "value", "number", "min", "number", "max", "number")));
        list.add(entry("FileInput", "File path input (interactive inside Form with name)",
                Map.of("name", "string", "label", "string", "accept", "string")));
        list.add(entry("Textarea", "Textarea (interactive inside Form with name)",
                Map.of("name", "string", "label", "string", "value", "string", "rows", "number")));
        // Feedback
        list.add(entry("Alert", "Alert banner", Map.of("message", "string", "variant", "info|success|warning|error")));
        list.add(entry("Callout", "Callout", Map.of("title", "string", "message", "string", "variant", "info|success|warning|error")));
        // Embed
        list.add(entry("HostedCanvasFrame", "Hosted canvas frame", Map.of("canvasId", "string", "height", "number")));
        list.add(entry("HtmlFrame", "Sandboxed HTML frame", Map.of("html", "string", "height", "number", "title", "string")));
        list.add(entry("ThreeJsFrame",
                "Procedural Three.js scene (box/sphere/icosahedron/…). Prefer over free-form HTML for simple 3D.",
                Map.of(
                        "geometry", "box|sphere|icosahedron|octahedron|dodecahedron|tetrahedron|torusKnot",
                        "color", "hex color",
                        "accentColor", "hex color",
                        "height", "number",
                        "background", "css color",
                        "autoRotate", "boolean",
                        "wireframe", "boolean",
                        "particles", "number",
                        "orbiters", "number",
                        "title", "string"
                )));
        list.add(entry("JsonDebug", "JSON debug viewer", Map.of("value", "object|string", "title", "string")));
        return List.copyOf(list);
    }

    private static Map<String, Object> entry(String kind, String description, Map<String, String> props) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind);
        m.put("description", description);
        m.put("props", props);
        return m;
    }
}
