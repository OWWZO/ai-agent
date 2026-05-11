package org.wwz.ai.domain.agent.reactor.data.sql;

import com.clickhouse.data.ClickHouseAggregateFunction;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.util.SqlBasicVisitor;
import org.apache.commons.collections4.CollectionUtils;

import java.util.HashSet;
import java.util.Set;

public class HasAggVisitor extends SqlBasicVisitor<Boolean> {

    public static final String COUNT_DISTINCT = "count_distinct";

    private boolean isAgg = false;
    public static final Set<String> AGG_NAME_SET = new HashSet<>();

    static {
        for (SqlKind sqlKind : SqlKind.AGGREGATE) {
            AGG_NAME_SET.add(sqlKind.name().toLowerCase());
        }
        for (ClickHouseAggregateFunction ckAgg : ClickHouseAggregateFunction.class.getEnumConstants()) {
            AGG_NAME_SET.add(ckAgg.name().toLowerCase());
            if(CollectionUtils.isNotEmpty(ckAgg.getAliases())){
                AGG_NAME_SET.addAll(ckAgg.getAliases());
            }
        }
        AGG_NAME_SET.add(COUNT_DISTINCT);
        AGG_NAME_SET.add("stddev");
        AGG_NAME_SET.add("percentile");
        AGG_NAME_SET.add("percentile_approx");
    }

    public boolean isAgg() {
        return isAgg;
    }

    @Override
    public Boolean visit(SqlCall call) {
        if (AGG_NAME_SET.contains(call.getOperator().getName().toLowerCase())) {
            isAgg = true;
        }
        return super.visit(call);
    }
}
