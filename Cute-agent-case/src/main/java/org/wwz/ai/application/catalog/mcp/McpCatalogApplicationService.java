package org.wwz.ai.application.catalog.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.catalog.ApplicationResult;
import org.wwz.ai.domain.agent.catalog.model.AiClientToolMcpConfig;
import org.wwz.ai.domain.agent.catalog.model.CatalogMcp;
import org.wwz.ai.domain.agent.catalog.port.IAiClientToolMcpConfigRepository;
import org.wwz.ai.types.enums.ResponseCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MCP 目录、安全投影及普通目录创建用例。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpCatalogApplicationService {

    private final IAiClientToolMcpConfigRepository repository;
    private final McpWriteReloadSupport writeReloadSupport;
    private final McpConfigurationApplicationService configurationService;

    public ApplicationResult<List<CatalogMcp>> listMcps() {
        try {
            List<AiClientToolMcpConfig> configs = repository.findAll();
            return ApplicationResult.success(configs == null
                    ? List.of()
                    : configs.stream().filter(config -> config != null).map(McpCatalogApplicationService::toCatalogMcp).toList());
        } catch (Exception e) {
            log.error("查询 MCP 目录失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), null);
        }
    }

    public ApplicationResult<Boolean> createMcp(CatalogMcpCreateCommand command) {
        try {
            return writeReloadSupport.afterWrite(configurationService.createMcp(command));
        } catch (IllegalArgumentException e) {
            return ApplicationResult.failure(ResponseCode.ILLEGAL_PARAMETER, e.getMessage(), false);
        } catch (Exception e) {
            log.error("新增 MCP 失败", e);
            return ApplicationResult.failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo(), false);
        }
    }


    private static CatalogMcp toCatalogMcp(AiClientToolMcpConfig config) {
        return new CatalogMcp(config.getMcpId(), config.getMcpName(), config.getTransportType(),
                config.getStatus() == null ? 1 : config.getStatus());
    }

}
