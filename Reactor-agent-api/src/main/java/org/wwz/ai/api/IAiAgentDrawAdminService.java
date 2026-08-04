package org.wwz.ai.api;

import org.wwz.ai.api.dto.AiAgentDrawConfigRequestDTO;
import org.wwz.ai.api.dto.AiAgentDrawConfigQueryRequestDTO;
import org.wwz.ai.api.dto.AiAgentDrawConfigResponseDTO;
import org.wwz.ai.api.response.Response;
import java.util.List;


/**
 * 可视化拖拉拽流程图配置的管理契约。
 *
 * <p>配置正文以字符串形式跨越 API 边界保存和读取，具体 JSON/节点结构由 trigger 与 domain 层解析，
 * 本接口不提前绑定编辑器实现。</p>
 */
public interface IAiAgentDrawAdminService {

    /**
     * 保存拖拉拽流程图配置
     *
     * @param request 配置请求参数
     * @return 保存结果
     */
    Response<String> saveDrawConfig(AiAgentDrawConfigRequestDTO request);

    /**
     * 获取拖拉拽流程图配置
     *
     * @param configId 配置ID
     * @return 配置数据
     */
    Response<AiAgentDrawConfigResponseDTO> getDrawConfig(String configId);

    /**
     * 分页查询拖拉拽流程图配置列表
     *
     * @param request 查询条件与分页参数
     * @return 配置列表
     */
    Response<List<AiAgentDrawConfigResponseDTO>> queryDrawConfigList(AiAgentDrawConfigQueryRequestDTO request);

    /**
     * 删除拖拉拽流程图配置
     *
     * @param configId 配置ID
     * @return 删除结果
     */
    Response<String> deleteDrawConfig(String configId);

}
