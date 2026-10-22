package org.wwz.ai.domain.agent.runtime.openui;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 把模型的自由输出解析成 SEARCH/REPLACE 块列表（plans/038 §5 / §11 第 3 步）。
 *
 * <p>解析策略是「容忍」的：块外的解释、废话一律丢弃；标记行允许两侧空白与标记符号
 * 数量的小幅偏差（edit tool 的常见输出形态）。结构残缺（缺分隔或结尾标记）的块整块丢弃，
 * 不抛异常 —— 解析失败等于"模型这轮没给块"，由调用方决定重试。</p>
 */
public final class OpenUiIntentParser {

    private OpenUiIntentParser() {
    }

    public static List<OpenUiRepairIntent> parse(String output) {
        if (StringUtils.isBlank(output)) {
            return List.of();
        }
        List<OpenUiRepairIntent> blocks = new ArrayList<>();
        String[] lines = output.replace("\r\n", "\n").split("\n", -1);

        Phase phase = Phase.OUTSIDE;
        StringBuilder search = null;
        StringBuilder replace = null;

        for (String line : lines) {
            switch (phase) {
                case OUTSIDE -> {
                    if (isHeadMarker(line)) {
                        phase = Phase.IN_SEARCH;
                        search = new StringBuilder();
                        replace = new StringBuilder();
                    }
                    // 块外内容（解释、代码围栏、废话）一律丢弃
                }
                case IN_SEARCH -> {
                    if (isSeparator(line)) {
                        phase = Phase.IN_REPLACE;
                    } else {
                        appendLine(search, line);
                    }
                }
                case IN_REPLACE -> {
                    if (isTailMarker(line)) {
                        addBlock(blocks, search, replace);
                        phase = Phase.OUTSIDE;
                        search = null;
                        replace = null;
                    } else {
                        appendLine(replace, line);
                    }
                }
                default -> throw new IllegalStateException("未知解析阶段: " + phase);
            }
        }
        // EOF 时块还没闭合（缺结尾标记）→ 结构残缺，整块丢弃。
        return List.copyOf(blocks);
    }

    private static void addBlock(List<OpenUiRepairIntent> blocks, StringBuilder search, StringBuilder replace) {
        String searchText = StringUtils.stripEnd(search.toString(), "\n");
        String replaceText = StringUtils.stripEnd(replace.toString(), "\n");
        // 空 SEARCH 的块没有定位能力，直接丢弃。
        if (StringUtils.isBlank(searchText)) {
            return;
        }
        blocks.add(new OpenUiRepairIntent(searchText, replaceText));
    }

    private static void appendLine(StringBuilder builder, String line) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    /** `<<<<<<< SEARCH`：容忍 `<` 数量偏差（≥5）与两侧空白，但必须带 SEARCH 字样（防止与普通行混淆）。 */
    private static boolean isHeadMarker(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("<<<<<") && trimmed.toUpperCase(Locale.ROOT).contains("SEARCH");
    }

    /** `=======`：容忍 `=` 数量 ≥7，但不允许夹其他字符（区分于普通分隔线注释）。 */
    private static boolean isSeparator(String line) {
        String trimmed = line.trim();
        return trimmed.length() >= 7 && trimmed.chars().allMatch(ch -> ch == '=');
    }

    /** `>>>>>>> REPLACE`：容忍 `>` 数量偏差（≥5）与两侧空白，但必须带 REPLACE 字样。 */
    private static boolean isTailMarker(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith(">>>>>") && trimmed.toUpperCase(Locale.ROOT).contains("REPLACE");
    }

    private enum Phase {
        OUTSIDE,
        IN_SEARCH,
        IN_REPLACE
    }
}
