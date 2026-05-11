package org.wwz.ai.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * code_interpreter 输出表 DAO。
 */
@Mapper
public interface IToolOutputCodeInterpreterDao {

    int insert(@Param("row") Map<String, Object> row);

    Map<String, Object> queryByToolInvocationId(@Param("toolInvocationId") Long toolInvocationId);

    Map<String, Object> queryByRequestToolCall(@Param("requestId") String requestId,
                                               @Param("toolCallId") String toolCallId);
}
