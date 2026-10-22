package org.wwz.ai.infrastructure.dataquery.jdbc.dialect;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/** 系统支持的数据源方言及其 URL 组成规则。 */
@Getter
public enum DialectEnum {
    MYSQL("MySql", "jdbc:mysql://", "/", ""),
    H2("h2", "jdbc:h2:mem", ":", ";MODE=MySQL"),
    CLICKHOUSE("ClickHouse", "jdbc:clickhouse://", "/", "");

    private final String name;
    private final String urlPrefix;
    private final String suffixDelimiter;
    private final String urlEndWith;

    DialectEnum(String name, String urlPrefix, String suffixDelimiter, String urlEndWith) {
        this.name = name;
        this.urlPrefix = urlPrefix;
        this.suffixDelimiter = suffixDelimiter;
        this.urlEndWith = urlEndWith;
    }

    public static DialectEnum of(String dialectName) {
        // 管理配置通常传入展示名称，因此采用大小写不敏感的精确匹配。
        DialectEnum[] dialectEnums = DialectEnum.class.getEnumConstants();

        for (DialectEnum dialectEnum : dialectEnums) {
            if (StringUtils.equalsIgnoreCase(dialectName, dialectEnum.name)) {
                return dialectEnum;
            }
        }

        throw new IllegalArgumentException("不支持的数据源类型:" + dialectName);
    }

}
