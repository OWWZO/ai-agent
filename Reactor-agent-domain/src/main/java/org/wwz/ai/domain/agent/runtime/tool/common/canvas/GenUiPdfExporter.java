package org.wwz.ai.domain.agent.runtime.tool.common.canvas;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.commons.lang3.StringUtils;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/**
 * Render validated GenUI tree to PDF via OpenPDF (text-first best-effort).
 */
public final class GenUiPdfExporter {

    private GenUiPdfExporter() {
    }

    @SuppressWarnings("unchecked")
    public static byte[] export(Map<String, Object> normalizedTree, String mode) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 42, 42, 48, 48);
            PdfWriter.getInstance(document, out);
            document.open();

            Object rootObj = normalizedTree == null ? null : normalizedTree.get("root");
            if (!(rootObj instanceof Map<?, ?> rootMap)) {
                document.add(new Paragraph("(empty)"));
            } else {
                Map<String, Object> root = cast(rootMap);
                String kind = str(root.get("kind"));
                if ("deck".equalsIgnoreCase(mode) && "SlideDeck".equals(kind)) {
                    List<Map<String, Object>> slides = childrenOfKind(root, "Slide");
                    if (slides.isEmpty()) {
                        slides = children(root);
                    }
                    for (int i = 0; i < slides.size(); i++) {
                        if (i > 0) {
                            document.newPage();
                        }
                        renderNode(document, slides.get(i));
                    }
                } else {
                    renderNode(document, root);
                }
            }
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("GenUI PDF export failed: " + e.getMessage(), e);
        }
    }

    private static void renderNode(Document document, Map<String, Object> node) throws DocumentException {
        if (node == null) {
            return;
        }
        String kind = str(node.get("kind"));
        Map<String, Object> props = props(node);
        List<Map<String, Object>> children = children(node);

        switch (kind) {
            case "Heading" -> {
                int level = clamp(intVal(props.get("level"), 2), 1, 4);
                document.add(heading(primaryText(props), level));
            }
            case "Text", "Markdown", "Callout", "Alert", "AlertCard" ->
                    document.add(body(primaryText(props)));
            case "SectionHeader" -> {
                if (StringUtils.isNotBlank(str(props.get("eyebrow")))) {
                    document.add(muted(str(props.get("eyebrow")).toUpperCase()));
                }
                document.add(heading(primaryText(props), 2));
                if (StringUtils.isNotBlank(str(props.get("subtitle")))) {
                    document.add(body(str(props.get("subtitle"))));
                }
            }
            case "Card", "DataCard", "MetricCard", "ProfileCard", "MediaCard", "QuoteCard", "WeatherCard" -> {
                String title = firstNonBlank(props, "title", "name", "quote", "location");
                if (StringUtils.isNotBlank(title)) {
                    document.add(heading(title, 3));
                }
                String body = firstNonBlank(props, "value", "description", "bio", "message", "condition", "subtitle");
                if (StringUtils.isNotBlank(body)) {
                    document.add(body(body));
                }
                for (Map<String, Object> child : children) {
                    renderNode(document, child);
                }
            }
            case "Stat" -> document.add(body(
                    str(props.get("label")) + ": " + str(props.get("value"))
                            + (StringUtils.isNotBlank(str(props.get("delta"))) ? " (" + str(props.get("delta")) + ")" : "")));
            case "List" -> {
                boolean ordered = Boolean.TRUE.equals(props.get("ordered")) || "true".equalsIgnoreCase(str(props.get("ordered")));
                int i = 1;
                for (Map<String, Object> child : children) {
                    String item = primaryText(props(child));
                    document.add(body((ordered ? (i++ + ". ") : "• ") + item));
                }
            }
            case "ListItem" -> document.add(body("• " + primaryText(props)));
            case "Table" -> document.add(buildTable(props, children));
            case "CodeBlock" -> document.add(mono(str(props.get("code"))));
            case "Chart" -> document.add(body("Chart(" + str(props.get("chart")) + "): " + str(props.get("title"))));
            case "Image", "Video", "Model3D" -> document.add(muted("[" + kind + "] " + str(props.get("src"))));
            case "Button", "LinkButton", "InteractiveButton", "ToggleButton" ->
                    document.add(body("[" + firstNonBlank(props, "label", "value", "text") + "]"));
            case "KeyValueList" -> {
                Object items = props.get("items");
                if (items instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> m) {
                            document.add(body(str(m.get("key")) + ": " + str(m.get("value"))));
                        }
                    }
                }
                for (Map<String, Object> child : children) {
                    renderNode(document, child);
                }
            }
            case "Slide" -> {
                if (StringUtils.isNotBlank(str(props.get("eyebrow")))) {
                    document.add(muted(str(props.get("eyebrow")).toUpperCase()));
                }
                if (StringUtils.isNotBlank(str(props.get("title")))) {
                    document.add(heading(str(props.get("title")), 1));
                }
                if (StringUtils.isNotBlank(str(props.get("subtitle")))) {
                    document.add(body(str(props.get("subtitle"))));
                }
                for (Map<String, Object> child : children) {
                    renderNode(document, child);
                }
            }
            case "Divider" -> document.add(muted("—— " + str(props.get("label")) + " ——"));
            case "JsonDebug" -> document.add(mono(String.valueOf(props.getOrDefault("value", ""))));
            default -> {
                String text = primaryText(props);
                if (StringUtils.isNotBlank(text)) {
                    document.add(body(text));
                }
                for (Map<String, Object> child : children) {
                    renderNode(document, child);
                }
            }
        }
    }

    private static PdfPTable buildTable(Map<String, Object> props, List<Map<String, Object>> rows) {
        List<?> headers = props.get("headers") instanceof List<?> h ? h : List.of();
        int colCount = Math.max(1, headers.isEmpty()
                ? rows.stream().mapToInt(r -> children(r).size()).max().orElse(1)
                : headers.size());
        PdfPTable table = new PdfPTable(colCount);
        table.setWidthPercentage(100);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
        if (!headers.isEmpty()) {
            for (int c = 0; c < colCount; c++) {
                String val = c < headers.size() ? String.valueOf(headers.get(c)) : "";
                PdfPCell cell = new PdfPCell(new Phrase(val, headerFont));
                cell.setBackgroundColor(new Color(245, 245, 247));
                cell.setPadding(6);
                table.addCell(cell);
            }
        }
        for (Map<String, Object> rowNode : rows) {
            List<Map<String, Object>> cells = children(rowNode);
            for (int c = 0; c < colCount; c++) {
                String val = c < cells.size() ? primaryText(props(cells.get(c))) : "";
                PdfPCell cell = new PdfPCell(new Phrase(val, cellFont));
                cell.setPadding(6);
                table.addCell(cell);
            }
        }
        return table;
    }

    private static Paragraph heading(String text, int level) {
        float size = level == 1 ? 18f : level == 2 ? 15f : level == 3 ? 13f : 12f;
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, size);
        Paragraph p = new Paragraph(text == null ? "" : text, font);
        p.setSpacingBefore(8);
        p.setSpacingAfter(4);
        return p;
    }

    private static Paragraph body(String text) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 11);
        Paragraph p = new Paragraph(text == null ? "" : text, font);
        p.setSpacingAfter(4);
        p.setLeading(16);
        return p;
    }

    private static Paragraph muted(String text) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, new Color(100, 100, 110));
        Paragraph p = new Paragraph(text == null ? "" : text, font);
        p.setSpacingAfter(3);
        return p;
    }

    private static Paragraph mono(String text) {
        Font font = FontFactory.getFont(FontFactory.COURIER, 9);
        Paragraph p = new Paragraph(text == null ? "" : text, font);
        p.setSpacingAfter(4);
        return p;
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
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> cast((Map<?, ?>) item))
                .toList();
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

    private static int intVal(Object v, int def) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
