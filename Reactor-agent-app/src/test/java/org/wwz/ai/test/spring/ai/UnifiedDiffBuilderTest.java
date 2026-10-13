package org.wwz.ai.test.spring.ai;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.tool.workspace.UnifiedDiffBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link UnifiedDiffBuilder} 的行号 / 上下文 / 多 hunk / 截断语义。
 *
 * <p>这里断言的是**具体行号**（例如 {@code @@ -44,7 +44,7 @@}）而不是「包含某段文本」——
 * Java 侧 0-based 索引与展示 1-based 行号之间最容易出 off-by-one，
 * 只断言文本会让整行错位这种 bug 静默通过。
 */
public class UnifiedDiffBuilderTest {

    /** 50 行文件，第 47 / 48 行被改（对齐 ZCode 截图场景）。 */
    private static String fixture50Lines() {
        StringBuilder sb = new StringBuilder();
        for (int line = 1; line <= 50; line++) {
            sb.append("第").append(line).append("行\n");
        }
        return sb.toString();
    }

    private static List<String> linesOf(String patch) {
        List<String> lines = new ArrayList<>();
        for (String line : patch.split("\n", -1)) {
            lines.add(line);
        }
        return lines;
    }

    private static long countHunkHeaders(String patch) {
        return linesOf(patch).stream().filter((line) -> line.startsWith("@@ ")).count();
    }

    @Test
    public void shouldEmitRealLineNumbersAndThreeContextLines() {
        String diff = UnifiedDiffBuilder.build(
                "tool-demo-50-lines.txt",
                fixture50Lines(),
                "第47行\n第48行",
                "第47行-新\n第48行-新",
                false);

        Assert.assertNotNull(diff);
        // 上文 44/45/46 + 变更 47/48 + 下文 49/50
        Assert.assertTrue(diff, diff.contains("@@ -44,7 +44,7 @@"));
        Assert.assertTrue(diff, diff.contains("\n 第44行\n"));
        Assert.assertTrue(diff, diff.contains("\n 第46行\n"));
        Assert.assertTrue(diff, diff.contains("\n-第47行\n"));
        Assert.assertTrue(diff, diff.contains("\n+第47行-新\n"));
        Assert.assertTrue(diff, diff.contains("\n 第49行\n"));
        Assert.assertTrue(diff, diff.contains("\n 第50行\n"));
        // 文件头用展示路径
        Assert.assertTrue(diff, diff.startsWith("--- a/tool-demo-50-lines.txt\n+++ b/tool-demo-50-lines.txt\n"));
        Assert.assertEquals(1, countHunkHeaders(diff));
    }

    @Test
    public void shouldClampContextAtFileHeadAndTail() {
        String content = "a\nb\nc\n";

        String head = UnifiedDiffBuilder.build("f.txt", content, "a", "A", false);
        Assert.assertNotNull(head);
        Assert.assertTrue(head, head.contains("@@ -1,3 +1,3 @@"));
        Assert.assertTrue(head, head.contains("\n-a\n+A\n"));
        Assert.assertTrue(head, head.contains("\n b\n"));
        Assert.assertTrue(head, head.contains("\n c\n"));

        String tail = UnifiedDiffBuilder.build("f.txt", content, "c", "C", false);
        Assert.assertNotNull(tail);
        Assert.assertTrue(tail, tail.contains("@@ -1,3 +1,3 @@"));
        Assert.assertTrue(tail, tail.contains("\n a\n"));
        Assert.assertTrue(tail, tail.contains("\n-c\n+C\n"));
    }

    @Test
    public void shouldHandleFileWithoutTrailingNewline() {
        String diff = UnifiedDiffBuilder.build("f.txt", "a\nb", "b", "B", false);
        Assert.assertNotNull(diff);
        Assert.assertTrue(diff, diff.contains("@@ -1,2 +1,2 @@"));
        Assert.assertTrue(diff, diff.contains("\n-b\n+B\n"));
    }

    @Test
    public void shouldExpandPartialLineMatchToWholeLines() {
        // old_string 只覆盖 "beta" 中间两个字符，不对齐行边界。
        String diff = UnifiedDiffBuilder.build("f.txt", "alpha\nbeta\ngamma\n", "et", "ET", false);
        Assert.assertNotNull(diff);
        Assert.assertTrue(diff, diff.contains("@@ -1,3 +1,3 @@"));
        // 整行被扩进来：删旧整行、加新整行，行内其余字符也一并呈现
        Assert.assertTrue(diff, diff.contains("\n-beta\n"));
        Assert.assertTrue(diff, diff.contains("\n+bETa\n"));
        Assert.assertTrue(diff, diff.contains("\n alpha\n"));
        Assert.assertTrue(diff, diff.contains("\n gamma\n"));
    }

