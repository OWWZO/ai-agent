package org.wwz.ai.test.domain.openui;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiRepairIntent;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiIntentParser;

import java.util.List;

/**
 * SEARCH/REPLACE 块解析（plans/038 §11 第 3 步）：容忍废话与标记偏差，残缺块整块丢弃。
 */
public class OpenUiIntentParserTest {

    @Test
    public void shouldParseWellFormedBlocks() {
        String output = """
                修复如下：
                <<<<<<< SEARCH
                t = Text($display + "", "3xl")
                =======
                t = Text($display + "", "lg")
                >>>>>>> REPLACE
                """;
        List<OpenUiRepairIntent> blocks = OpenUiIntentParser.parse(output);
        Assert.assertEquals(1, blocks.size());
        Assert.assertEquals("t = Text($display + \"\", \"3xl\")", blocks.get(0).searchText());
        Assert.assertEquals("t = Text($display + \"\", \"lg\")", blocks.get(0).replaceText());
    }

    @Test
    public void shouldParseMultipleBlocksAndSkipProse() {
        String output = """
                好的，我发现了两个问题。
                <<<<<<< SEARCH
                a = FancyCard("x")
                =======
                a = Card([Title("x")])
                >>>>>>> REPLACE
                另外这一行也有问题：
                <<<<<<< SEARCH
                root = Col([a])
                =======
                root = Col([a, b])
                >>>>>>> REPLACE
                希望有帮助。
                """;
        List<OpenUiRepairIntent> blocks = OpenUiIntentParser.parse(output);
        Assert.assertEquals(2, blocks.size());
        Assert.assertEquals("a = FancyCard(\"x\")", blocks.get(0).searchText());
        Assert.assertEquals("root = Col([a, b])", blocks.get(1).replaceText());
    }

    @Test
    public void shouldTolerantMarkerVariants() {
        // 标记两侧空白、`<`/`>` 数量偏差、代码围栏包裹。
        String output = """
                ```
                 <<<<< SEARCH
                a = Title("x", "lg")
                 =======
                a = Title("x", "xl")
                 >>>>>>> REPLACE
                ```
                """;
        List<OpenUiRepairIntent> blocks = OpenUiIntentParser.parse(output);
        Assert.assertEquals(1, blocks.size());
        Assert.assertEquals("a = Title(\"x\", \"lg\")", blocks.get(0).searchText());
    }

    @Test
    public void shouldKeepEmptyReplaceAsDeletion() {
        String output = """
                <<<<<<< SEARCH
                broken = Caption("待删除")
                =======
                >>>>>>> REPLACE
                """;
        List<OpenUiRepairIntent> blocks = OpenUiIntentParser.parse(output);
        Assert.assertEquals(1, blocks.size());
        Assert.assertTrue(blocks.get(0).replaceText().isEmpty());
    }

    @Test
    public void shouldPreserveInnerBlankLinesInSearch() {
        String output = """
                <<<<<<< SEARCH
                $op = ""

                status = Caption($op)
                =======
                status = Caption("Ready")
                >>>>>>> REPLACE
                """;
        List<OpenUiRepairIntent> blocks = OpenUiIntentParser.parse(output);
        Assert.assertEquals(1, blocks.size());
        Assert.assertEquals("$op = \"\"\n\nstatus = Caption($op)", blocks.get(0).searchText());
    }

    @Test
    public void shouldDropIncompleteBlockWithoutTailMarker() {
        // EOF 时块没闭合 → 整块丢弃，不抛异常。
        String output = """
                <<<<<<< SEARCH
                a = Title("x")
                =======
                a = Title("y")
                """;
        Assert.assertTrue(OpenUiIntentParser.parse(output).isEmpty());
    }

    @Test
    public void shouldDropBlockWithEmptySearch() {
        String output = """
                <<<<<<< SEARCH
                =======
                replacement without anchor
                >>>>>>> REPLACE
                """;
        Assert.assertTrue(OpenUiIntentParser.parse(output).isEmpty());
    }

    @Test
    public void shouldReturnEmptyForGarbage() {
        Assert.assertTrue(OpenUiIntentParser.parse("这是一段没有任何块的普通解释文字。").isEmpty());
        Assert.assertTrue(OpenUiIntentParser.parse("").isEmpty());
        Assert.assertTrue(OpenUiIntentParser.parse(null).isEmpty());
    }

    @Test
    public void shouldNotTreatOrdinaryEqualsLineAsSeparator() {
        // DSL 里的表达式赋值不能被误认成分隔标记。
        String output = """
                <<<<<<< SEARCH
                $op = ""
                =======
                $op = "Ready"
                >>>>>>> REPLACE
                """;
        List<OpenUiRepairIntent> blocks = OpenUiIntentParser.parse(output);
        Assert.assertEquals(1, blocks.size());
        Assert.assertEquals("$op = \"\"", blocks.get(0).searchText());
    }
}
