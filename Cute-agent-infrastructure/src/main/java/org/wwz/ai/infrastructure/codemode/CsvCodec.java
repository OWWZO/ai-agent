package org.wwz.ai.infrastructure.codemode;

import org.wwz.ai.domain.agent.runtime.tool.codemode.CodeModeErrorCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV 解析与序列化（RFC 4180 语义）。
 * <p>
 * 由 Java 侧完成，脚本不需要也不能用 {@code split(",")}：
 * 字段内逗号、双引号转义、字段内换行、UTF-8 BOM、空值、缺失列都必须正确处理。
 * </p>
 */
public final class CsvCodec {

    private CsvCodec() {
    }

    public record Table(List<String> columns, List<Map<String, String>> rows) {
    }

    /**
     * 解析 CSV 文本。
     *
     * @param content    已解码的文本（BOM 在调用前剥离）
     * @param delimiter  分隔符
     * @param maxRows    最大数据行数（不含表头）
     * @param maxColumns 最大列数
     */
    public static Table parse(String content, char delimiter, int maxRows, int maxColumns) {
        // 多读一条用于判定"超限"，否则截断会把超限误判成刚好达标
        List<List<String>> records = parseRecords(content, delimiter, maxRows + 2, maxColumns);
        if (records.isEmpty()) {
            return new Table(List.of(), List.of());
        }
        if (records.size() - 1 > maxRows) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_FILE_TOO_LARGE,
                    "CSV 行数超过上限 " + maxRows);
        }
        List<String> header = records.get(0);
        List<String> columns = new ArrayList<>();
        for (int i = 0; i < header.size(); i++) {
            String raw = header.get(i);
            String name = raw == null ? "" : raw.trim();
            columns.add(name.isEmpty() ? "column_" + (i + 1) : dedupe(columns, name));
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (int r = 1; r < records.size(); r++) {
            List<String> record = records.get(r);
            // 单个空行（常见于文件尾）不生成数据行
            if (record.size() == 1 && (record.get(0) == null || record.get(0).isEmpty())) {
                continue;
            }
            if (rows.size() >= maxRows) {
                throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_FILE_TOO_LARGE,
                        "CSV 行数超过上限 " + maxRows);
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < columns.size(); c++) {
                row.put(columns.get(c), c < record.size() ? record.get(c) : "");
            }
            rows.add(row);
        }
        return new Table(List.copyOf(columns), rows);
    }

    /** 逐条记录解析：正确处理引号包裹、转义引号和字段内换行。 */
    static List<List<String>> parseRecords(String content, char delimiter, int maxRecords, int maxColumns) {
        List<List<String>> records = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return records;
        }
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean inQuotes = false;
        int length = content.length();
        for (int i = 0; i < length; i++) {
            char ch = content.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < length && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(ch);
                }
                continue;
            }
            if (ch == '"' && field.length() == 0 && !quoted) {
                inQuotes = true;
                quoted = true;
                continue;
            }
            if (ch == delimiter) {
                appendColumn(current, field, maxColumns);
                field.setLength(0);
                quoted = false;
                continue;
            }
            if (ch == '\n') {
                appendColumn(current, field, maxColumns);
                field.setLength(0);
                quoted = false;
                records.add(current);
                current = new ArrayList<>();
                if (records.size() >= maxRecords) {
                    break;
                }
                continue;
            }
            if (ch == '\r') {
                // \r\n 与裸 \r 都视为行结束
                if (i + 1 < length && content.charAt(i + 1) == '\n') {
                    i++;
                }
                appendColumn(current, field, maxColumns);
                field.setLength(0);
                quoted = false;
                records.add(current);
                current = new ArrayList<>();
                if (records.size() >= maxRecords) {
                    break;
                }
                continue;
            }
            field.append(ch);
        }
        if (field.length() > 0 || !current.isEmpty()) {
            appendColumn(current, field, maxColumns);
            records.add(current);
        }
        return records;
    }

    private static void appendColumn(List<String> row, StringBuilder field, int maxColumns) {
        if (maxColumns > 0 && row.size() >= maxColumns) {
            throw new CodeModeExecutionException(CodeModeErrorCode.CODE_MODE_FILE_TOO_LARGE,
                    "CSV 列数超过上限 " + maxColumns);
        }
        row.add(field.toString());
    }

    private static String dedupe(List<String> existing, String name) {
        if (!existing.contains(name)) {
            return name;
        }
        int index = 2;
        while (existing.contains(name + "_" + index)) {
            index++;
        }
        return name + "_" + index;
    }

    /** 剥离 UTF-8 BOM。 */
    public static String stripBom(String content) {
        if (content != null && !content.isEmpty() && content.charAt(0) == '\uFEFF') {
            return content.substring(1);
        }
        return content;
    }

    /**
     * 序列化：只在必要时加引号，引号内双写转义。
     */
    public static String serialize(List<String> columns, List<Map<String, Object>> rows, char delimiter) {
        StringBuilder out = new StringBuilder();
        writeRow(out, columns, delimiter);
        for (Map<String, Object> row : rows) {
            List<String> values = new ArrayList<>(columns.size());
            for (String column : columns) {
                Object value = row == null ? null : row.get(column);
                values.add(value == null ? "" : String.valueOf(value));
            }
            writeRow(out, values, delimiter);
        }
        return out.toString();
    }

    private static void writeRow(StringBuilder out, List<String> values, char delimiter) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(delimiter);
            }
            out.append(escape(values.get(i), delimiter));
        }
        out.append("\r\n");
    }

    private static String escape(String value, char delimiter) {
        if (value == null) {
            return "";
        }
        boolean needsQuote = value.indexOf(delimiter) >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuote) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
