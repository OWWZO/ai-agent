package org.wwz.ai.domain.agent.reactor.data.model;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.EnumSet;
import java.util.Set;

public enum ComparisonType {

    IN("IN", "包含","",""),
    NOT_IN("NOT IN", "不包含","",""),
    LESS_THAN("<", "小于","%s<%s",""),
    GREATER_THAN(">", "大于","%s>%s",""),
    LESS_THAN_OR_EQUAL("<=", "小于等于","%s<=%s",""),
    GREATER_THAN_OR_EQUAL(">=", "大于等于","%s>=%s",""),
    EQUALS("=", "等于","%s==%s",""),
    NOT_EQUALS("<>", "不等于","%s!=%s",""),
    LIKE("LIKE", "模糊匹配","str:contains(%s,%s)","like '%${value}%'"),
    NOT_LIKE("NOT LIKE", "模糊匹配","","not like '%${value}%'"),
    RLIKE("RLIKE", "模糊左匹配","","like '%${value}'"),
    LLIKE("LLIKE", "模糊右匹配","","like '${value}%'"),
    IS_NULL("IS NULL", "为空","",""),
    IS_NOT_NULL("IS NOT NULL", "不为空","",""),
    BETWEEN("BETWEEN", "区间","","");


    public static final Set<ComparisonType> NONE_PARAM_TYPE = EnumSet.of(IS_NULL, IS_NOT_NULL);

    public static final Set<ComparisonType> ONE_PARAM_TYPE =
            EnumSet.of(LESS_THAN, GREATER_THAN, LESS_THAN_OR_EQUAL, GREATER_THAN_OR_EQUAL
                    , EQUALS, NOT_EQUALS, LIKE, RLIKE, LLIKE);

    public static final Set<ComparisonType> MORE_PARAM_TYPE = EnumSet.of(IN, NOT_IN, BETWEEN);

    @Getter
    private final String comparison;

    @Getter
    private final String comparisonName;
    @Getter
    private final String relationalAlgebra;
    @Getter
    private final String relationalAlgebraSql;

    ComparisonType(String comparison, String comparisonName,String relationalAlgebra,String relationalAlgebraSql) {
        this.comparison = comparison;
        this.comparisonName = comparisonName;
        this.relationalAlgebra = relationalAlgebra;
        this.relationalAlgebraSql = relationalAlgebraSql;
    }


    public static ComparisonType of(String var0) {
        ComparisonType[] var1 = ComparisonType.class.getEnumConstants();

        for (ComparisonType var4 : var1) {
            if (StringUtils.equalsIgnoreCase(var0, var4.name())) {
                return var4;
            }
        }

        throw new IllegalArgumentException("不支持的操作类型:" + var0);
    }
}
