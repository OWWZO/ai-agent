package org.wwz.ai.domain.agent.reactor.data.sql.dialect;

import org.apache.calcite.sql.SqlDialect;
import org.apache.calcite.sql.dialect.ClickHouseSqlDialect;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ClickHouseSqlDialect2 extends ClickHouseSqlDialect {

    public static final SqlDialect DEFAULT = new ClickHouseSqlDialect2(DEFAULT_CONTEXT);

    public ClickHouseSqlDialect2(Context context) {
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
