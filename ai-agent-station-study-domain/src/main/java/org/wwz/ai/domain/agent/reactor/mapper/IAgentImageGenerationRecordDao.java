package org.wwz.ai.domain.agent.reactor.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wwz.ai.domain.agent.reactor.entity.AgentImageGenerationRecord;

import java.util.List;

/**
 * 生图工作台结果明细 DAO。
 */
@Mapper
public interface IAgentImageGenerationRecordDao {

    int insert(AgentImageGenerationRecord record);

    /**
     * 按设备统计可见历史批次数。
     */
    int countDistinctRequestIdByDeviceId(@Param("deviceId") String deviceId);

    /**
     * 按设备分页查询批次 requestId。
     */
    List<String> queryRequestIdsByDeviceId(@Param("deviceId") String deviceId,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    /**
     * 按 requestId 批量查询图片明细。
     */
    List<AgentImageGenerationRecord> queryByRequestIds(@Param("deviceId") String deviceId,
                                                       @Param("requestIds") List<String> requestIds);
}
