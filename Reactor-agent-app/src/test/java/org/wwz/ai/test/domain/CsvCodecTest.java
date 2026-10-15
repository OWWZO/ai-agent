package org.wwz.ai.test.domain;

import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.infrastructure.codemode.CsvCodec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV 解析 / 序列化：字段内逗号、引号转义、字段内换行、BOM、缺失列、列顺序。
 */
public class CsvCodecTest {

    @Test
    public void parsesQuotedCommasQuotesAndNewlines() {
        String csv = "name,note,price\r\n"
                + "\"A, Inc.\",\"say \"\"hi\"\"\",10\r\n"
                + "B,\"line1\nline2\",20\r\n";
        CsvCodec.Table table = CsvCodec.parse(CsvCodec.stripBom(csv), ',', 100, 50);

        Assert.assertEquals(List.of("name", "note", "price"), table.columns());
        Assert.assertEquals(2, table.rows().size());
        Assert.assertEquals("A, Inc.", table.rows().get(0).get("name"));
        Assert.assertEquals("say \"hi\"", table.rows().get(0).get("note"));
        Assert.assertEquals("line1\nline2", table.rows().get(1).get("note"));
    }

    @Test
    public void stripsUtf8BomFromFirstColumn() {
        CsvCodec.Table table = CsvCodec.parse(CsvCodec.stripBom("\uFEFFname,price\r\nA,1\r\n"), ',', 100, 50);
        Assert.assertEquals(List.of("name", "price"), table.columns());
        Assert.assertEquals("A", table.rows().get(0).get("name"));
    }

    @Test
    public void fillsMissingColumnsWithEmptyString() {
        CsvCodec.Table table = CsvCodec.parse("a,b,c\r\n1,2\r\n", ',', 100, 50);
        Assert.assertEquals("", table.rows().get(0).get("c"));
    }

    @Test
    public void keepsColumnOrderAndRenamesDuplicates() {
        CsvCodec.Table table = CsvCodec.parse("a,a,,b\r\n1,2,3,4\r\n", ',', 100, 50);
        Assert.assertEquals(List.of("a", "a_2", "column_3", "b"), table.columns());
    }

    @Test
    public void serializesAndRoundTripsComplexValues() {
        List<String> columns = List.of("name", "note", "price");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "A, Inc.");
        row.put("note", "say \"hi\"\nnext");
        row.put("price", 10);
        String csv = CsvCodec.serialize(columns, List.of(row), ',');

        CsvCodec.Table table = CsvCodec.parse(csv, ',', 100, 50);
        Assert.assertEquals("A, Inc.", table.rows().get(0).get("name"));
        Assert.assertEquals("say \"hi\"\nnext", table.rows().get(0).get("note"));
        Assert.assertEquals("10", table.rows().get(0).get("price"));
    }

    @Test
    public void supportsCustomDelimiter() {
        CsvCodec.Table table = CsvCodec.parse("a;b\r\n1;2\r\n", ';', 100, 50);
        Assert.assertEquals(List.of("a", "b"), table.columns());
        Assert.assertEquals("2", table.rows().get(0).get("b"));
    }

    @Test
    public void enforcesRowAndColumnLimits() {
        try {
            CsvCodec.parse("a,b\r\n1,2\r\n3,4\r\n", ',', 1, 50);
            Assert.fail("expected row limit");
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("行数"));
        }
        try {
            CsvCodec.parse("a,b,c\r\n1,2,3\r\n", ',', 100, 2);
            Assert.fail("expected column limit");
        } catch (RuntimeException e) {
            Assert.assertTrue(e.getMessage().contains("列数"));
        }
    }
}
