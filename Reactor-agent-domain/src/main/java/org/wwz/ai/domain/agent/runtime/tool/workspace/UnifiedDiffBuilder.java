package org.wwz.ai.domain.agent.runtime.tool.workspace;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把「一次字符串替换」转成 unified diff。
 *
 * <p>设计前提：<b>不做全文件 diff</b>。调用方（{@link WorkspaceEditTool}）已经知道替换点的
 * 位置与替换前后的完整内容，因此只需要：
 * <ol>
 *   <li>把替换区间向两侧扩到整行边界（{@code old_string} 可能只覆盖一行的中间一段）；</li>
 *   <li>只对这一段片段做行级 LCS（片段很短，O(n·m) 无压力）；</li>
 *   <li>取前后各 {@link #CONTEXT_LINES} 行上下文，拼成 hunk。</li>
 * </ol>
 *
 * <p>产出形态是 unified diff <b>字符串</b>（而不是 ZCode 那种结构化数组）：前端两个消费点
 * ——内联轻量预览 {@code patchPreview.ts} 与右侧 {@code @pierre/diffs} 的 {@code PatchDiff}
 * ——都只吃字符串，中间结构在前端没有被二次消费。
 *
 * <p>因为做了「扩到整行」，所以 hunk 行号是<b>文件真实行号</b>（1-based），而不是渲染序号。
 * 这是与前端 {@code toolDiff.ts buildEditDiffCode()} 的关键区别：那边只能拿
 * {@code old_string}/{@code new_string} 两个片段做 LCS，既没有上下文也不知道落在第几行。
 */
public final class UnifiedDiffBuilder {

    /** 上下文行数（git 与 ZCode 默认 3 行）。 */
    public static final int CONTEXT_LINES = 3;

    /** 相邻变更之间的未改动行数 ≤ 该值时并入同一个 hunk（标准做法：2 × context）。 */
    private static final int HUNK_MERGE_GAP = CONTEXT_LINES * 2;

    /** 单个 patch 最多输出的 hunk 数，超出则截断并标注。 */
    public static final int MAX_HUNKS = 50;

    /** 单个 patch 的字符上限，超出则截断并标注。 */
    public static final int MAX_PATCH_CHARS = 120_000;

    /** 片段 LCS 的行数 / 单元格上限，超出直接放弃（调用方退回前端 LCS 合成）。 */
    private static final int MAX_BLOCK_ROWS = 5_000;
    private static final long MAX_BLOCK_CELLS = 1_000_000L;

    private UnifiedDiffBuilder() {
    }

    /**
     * @param path       展示用路径，写进 diff 头的 {@code a/} {@code b/} 前缀
     * @param original   替换前的完整文件内容
     * @param oldString  被替换的原文
     * @param newString  替换后的新文本
     * @param replaceAll 是否替换全部匹配（true 时每处匹配各出一个 hunk）
     * @return unified diff 字符串；无法生成时返回 {@code null}（调用方应退回旧逻辑）
     */
    public static String build(String path,
                               String original,
                               String oldString,
                               String newString,
                               boolean replaceAll) {
        if (original == null || oldString == null || newString == null) {
            return null;
        }
        if (oldString.isEmpty() || oldString.equals(newString)) {
            return null;
        }

        List<Integer> positions = findOccurrences(original, oldString, replaceAll ? Integer.MAX_VALUE : 1);
        if (positions.isEmpty()) {
            return null;
        }

        // 与 WorkspaceEditTool 的替换语义严格一致，保证 hunk 描述的就是真正写盘的内容。
        String updated = replaceAll
                ? original.replace(oldString, newString)
                : original.replaceFirst(Pattern.quote(oldString), Matcher.quoteReplacement(newString));

        List<String> originalLines = splitLines(original);
        int lengthDelta = newString.length() - oldString.length();

        List<EditBlock> blocks = new ArrayList<>(positions.size());
        for (int i = 0; i < positions.size(); i++) {
            int oldPos = positions.get(i);
            // replace_all 时第 i 处匹配在 updated 中的偏移（前面每处都整体位移了 lengthDelta）。
            int newPos = oldPos + i * lengthDelta;
            EditBlock block = buildBlock(original, updated, originalLines, oldPos, newPos,
                    oldString.length(), newString.length());
            if (block == null) {
                // 片段过大 / 越界等异常形态：整体放弃，交给前端 LCS 兜底，不要产出半截 patch。
                return null;
            }
            blocks.add(block);
        }

        List<List<EditBlock>> groups = groupBlocks(blocks);

        List<Hunk> hunks = new ArrayList<>(groups.size());
        int cumulativeDelta = 0;
        for (List<EditBlock> group : groups) {
            Hunk hunk = buildHunk(originalLines, group, cumulativeDelta);
            cumulativeDelta += hunk.newCount - hunk.oldCount;
            hunks.add(hunk);
        }

        String header = "--- a/" + (path == null || path.isEmpty() ? "file" : path) + "\n"
                + "+++ b/" + (path == null || path.isEmpty() ? "file" : path) + "\n";
        return serialize(header, hunks);
    }

    // ------------------------------------------------------------------ 定位与分组

    /** 与 {@code String.replace} 同语义的非重叠匹配扫描。 */
    private static List<Integer> findOccurrences(String content, String target, int limit) {
        List<Integer> positions = new ArrayList<>();
        int index = 0;
        while (positions.size() < limit) {
            int found = content.indexOf(target, index);
            if (found < 0) {
                break;
            }
            positions.add(found);
            index = found + Math.max(1, target.length());
        }
        return positions;
    }

    /**
     * 把一个替换点扩成「整行块」，并对块内做行级 LCS。
     *
     * <p>⚠️ {@code old_string} 不一定对齐行边界（模型可能只选了一行中间的一段），
     * 必须把区间扩到整行，否则 hunk 首尾行会被截断。
     */
    private static EditBlock buildBlock(String original,
                                        String updated,
                                        List<String> originalLines,
                                        int oldPos,
                                        int newPos,
                                        int oldLength,
                                        int newLength) {
        int oldLineStart = lastLineStart(original, oldPos);
        int oldLineEnd = lineEndInclusive(original, oldPos + oldLength - 1);
        int newLineStart = lastLineStart(updated, newPos);
        int newLineEnd = lineEndInclusive(updated, newPos + newLength - 1);

        if (oldLineStart < 0 || oldLineEnd > original.length()
                || oldLineStart >= oldLineEnd
                || newLineStart < 0 || newLineEnd > updated.length()
                || newLineStart >= newLineEnd) {
            return null;
        }

        List<String> oldBlockLines = splitLines(original.substring(oldLineStart, oldLineEnd));
        List<String> newBlockLines = splitLines(updated.substring(newLineStart, newLineEnd));

        int n = oldBlockLines.size();
        int m = newBlockLines.size();
        if (n == 0 || m == 0 || n > MAX_BLOCK_ROWS || m > MAX_BLOCK_ROWS) {
            return null;
        }
        if ((long) (n + 1) * (m + 1) > MAX_BLOCK_CELLS) {
            return null;
        }

        EditBlock block = new EditBlock();
        block.oldStart = countNewlines(original, 0, oldLineStart);
        block.oldEnd = block.oldStart + n;
        block.ops = diffLines(oldBlockLines, newBlockLines);
        if (block.oldEnd > originalLines.size()) {
            return null;
        }
        return block;
    }

    /** 变更块之间未改动行数不超过 {@link #HUNK_MERGE_GAP} 时并入同一个 hunk。 */
    private static List<List<EditBlock>> groupBlocks(List<EditBlock> blocks) {
        List<List<EditBlock>> groups = new ArrayList<>();
        List<EditBlock> current = null;
        for (EditBlock block : blocks) {
            if (current != null) {
                EditBlock previous = current.get(current.size() - 1);
                if (block.oldStart - previous.oldEnd <= HUNK_MERGE_GAP) {
                    current.add(block);
                    continue;
                }
            }
            current = new ArrayList<>();
            current.add(block);
            groups.add(current);
        }
        return groups;
    }

    // ------------------------------------------------------------------ hunk 组装

    private static Hunk buildHunk(List<String> originalLines, List<EditBlock> group, int cumulativeDelta) {
        EditBlock first = group.get(0);
        EditBlock last = group.get(group.size() - 1);

        int contextStart = Math.max(0, first.oldStart - CONTEXT_LINES);
        int contextEnd = Math.min(originalLines.size(), last.oldEnd + CONTEXT_LINES);

        List<String> body = new ArrayList<>();
        for (int i = contextStart; i < first.oldStart; i++) {
            body.add(" " + originalLines.get(i));
        }

        int cursor = first.oldStart;
        for (EditBlock block : group) {
            // 同 hunk 内两个变更块之间的行是未改动的，按上下文行输出。
            for (int i = cursor; i < block.oldStart; i++) {
                body.add(" " + originalLines.get(i));
            }
            for (EditOp op : block.ops) {
                body.add(op.kind + op.text);
            }
            cursor = block.oldEnd;
        }

        for (int i = last.oldEnd; i < contextEnd; i++) {
            body.add(" " + originalLines.get(i));
        }

        int oldCount = 0;
        int newCount = 0;
        for (String line : body) {
            if (line.isEmpty()) {
                continue;
            }
            char kind = line.charAt(0);
            if (kind != '+') {
                oldCount++;
            }
            if (kind != '-') {
                newCount++;
            }
        }

        Hunk hunk = new Hunk();
        hunk.oldStart = contextStart + 1;
        hunk.newStart = hunk.oldStart + cumulativeDelta;
        hunk.oldCount = oldCount;
        hunk.newCount = newCount;
        hunk.body = body;
        return hunk;
    }

    private static String serialize(String header, List<Hunk> hunks) {
        List<String> parts = new ArrayList<>(hunks.size());
        int chars = header.length();
        for (Hunk hunk : hunks) {
            if (parts.size() >= MAX_HUNKS || chars > MAX_PATCH_CHARS) {
                break;
            }
            String text = hunk.render();
            parts.add(text);
            chars += text.length();
        }

        int omitted = hunks.size() - parts.size();
        if (omitted > 0 && !parts.isEmpty()) {
            // ⚠️ 截断说明必须是**最后一条 hunk 内部**的 context 行并同步加计数：
            // 裸挂在 hunk 之外会让 @pierre/diffs 报「hunk line count mismatch」并整个丢弃该 hunk。
            int lastIndex = parts.size() - 1;
            parts.set(lastIndex, hunks.get(lastIndex).renderWithTrailingNote(
                    " ... (" + omitted + " more hunks omitted)"));
        }
        return header + String.join("", parts);
    }

    // ------------------------------------------------------------------ 行级 LCS

    private static List<EditOp> diffLines(List<String> oldLines, List<String> newLines) {
        int n = oldLines.size();
        int m = newLines.size();
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                dp[i][j] = oldLines.get(i - 1).equals(newLines.get(j - 1))
                        ? dp[i - 1][j - 1] + 1
                        : Math.max(dp[i - 1][j], dp[i][j - 1]);
            }
        }

        List<EditOp> reversed = new ArrayList<>();
        int i = n;
        int j = m;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && oldLines.get(i - 1).equals(newLines.get(j - 1))) {
                reversed.add(new EditOp(' ', oldLines.get(i - 1)));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                reversed.add(new EditOp('+', newLines.get(j - 1)));
                j--;
            } else {
                reversed.add(new EditOp('-', oldLines.get(i - 1)));
                i--;
            }
        }

        List<EditOp> ops = new ArrayList<>(reversed.size());
        for (int index = reversed.size() - 1; index >= 0; index--) {
            ops.add(reversed.get(index));
        }
        return ops;
    }

    // ------------------------------------------------------------------ 文本工具

    private static List<String> splitLines(String content) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        int length = content.length();
        while (start < length) {
            int newline = content.indexOf('\n', start);
            if (newline < 0) {
                lines.add(content.substring(start));
                break;
            }
            lines.add(content.substring(start, newline));
            start = newline + 1;
        }
        return lines;
    }

    /** {@code index} 所在行的行首下标。 */
    private static int lastLineStart(String content, int index) {
        if (index < 0) {
            return 0;
        }
        return content.lastIndexOf('\n', index) + 1;
    }

    /** 包含 {@code index} 的那一行之后（含换行符）的下标；无换行则到内容末尾。 */
    private static int lineEndInclusive(String content, int index) {
        if (index < 0) {
            return content.length();
        }
        int newline = content.indexOf('\n', index);
        return newline < 0 ? content.length() : newline + 1;
    }

    private static int countNewlines(String content, int from, int to) {
        int count = 0;
        for (int i = from; i < to && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ 内部结构

    private static final class EditOp {
        final char kind;
        final String text;

        EditOp(char kind, String text) {
            this.kind = kind;
            this.text = text;
        }
    }

    private static final class EditBlock {
        /** 0-based 起始行索引（含） */
        int oldStart;
        /** 0-based 结束行索引（不含） */
        int oldEnd;
        List<EditOp> ops = List.of();
    }

    private static final class Hunk {
        int oldStart;
        int newStart;
        int oldCount;
        int newCount;
        List<String> body = List.of();

        String render() {
            return renderWithTrailingNote(null);
        }

        String renderWithTrailingNote(String note) {
            // 说明行以空格开头 = context 行，两侧计数同步 +1，保证 hunk 自洽可解析。
            int oldLines = note == null ? oldCount : oldCount + 1;
            int newLines = note == null ? newCount : newCount + 1;
            StringBuilder sb = new StringBuilder();
            sb.append("@@ -").append(oldStart).append(',').append(oldLines)
                    .append(" +").append(newStart).append(',').append(newLines).append(" @@\n");
            for (String line : body) {
                sb.append(line).append('\n');
            }
            if (note != null) {
                sb.append(note).append('\n');
            }
            return sb.toString();
        }
    }
}
