package org.wwz.ai.api;

import org.wwz.ai.api.dto.DataStatisticsResponseDTO;
import org.wwz.ai.api.response.Response;


/**
 * 管理端首页统计数据查询契约。
 *
 * <p>返回对象聚合多个配置域和运行态指标，接口层只定义统一的读取结果，不承担统计口径计算。</p>
 */
public interface IAiAgentDataStatisticsAdminService {

    /**
     * 获取系统数据统计
     * @return 统计数据响应
     */
    Response<DataStatisticsResponseDTO> getDataStatistics();
}
