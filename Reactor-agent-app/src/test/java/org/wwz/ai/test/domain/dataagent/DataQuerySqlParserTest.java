package org.wwz.ai.test.domain.dataagent;

import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.parser.SqlParser;
import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.rag.model.sql.SqlModel;
import org.wwz.ai.domain.agent.rag.sql.SqlParserUtils;

public class DataQuerySqlParserTest {

    @Test
    public void parsesMysqlAndClickhouseAndRejectsNonSelectStatements() throws Exception {
        String sql = "SELECT id FROM sales_data LIMIT 10";
        Assert.assertTrue(SqlParserUtils.isSelectSql(sql, "mysql"));
        Assert.assertTrue(SqlParserUtils.isSelectSql(sql, "clickhouse"));

        SqlNode mysqlNode = SqlParser.create(sql, SqlParserUtils.parserConfigWithoutQuoted("mysql")).parseStmt();
        Assert.assertEquals(SqlKind.ORDER_BY, mysqlNode.getKind());
        SqlModel parsed = SqlParserUtils.parseSelectSql(sql, "mysql");
        Assert.assertEquals("sales_data", parsed.getFromTable().getTableName());

        RuntimeException exception = Assert.assertThrows(RuntimeException.class,
                () -> SqlParserUtils.parseSelectSql("DELETE FROM sales_data", "mysql"));
        Assert.assertEquals("请检查sql是否正确", exception.getMessage());
    }
}
