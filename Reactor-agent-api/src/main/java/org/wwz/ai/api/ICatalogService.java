package org.wwz.ai.api;

import org.wwz.ai.api.dto.CatalogCapabilitiesResponseDTO;
import org.wwz.ai.api.dto.CatalogModelResponseDTO;
import org.wwz.ai.api.dto.CatalogSubAgentCreateRequestDTO;
import org.wwz.ai.api.dto.CatalogSubAgentResponseDTO;
import org.wwz.ai.api.response.Response;

import java.util.List;

/**
 * 登录用户可读的运行时目录契约。
 *
 * <p>目录 DTO 只包含展示和选择所需的安全元数据，不承载连接凭据、内部提示词
 * 或本地文件路径。</p>
 */
public interface ICatalogService {

    Response<List<CatalogModelResponseDTO>> listModels();

    Response<List<CatalogSubAgentResponseDTO>> listSubAgents();

    /**
     * 登录用户只能通过 catalog 创建新子 Agent，不能更新或删除已有定义。
     */
    Response<Boolean> createSubAgent(CatalogSubAgentCreateRequestDTO request);

    Response<CatalogCapabilitiesResponseDTO> listCapabilities();
}
