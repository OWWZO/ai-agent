package org.wwz.ai.api;

import org.wwz.ai.api.dto.DataStatisticsResponseDTO;
import org.wwz.ai.api.response.Response;


public interface IAiAgentDataStatisticsAdminService {

    /**
     * 获取系统数据统计
     * @return 统计数据响应
     */
    Response<DataStatisticsResponseDTO> getDataStatistics();
}