    @Test
    public void shouldShiftNewLineNumbersAcrossMultipleHunks() {
        // 40 行，第 5 行与第 30 行都是 TOKEN；replace_all 后每处膨胀一行。
        StringBuilder content = new StringBuilder();
        for (int line = 1; line <= 40; line++) {
            content.append(line == 5 || line == 30 ? "TOKEN" : "L" + line).append('\n');
        }

        String diff = UnifiedDiffBuilder.build(
                "f.txt", content.toString(), "TOKEN", "TOKEN-A\nTOKEN-B", true);

        Assert.assertNotNull(diff);
        Assert.assertEquals(2, countHunkHeaders(diff));
        // 第一处：上文 L2/L3/L4，下文 L6/L7/L8 → 旧 7 行、新 8 行（多了 1 行）
        Assert.assertTrue(diff, diff.contains("@@ -2,7 +2,8 @@"));
        // 第二处：文件真实行号 27 起，但前面已插入 1 行 → 新文件侧 28 起
        Assert.assertTrue(diff, diff.contains("@@ -27,7 +28,8 @@"));
        Assert.assertTrue(diff, diff.contains("\n L29\n"));
        Assert.assertTrue(diff, diff.contains("\n L31\n"));
        Assert.assertTrue(diff, diff.contains("\n-TOKEN\n"));
        Assert.assertTrue(diff, diff.contains("\n+TOKEN-A\n+TOKEN-B\n"));
    }

    @Test
    public void shouldMergeNearbyChangesIntoSingleHunk() {
        // 相邻两处变更（间隔 1 行 ≤ 2 × context）应并入同一个 hunk。
        String diff = UnifiedDiffBuilder.build("f.txt", "x\ny\nz\n", "x", "X", true);
        // 只有一处匹配，这里验证的是单 hunk 的形态没有被合并逻辑打散
        Assert.assertNotNull(diff);
        Assert.assertEquals(1, countHunkHeaders(diff));

        String merged = UnifiedDiffBuilder.build("f.txt", "a\nT\nb\nT\nc\n", "T", "T2", true);
        Assert.assertNotNull(merged);
        Assert.assertEquals(1, countHunkHeaders(merged));
        Assert.assertTrue(merged, merged.contains("@@ -1,5 +1,5 @@"));
    }

    @Test
    public void shouldTruncateHunksWithSelfConsistentMarker() {
        // 60 处互不相邻的变更（每处间隔 7 行 > 2 × context），必然超过 hunk 上限。
        StringBuilder content = new StringBuilder();
        for (int group = 0; group < 60; group++) {
            for (int filler = 0; filler < 7; filler++) {
                content.append("FILLER_").append(group).append('_').append(filler).append('\n');
            }
            content.append("TOKEN\n");
        }

        String diff = UnifiedDiffBuilder.build("big.txt", content.toString(), "TOKEN", "TOKEN2", true);

        Assert.assertNotNull(diff);
        Assert.assertEquals(UnifiedDiffBuilder.MAX_HUNKS, countHunkHeaders(diff));
        Assert.assertTrue(diff, diff.contains("more hunks omitted)"));

        // 截断说明行必须是最后一条 hunk 内部的 context 行：hunk 声明的行数要与实际行数一致，
        // 否则 @pierre/diffs 会报 hunk line count mismatch 并整个丢弃该 hunk。
        List<String> lines = linesOf(diff);
        int lastHeader = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("@@ ")) {
                lastHeader = i;
            }
        }
        Assert.assertTrue(lastHeader > 0);
        int declared = Integer.parseInt(
                lines.get(lastHeader).replaceAll("^@@ -\\d+,(\\d+) \\+\\d+,(\\d+) @@$", "$1"));
        int bodyLines = 0;
        for (int i = lastHeader + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty()) {
                continue;
            }
            char kind = line.charAt(0);
            if (kind != '+') {
                bodyLines++;
            }
        }
        Assert.assertEquals(declared, bodyLines);
        // patch 以换行结尾，split 的末元素是空串，取最后一个非空行。
        String lastNonEmpty = "";
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (!lines.get(i).isEmpty()) {
                lastNonEmpty = lines.get(i);
                break;
            }
        }
        Assert.assertTrue(lastNonEmpty, lastNonEmpty.contains("more hunks omitted)"));
    }

    @Test
    public void shouldReturnNullForUnsupportedInput() {
        Assert.assertNull(UnifiedDiffBuilder.build("f.txt", "a\n", "", "x", false));
        Assert.assertNull(UnifiedDiffBuilder.build("f.txt", "a\n", "a", "a", false));
        Assert.assertNull(UnifiedDiffBuilder.build("f.txt", "a\n", "missing", "x", false));
        Assert.assertNull(UnifiedDiffBuilder.build("f.txt", null, "a", "x", false));
    }

    @Test
    public void shouldTreatReplaceAllFalseAsSingleOccurrence() {
        String diff = UnifiedDiffBuilder.build("f.txt", "T\nT\n", "T", "T2", false);
        Assert.assertNotNull(diff);
        Assert.assertEquals(1, countHunkHeaders(diff));
        Assert.assertTrue(diff, diff.contains("\n-T\n+T2\n"));
        // 第二处未被替换，仍以上下文行原样出现
        Assert.assertTrue(diff, diff.contains("\n T\n"));
    }
}
