package org.wwz.ai.api;

import org.wwz.ai.api.dto.AiClientModelQueryRequestDTO;
import org.wwz.ai.api.dto.AiClientModelRequestDTO;
import org.wwz.ai.api.dto.AiClientModelResponseDTO;
import org.wwz.ai.api.response.Response;

import java.util.List;


/**
 * AI 客户端模型配置管理契约。
 *
 * <p>模型通过 modelId 关联外部模型，通过 apiId 关联连接配置；按模型类型和 API 配置查询用于运行时选择候选模型。</p>
 */
public interface IAiClientModelAdminService {

    /**
     * 创建AI客户端模型配置
     * @param request AI客户端模型配置请求对象
     * @return 操作结果
     */
    Response<Boolean> createAiClientModel(AiClientModelRequestDTO request);

    /**
     * 根据ID更新AI客户端模型配置
     * @param request AI客户端模型配置请求对象
     * @return 操作结果
     */
    Response<Boolean> updateAiClientModelById(AiClientModelRequestDTO request);

    /**
     * 根据模型ID更新AI客户端模型配置；重复模型ID时必须携带记录ID
     * @param request AI客户端模型配置请求对象
     * @return 操作结果
     */
    Response<Boolean> updateAiClientModelByModelId(AiClientModelRequestDTO request);

    /**
     * 根据ID删除AI客户端模型配置
     * @param id 主键ID
     * @return 操作结果
     */
    Response<Boolean> deleteAiClientModelById(Long id);

    /**
     * 根据模型ID删除AI客户端模型配置；重复模型ID时应使用按ID删除接口
     * @param modelId 模型引用标识
     * @return 操作结果
     */
    Response<Boolean> deleteAiClientModelByModelId(String modelId);

    /**
     * 根据ID查询AI客户端模型配置
     * @param id 主键ID
     * @return AI客户端模型配置对象
     */
    Response<AiClientModelResponseDTO> queryAiClientModelById(Long id);

    /**
     * 根据模型ID查询一条AI客户端模型配置；重复模型ID时返回主模型配置
     * @param modelId 模型引用标识
     * @return AI客户端模型配置对象
     */
    Response<AiClientModelResponseDTO> queryAiClientModelByModelId(String modelId);

    /**
     * 根据API配置ID查询AI客户端模型配置列表
     * @param apiId API配置ID
     * @return AI客户端模型配置列表
     */
    Response<List<AiClientModelResponseDTO>> queryAiClientModelsByApiId(String apiId);

    /**
     * 根据模型类型查询AI客户端模型配置列表
     * @param modelType 模型类型
     * @return AI客户端模型配置列表
     */
    Response<List<AiClientModelResponseDTO>> queryAiClientModelsByModelType(String modelType);

    /**
     * 查询所有启用的AI客户端模型配置
     * @return AI客户端模型配置列表
     */
    Response<List<AiClientModelResponseDTO>> queryEnabledAiClientModels();

    /**
     * 根据条件查询AI客户端模型配置列表；按模型ID筛选时返回全部匹配配置
     * @param request 查询条件
     * @return AI客户端模型配置列表
     */
    Response<List<AiClientModelResponseDTO>> queryAiClientModelList(AiClientModelQueryRequestDTO request);

    /**
     * 查询所有AI客户端模型配置
     * @return AI客户端模型配置列表
     */
    Response<List<AiClientModelResponseDTO>> queryAllAiClientModels();

}
