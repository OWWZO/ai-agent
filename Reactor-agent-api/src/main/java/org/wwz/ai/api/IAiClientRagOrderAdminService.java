package org.wwz.ai.api;

import org.wwz.ai.api.dto.AiClientRagOrderQueryRequestDTO;
import org.wwz.ai.api.dto.AiClientRagOrderRequestDTO;
import org.wwz.ai.api.dto.AiClientRagOrderResponseDTO;
import org.wwz.ai.api.response.Response;
import java.util.List;


/**
 * AI 客户端知识库/RAG 配置管理契约。
 *
 * <p>ragId 是业务知识库标识，knowledgeTag 用于按知识域筛选；启用状态由实现层统一解释为可用候选范围。</p>
 */
public interface IAiClientRagOrderAdminService {

    /**
     * 创建知识库配置
     * @param request 知识库配置请求对象
     * @return 操作结果
     */
    Response<Boolean> createAiClientRagOrder(AiClientRagOrderRequestDTO request);

    /**
     * 根据ID更新知识库配置
     * @param request 知识库配置请求对象
     * @return 操作结果
     */
    Response<Boolean> updateAiClientRagOrderById(AiClientRagOrderRequestDTO request);

    /**
     * 根据知识库ID更新知识库配置
     * @param request 知识库配置请求对象
     * @return 操作结果
     */
    Response<Boolean> updateAiClientRagOrderByRagId(AiClientRagOrderRequestDTO request);

    /**
     * 根据ID删除知识库配置
     * @param id 主键ID
     * @return 操作结果
     */
    Response<Boolean> deleteAiClientRagOrderById(Long id);

    /**
     * 根据知识库ID删除知识库配置
     * @param ragId 知识库ID
     * @return 操作结果
     */
    Response<Boolean> deleteAiClientRagOrderByRagId(String ragId);

    /**
     * 根据ID查询知识库配置
     * @param id 主键ID
     * @return 知识库配置对象
     */
    Response<AiClientRagOrderResponseDTO> queryAiClientRagOrderById(Long id);

    /**
     * 根据知识库ID查询知识库配置
     * @param ragId 知识库ID
     * @return 知识库配置对象
     */
    Response<AiClientRagOrderResponseDTO> queryAiClientRagOrderByRagId(String ragId);

    /**
     * 查询所有启用的知识库配置
     * @return 知识库配置列表
     */
    Response<List<AiClientRagOrderResponseDTO>> queryEnabledAiClientRagOrders();

    /**
     * 根据知识标签查询知识库配置
     * @param knowledgeTag 知识标签
     * @return 知识库配置列表
     */
    Response<List<AiClientRagOrderResponseDTO>> queryAiClientRagOrdersByKnowledgeTag(String knowledgeTag);

    /**
     * 根据状态查询知识库配置
     * @param status 状态
     * @return 知识库配置列表
     */
    Response<List<AiClientRagOrderResponseDTO>> queryAiClientRagOrdersByStatus(Integer status);

    /**
     * 分页查询知识库配置列表
     * @param request 查询请求对象
     * @return 知识库配置列表
     */
    Response<List<AiClientRagOrderResponseDTO>> queryAiClientRagOrderList(AiClientRagOrderQueryRequestDTO request);

    /**
     * 查询所有知识库配置
     * @return 知识库配置列表
     */
    Response<List<AiClientRagOrderResponseDTO>> queryAllAiClientRagOrders();

}
