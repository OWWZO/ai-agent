package org.wwz.ai.test.domain;

import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.parser.SqlParser;
import org.junit.Assert;
import org.junit.Test;
import org.wwz.ai.domain.agent.rag.model.sql.SqlModel;
import org.wwz.ai.domain.agent.rag.sql.SqlParserUtils;

public class SqlParserUtilsTest {

    private static final String CTE_SQL = "WITH `monthly_sales` AS ("
            + "SELECT DATE_FORMAT(`order_date`, '%Y-%m') AS `month`, "
            + "SUM(CAST(`quantity` AS DECIMAL(20,4))) AS `monthly_quantity` "
            + "FROM `t_qtpbgamccmrctthlurauclckq` "
            + "WHERE `order_date` >= '2024-01-01' AND `order_date` < '2025-01-01' "
            + "GROUP BY DATE_FORMAT(`order_date`, '%Y-%m')), "
            + "`monthly_trend` AS ("
            + "SELECT `month`, `monthly_quantity`, "
            + "LAG(`monthly_quantity`) OVER (ORDER BY `month`) AS `previous_month_quantity` "
            + "FROM `monthly_sales`) "
            + "SELECT `month`, `monthly_quantity`, `previous_month_quantity`, "
            + "`monthly_quantity` - `previous_month_quantity` AS `month_over_month_change`, "
            + "CASE WHEN `previous_month_quantity` IS NULL OR `previous_month_quantity` = 0 "
            + "THEN NULL ELSE (`monthly_quantity` - `previous_month_quantity`) / `previous_month_quantity` END "
            + "AS `month_over_month_growth_rate` FROM `monthly_trend` ORDER BY `month`";

    @Test
    public void parsesCteQueryAndResolvesUnderlyingModelTable() throws Exception {
        SqlNode node = SqlParser.create(CTE_SQL, SqlParserUtils.parserConfigWithoutQuoted("mysql")).parseStmt();

        Assert.assertEquals(SqlKind.ORDER_BY, node.getKind());
        Assert.assertTrue(SqlParserUtils.isSelectSql(CTE_SQL, "mysql"));

        SqlModel sqlModel = SqlParserUtils.parseSelectSql(CTE_SQL, "mysql");
        Assert.assertNotNull(sqlModel.getFromTable());
        Assert.assertTrue(sqlModel.getFromTable().getTableName().contains("t_qtpbgamccmrctthlurauclckq"));
        Assert.assertEquals(5, sqlModel.getColumnList().size());
    }

    @Test
    public void parsesSupportedDialectsAndRejectsNonSelectStatements() throws Exception {
        Assert.assertTrue(SqlParserUtils.isSelectSql("SELECT id FROM sales_data LIMIT 10", "mysql"));
        Assert.assertTrue(SqlParserUtils.isSelectSql("SELECT id FROM sales_data LIMIT 10", "clickhouse"));

        RuntimeException exception = Assert.assertThrows(RuntimeException.class,
                () -> SqlParserUtils.parseSelectSql("DELETE FROM sales_data", "mysql"));
        Assert.assertEquals("请检查sql是否正确", exception.getMessage());
    }
}
