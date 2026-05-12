package org.wwz.ai.api;

import org.wwz.ai.api.dto.AiClientQueryRequestDTO;
import org.wwz.ai.api.dto.AiClientRequestDTO;
import org.wwz.ai.api.dto.AiClientResponseDTO;
import org.wwz.ai.api.response.Response;

import java.util.List;

//todo:预留给后续开发
public interface IAiClientAdminService {

    /**
     * 创建AI客户端配置
     * @param request AI客户端配置请求对象
     * @return 操作结果
     */
    Response<Boolean> createAiClient(AiClientRequestDTO request);

    /**
     * 根据ID更新AI客户端配置
     * @param request AI客户端配置请求对象
     * @return 操作结果
     */
    Response<Boolean> updateAiClientById(AiClientRequestDTO request);

    /**
     * 根据客户端ID更新AI客户端配置
     * @param request AI客户端配置请求对象
     * @return 操作结果
     */
    Response<Boolean> updateAiClientByClientId(AiClientRequestDTO request);

    /**
     * 根据ID删除AI客户端配置
     * @param id 主键ID
     * @return 操作结果
     */
    Response<Boolean> deleteAiClientById(Long id);

    /**
     * 根据客户端ID删除AI客户端配置
     * @param clientId 客户端ID
     * @return 操作结果
     */
    Response<Boolean> deleteAiClientByClientId(String clientId);

    /**
     * 根据ID查询AI客户端配置
     * @param id 主键ID
     * @return AI客户端配置对象
     */
    Response<AiClientResponseDTO> queryAiClientById(Long id);

    /**
     * 根据客户端ID查询AI客户端配置
     * @param clientId 客户端ID
     * @return AI客户端配置对象
     */
    Response<AiClientResponseDTO> queryAiClientByClientId(String clientId);

    /**
     * 查询所有启用的AI客户端配置
     * @return AI客户端配置列表
     */
    Response<List<AiClientResponseDTO>> queryEnabledAiClients();

    /**
     * 根据条件查询AI客户端配置列表
     * @param request 查询条件
     * @return AI客户端配置列表
     */
    Response<List<AiClientResponseDTO>> queryAiClientList(AiClientQueryRequestDTO request);

    /**
     * 查询所有AI客户端配置
     * @return AI客户端配置列表
     */
    Response<List<AiClientResponseDTO>> queryAllAiClients();

}
