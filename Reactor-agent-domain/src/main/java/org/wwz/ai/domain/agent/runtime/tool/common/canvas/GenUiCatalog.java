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
        // 按容器、数据展示、卡片、交互和嵌入能力分组，保持提示目录可读且便于扩展。
        list.add(entry("AspectBox", "Fixed aspect frame", Map.of("ratio", "16:9|4:3|1:1|3:2", "maxWidth", "number")));
        list.add(entry("Skeleton", "Loading placeholder", Map.of("lines", "number", "variant", "text|card|avatar")));
        // Data display
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
        // Teaching / parametric labs — client-side sliders recompute formulas + scene (no chat turn)
        list.add(entry("ParametricLab",
                "Interactive teaching lab: drag sliders → formulas + SVG scene update live. "
                        + "Use for math demos (Pythagoras, circle, functions). Prefer over static Chart for pedagogy.",
                Map.of(
                        "title", "string",
                        "description", "string",
                        "scene", "right_triangle|circle|rectangle|linear|quadratic|unit_circle|number_line|coordinate|custom_svg|none",
                        "params", "[{id,label,value,min,max,step,unit}]",
                        "outputs", "[{id,label,expr,format,unit}] expr uses param ids + sqrt/sin/cos/pi",
                        "svg", "optional SVG markup with {{id}} placeholders when scene=custom_svg",
                        "formulaNote", "string shown as equation badge",
                        "height", "number",
                        "showFormulas", "boolean"
                )));
        list.add(entry("PythagorasLab",
                "Shortcut for ParametricLab scene=right_triangle (勾股定理 a²+b²=c²). Drag a/b → triangle + c update.",
                Map.of(
                        "title", "string",
                        "description", "string",
                        "params", "optional override [{id:a|b,...}]",
                        "outputs", "optional override",
                        "height", "number"
                )));
        list.add(entry("GeometryLab", "Alias of ParametricLab for geometry teaching demos",
                Map.of("scene", "right_triangle|circle|rectangle|unit_circle", "params", "array", "outputs", "array", "title", "string")));
        list.add(entry("InteractiveLab", "Alias of ParametricLab for general interactive formula demos",
                Map.of("scene", "string", "params", "array", "outputs", "array", "title", "string", "svg", "string")));
        // Concept animation demos — play/pause/scrub step narrative (no chat turn)
        Map<String, String> conceptProps = new LinkedHashMap<>();
        conceptProps.put("title", "string");
        conceptProps.put("description", "string");
        conceptProps.put("scene", "flow|stack|tree|formula|compare|sequence");
        conceptProps.put("steps", "[{title,caption,duration,ms,highlight[],badge,formula}]");
        conceptProps.put("nodes", "[{id,label}] for flow/stack/tree/sequence");
        conceptProps.put("edges", "[{id,from,to,label}] for flow/tree/sequence");
        conceptProps.put("formulas", "string[] tokens for scene=formula");
        conceptProps.put("left", "string[] for scene=compare");
        conceptProps.put("right", "string[] for scene=compare");
        conceptProps.put("autoPlay", "boolean default true");
        conceptProps.put("loop", "boolean default true");
        conceptProps.put("height", "number");
        list.add(entry("ConceptDemo",
                "Animated concept walkthrough: auto-play steps with play/pause/scrub. "
                        + "Use for knowledge demos (request flow, layering, tree, formula transform, compare). "
                        + "Prefer over static Stepper/Timeline when user should SEE the idea unfold.",
                conceptProps));
        list.add(entry("AnimStepLab", "Alias of ConceptDemo for step-by-step concept animation",
                Map.of("scene", "flow|stack|tree|formula|compare|sequence", "steps", "array", "title", "string")));
        list.add(entry("KnowledgeDemo", "Alias of ConceptDemo for knowledge/concept animation demos",
                Map.of("scene", "string", "steps", "array", "nodes", "array", "edges", "array", "title", "string")));
        list.add(entry("BindScope",
                "Reactive scope: sliders update sibling nodes. Children props may use {{expr}} or $id (e.g. Chart, NumberLine).",
                Map.of(
                        "params", "[{id,label,value,min,max,step}]",
                        "outputs", "optional derived [{id,expr}] e.g. {id:c, expr:sqrt(a*a+b*b)}",
                        "showControls", "boolean default true"
                )));
        list.add(entry("ReactiveScope", "Alias of BindScope",
                Map.of("params", "array", "outputs", "array")));
        list.add(entry("Quiz",
                "Multiple-choice quiz with local reveal (no chat). Use after ConceptDemo/ParametricLab.",
                Map.of(
                        "prompt", "string question",
                        "options", "string[] or [{id,label}]",
                        "answer", "id or id[]",
                        "explanation", "string shown after submit",
                        "multi", "boolean"
                )));
        list.add(entry("WorkedExample",
                "Step-by-step worked example; user reveals each step then final answer.",
                Map.of("title", "string", "problem", "string", "steps", "[{title,body,answer}]", "answer", "final answer")));
        list.add(entry("BeforeAfter",
                "Drag-to-compare two images or text panels.",
                Map.of("before", "url or text", "after", "url or text", "beforeLabel", "string", "afterLabel", "string")));
        list.add(entry("CompareSlider", "Alias of BeforeAfter",
                Map.of("before", "string", "after", "string")));
        list.add(entry("NumberLine",
                "Number line. Bind value/points via BindScope {{x}}.",
                Map.of("min", "number", "max", "number", "value", "number or {{x}}", "points", "[{x,label}]", "title", "string")));
        list.add(entry("CoordinateGrid",
                "Coordinate plane with points/vectors/fn. Bind via BindScope.",
                Map.of("xmin", "number", "xmax", "number", "points", "[{x,y,label}]", "vectors", "[{x,y}]", "fn", "y expr in x", "title", "string")));
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
