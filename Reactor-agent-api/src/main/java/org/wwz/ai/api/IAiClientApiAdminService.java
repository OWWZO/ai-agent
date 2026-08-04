package org.wwz.ai.api;

import org.wwz.ai.api.dto.AiClientApiQueryRequestDTO;
import org.wwz.ai.api.dto.AiClientApiRequestDTO;
import org.wwz.ai.api.dto.AiClientApiResponseDTO;
import org.wwz.ai.api.response.Response;

import java.util.List;


/**
 * AI 客户端 API 连接配置管理契约。
 *
 * <p>配置包含兼容接口的基础地址、补全路径和 embedding 路径；敏感字段的存储与脱敏由实现层负责，
 * API 方法只暴露配置管理动作。</p>
 */
public interface IAiClientApiAdminService {

    /**
     * 创建AI客户端API配置
     * @param request AI客户端API配置请求对象
     * @return 操作结果
     */
    Response<Boolean> createAiClientApi(AiClientApiRequestDTO request);

    /**
     * 根据ID更新AI客户端API配置
     * @param request AI客户端API配置请求对象
     * @return 操作结果
     */
    Response<Boolean> updateAiClientApiById(AiClientApiRequestDTO request);

    /**
     * 根据API ID更新AI客户端API配置
     * @param request AI客户端API配置请求对象
     * @return 操作结果
     */
    Response<Boolean> updateAiClientApiByApiId(AiClientApiRequestDTO request);

    /**
     * 根据ID删除AI客户端API配置
     * @param id 主键ID
     * @return 操作结果
     */
    Response<Boolean> deleteAiClientApiById(Long id);

    /**
     * 根据API ID删除AI客户端API配置
     * @param apiId API ID
     * @return 操作结果
     */
    Response<Boolean> deleteAiClientApiByApiId(String apiId);

    /**
     * 根据ID查询AI客户端API配置
     * @param id 主键ID
     * @return AI客户端API配置对象
     */
    Response<AiClientApiResponseDTO> queryAiClientApiById(Long id);

    /**
     * 根据API ID查询AI客户端API配置
     * @param apiId API ID
     * @return AI客户端API配置对象
     */
    Response<AiClientApiResponseDTO> queryAiClientApiByApiId(String apiId);

    /**
     * 查询所有启用的AI客户端API配置
     * @return AI客户端API配置列表
     */
    Response<List<AiClientApiResponseDTO>> queryEnabledAiClientApis();

    /**
     * 分页查询AI客户端API配置列表
     * @param request 查询请求对象
     * @return AI客户端API配置列表
     */
    Response<List<AiClientApiResponseDTO>> queryAiClientApiList(AiClientApiQueryRequestDTO request);

    /**
     * 查询所有AI客户端API配置
     * @return AI客户端API配置列表
     */
    Response<List<AiClientApiResponseDTO>> queryAllAiClientApis();

}
