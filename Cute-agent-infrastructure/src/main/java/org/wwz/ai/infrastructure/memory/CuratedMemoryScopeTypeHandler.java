package org.wwz.ai.infrastructure.memory;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryScope;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** 将用户长期记忆作用域在 Java 枚举和数据库字符串之间转换的 MyBatis 类型处理器。 */
@MappedTypes(CuratedMemoryScope.class)
public class CuratedMemoryScopeTypeHandler extends BaseTypeHandler<CuratedMemoryScope> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, CuratedMemoryScope parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.getCode());
    }

    @Override
    public CuratedMemoryScope getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return CuratedMemoryScope.fromCode(rs.getString(columnName));
    }

    @Override
    public CuratedMemoryScope getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return CuratedMemoryScope.fromCode(rs.getString(columnIndex));
    }

    @Override
    public CuratedMemoryScope getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return CuratedMemoryScope.fromCode(cs.getString(columnIndex));
    }
}
