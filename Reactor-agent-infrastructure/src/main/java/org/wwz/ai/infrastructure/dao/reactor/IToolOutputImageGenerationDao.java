package org.wwz.ai.infrastructure.dao.reactor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * image_generation_tool 输出表 DAO。
 */
@Mapper
public interface IToolOutputImageGenerationDao {

    int insert(@Param("row") Map<String, Object> row);

    Map<String, Object> queryByToolInvocationId(@Param("toolInvocationId") Long toolInvocationId);

    Map<String, Object> queryByRequestToolCall(@Param("requestId") String requestId,
                                               @Param("toolCallId") String toolCallId);

    int countByRequestSource(@Param("requestSource") String requestSource);

    List<Map<String, Object>> queryPageByRequestSource(@Param("requestSource") String requestSource,
                                                       @Param("offset") int offset,
                                                       @Param("limit") int limit);
}
