package org.wwz.ai.domain.agent.runtime.tool.common.canvas;

import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Render validated GenUI tree to DOCX as a minimal OOXML package (no POI).
 */
public final class GenUiDocxExporter {

    private GenUiDocxExporter() {
    }

    @SuppressWarnings("unchecked")
    public static byte[] export(Map<String, Object> normalizedTree, String mode) {
        // 导出器只接收 GenUI 校验/归一化后的树，先把节点压平成段落，再封装成最小 OOXML
        // zip 包；复杂交互组件降级为可阅读文本，保证导出不依赖浏览器运行时。
        List<String> paragraphs = new ArrayList<>();
        Object rootObj = normalizedTree == null ? null : normalizedTree.get("root");
        if (!(rootObj instanceof Map<?, ?> rootMap)) {
            paragraphs.add(p("(empty)"));
        } else {
            Map<String, Object> root = cast(rootMap);
            String kind = str(root.get("kind"));
            if ("deck".equalsIgnoreCase(mode) && "SlideDeck".equals(kind)) {
                // deck 模式按 Slide 分页；没有标准 Slide 子节点时保留直接 children，兼容
                // 历史树结构而不丢失内容。
                List<Map<String, Object>> slides = childrenOfKind(root, "Slide");
                if (slides.isEmpty()) {
                    slides = children(root);
                }
                for (int i = 0; i < slides.size(); i++) {
                    if (i > 0) {
                        paragraphs.add("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>");
                    }
                    collect(slides.get(i), paragraphs);
                }
            } else {
                collect(root, paragraphs);
            }
        }
        String documentXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                """ + String.join("\n", paragraphs) + """
                    <w:sectPr/>
                  </w:body>
                </w:document>
                """;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            put(zos, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);
            put(zos, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """);
            put(zos, "word/_rels/document.xml.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>
                    """);
            put(zos, "word/document.xml", documentXml);
            zos.finish();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("GenUI DOCX export failed: " + e.getMessage(), e);
        }
    }

    private static void collect(Map<String, Object> node, List<String> out) {
        if (node == null) {
            return;
        }
        String kind = str(node.get("kind"));
        Map<String, Object> props = props(node);
        List<Map<String, Object>> children = children(node);
        // 节点渲染采用“当前节点摘要 + 递归子节点”的规则；未识别 kind 也继续遍历 children，
        // 让新组件至少保留文本内容，而不是整棵子树静默丢失。
        switch (kind) {
            case "Heading" -> out.add(p(primaryText(props), true));
            case "Text", "Markdown", "Callout", "Alert", "AlertCard" -> out.add(p(primaryText(props)));
            case "SectionHeader" -> {
                if (StringUtils.isNotBlank(str(props.get("eyebrow")))) {
                    out.add(p(str(props.get("eyebrow")).toUpperCase(), true));
                }
                out.add(p(primaryText(props), true));
                if (StringUtils.isNotBlank(str(props.get("subtitle")))) {
                    out.add(p(str(props.get("subtitle"))));
                }
            }
            case "Card", "DataCard", "MetricCard", "ProfileCard", "MediaCard", "QuoteCard", "WeatherCard" -> {
                String title = firstNonBlank(props, "title", "name", "quote", "location");
                if (StringUtils.isNotBlank(title)) {
                    out.add(p(title, true));
                }
                String body = firstNonBlank(props, "value", "description", "bio", "message", "condition", "subtitle");
                if (StringUtils.isNotBlank(body)) {
                    out.add(p(body));
                }
                children.forEach(c -> collect(c, out));
            }
            case "Stat" -> out.add(p(str(props.get("label")) + ": " + str(props.get("value"))
                    + (StringUtils.isNotBlank(str(props.get("delta"))) ? " (" + str(props.get("delta")) + ")" : ""), true));
            case "List" -> {
                boolean ordered = Boolean.TRUE.equals(props.get("ordered")) || "true".equalsIgnoreCase(str(props.get("ordered")));
                int i = 1;
                for (Map<String, Object> child : children) {
                    out.add(p((ordered ? (i++ + ". ") : "• ") + primaryText(props(child))));
                }
            }
            case "ListItem" -> out.add(p("• " + primaryText(props)));
            case "Table" -> {
                List<?> headers = props.get("headers") instanceof List<?> h ? h : List.of();
                if (!headers.isEmpty()) {
                    out.add(p(String.join(" | ", headers.stream().map(String::valueOf).toList()), true));
                }
                for (Map<String, Object> row : children) {
                    List<String> cells = children(row).stream().map(c -> primaryText(props(c))).toList();
                    out.add(p(String.join(" | ", cells)));
                }
            }
            case "CodeBlock" -> out.add(p(str(props.get("code"))));
            case "Chart" -> out.add(p("Chart(" + str(props.get("chart")) + "): " + str(props.get("title"))));
            case "ParametricLab", "PythagorasLab", "GeometryLab", "InteractiveLab" ->
                    out.add(p("Interactive lab: " + firstNonBlank(props, "title", "scene", "preset")));
            case "ConceptDemo", "AnimStepLab", "KnowledgeDemo" ->
                    out.add(p("Concept demo: " + firstNonBlank(props, "title", "scene", "preset")));
            case "Quiz", "WorkedExample", "BeforeAfter", "NumberLine", "CoordinateGrid", "BindScope" ->
                    out.add(p(kind + ": " + firstNonBlank(props, "title", "prompt", "problem")));
            case "Image", "Video", "Model3D" -> out.add(p("[" + kind + "] " + str(props.get("src"))));
            case "Button", "LinkButton", "InteractiveButton", "ToggleButton" ->
                    out.add(p("[" + firstNonBlank(props, "label", "value", "text") + "]", true));
            case "KeyValueList" -> {
                Object items = props.get("items");
                if (items instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            out.add(p(str(m.get("key")) + ": " + str(m.get("value"))));
                        }
                    }
                }
                children.forEach(c -> collect(c, out));
            }
            case "Slide" -> {
                if (StringUtils.isNotBlank(str(props.get("eyebrow")))) {
                    out.add(p(str(props.get("eyebrow")).toUpperCase(), true));
                }
                if (StringUtils.isNotBlank(str(props.get("title")))) {
                    out.add(p(str(props.get("title")), true));
                }
                if (StringUtils.isNotBlank(str(props.get("subtitle")))) {
                    out.add(p(str(props.get("subtitle"))));
                }
                children.forEach(c -> collect(c, out));
            }
            case "Divider" -> out.add(p("—— " + str(props.get("label")) + " ——"));
            case "JsonDebug" -> out.add(p(String.valueOf(props.getOrDefault("value", ""))));
            default -> {
                String text = primaryText(props);
                if (StringUtils.isNotBlank(text)) {
                    out.add(p(text));
                }
                children.forEach(c -> collect(c, out));
            }
        }
    }

    private static String p(String text) {
        return p(text, false);
    }

    private static String p(String text, boolean bold) {
        String safe = xml(text == null ? "" : text);
        if (bold) {
            return "<w:p><w:r><w:rPr><w:b/></w:rPr><w:t xml:space=\"preserve\">" + safe + "</w:t></w:r></w:p>";
        }
        return "<w:p><w:r><w:t xml:space=\"preserve\">" + safe + "</w:t></w:r></w:p>";
    }

    private static void put(ZipOutputStream zos, String name, String content) throws Exception {
        // DOCX 是 zip 容器，所有 XML 都按 UTF-8 写入；这里集中创建 entry，避免遗漏关闭
        // entry 导致生成的文件能下载但 Office 无法打开。
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String primaryText(Map<String, Object> props) {
        return firstNonBlank(props, "value", "label", "title", "message", "description",
                "subtitle", "name", "quote", "content", "text", "code");
    }

    private static String firstNonBlank(Map<String, Object> props, String... keys) {
        if (props == null) {
            return "";
        }
        for (String key : keys) {
            String v = str(props.get(key));
            if (StringUtils.isNotBlank(v)) {
                return v;
            }
        }
        return "";
    }

    private static List<Map<String, Object>> children(Map<String, Object> node) {
        Object c = node.get("children");
        if (!(c instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance).map(item -> cast((Map<?, ?>) item)).toList();
    }

    private static List<Map<String, Object>> childrenOfKind(Map<String, Object> node, String kind) {
        return children(node).stream().filter(c -> kind.equals(str(c.get("kind")))).toList();
    }

    private static Map<String, Object> props(Map<String, Object> node) {
        Object p = node.get("props");
        if (p instanceof Map<?, ?> m) {
            return cast(m);
        }
        return Map.of();
    }

    private static Map<String, Object> cast(Map<?, ?> map) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() != null) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return out;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
