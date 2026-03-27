package org.wwz.ai.domain.agent.reactor.data.sql;


import com.google.common.collect.ImmutableSet;
import org.apache.calcite.sql.type.SqlTypeName;

import java.util.Set;

public class SqlLiteralUtil {

    private static final Set<SqlTypeName> numericType =
            ImmutableSet.<SqlTypeName>builder()
                    .add(SqlTypeName.TINYINT)
                    .add(SqlTypeName.SMALLINT)
                    .add(SqlTypeName.INTEGER)
                    .add(SqlTypeName.BIGINT)
                    .add(SqlTypeName.DECIMAL)
                    .add(SqlTypeName.FLOAT)
                    .build();

    public static boolean isNumericValue(SqlTypeName typeName) {
        return numericType.contains(typeName);
    }
}
