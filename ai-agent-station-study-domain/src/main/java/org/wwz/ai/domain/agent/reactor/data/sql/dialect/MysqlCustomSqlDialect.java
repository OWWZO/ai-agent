package org.wwz.ai.domain.agent.reactor.data.sql.dialect;

import org.apache.calcite.sql.SqlDialect;
import org.apache.calcite.sql.dialect.MysqlSqlDialect;
import org.checkerframework.checker.nullness.qual.Nullable;

public class MysqlCustomSqlDialect extends MysqlSqlDialect {

    public static final SqlDialect DEFAULT = new MysqlCustomSqlDialect(DEFAULT_CONTEXT);

    public MysqlCustomSqlDialect(Context context) {
        super(context);
    }

    @Override
    public void quoteStringLiteral(StringBuilder buf, @Nullable String charsetName, String val) {
        buf.append(literalQuoteString);
        buf.append(val.replace(literalEndQuoteString, literalEscapedQuote));
        buf.append(literalEndQuoteString);
    }

    @Override
    public String quoteIdentifier(String val) {
        return super.quoteIdentifier(val);
    }
}

