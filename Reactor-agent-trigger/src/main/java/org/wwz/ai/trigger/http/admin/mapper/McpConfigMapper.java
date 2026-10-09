package org.wwz.ai.trigger.http.admin.mapper;

import org.springframework.stereotype.Component;
import org.wwz.ai.api.dto.AiClientToolMcpQueryRequestDTO;
import org.wwz.ai.api.dto.AiClientToolMcpRequestDTO;
import org.wwz.ai.api.dto.AiClientToolMcpResponseDTO;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.catalog.ApplicationResult;
import org.wwz.ai.application.catalog.mcp.McpConfigCommand;
import org.wwz.ai.application.catalog.mcp.McpConfigQueryCommand;
import org.wwz.ai.domain.agent.catalog.model.AiClientToolMcpConfig;

import java.util.List;

/** MCP Admin HTTP DTO 与 Catalog 应用命令/结果的转换。 */
@Component
public class McpConfigMapper {

    public McpConfigCommand toCommand(AiClientToolMcpRequestDTO request) {
        if (request == null) {
            return null;
        }
        return new McpConfigCommand(
                request.getId(),
                request.getMcpId(),
                request.getMcpName(),
                request.getTransportType(),
                request.getTransportConfig(),
                request.getRequestTimeout(),
                request.getStatus());
    }

    public McpConfigQueryCommand toQueryCommand(AiClientToolMcpQueryRequestDTO request) {
        if (request == null) {
            return null;
        }
        return new McpConfigQueryCommand(
                request.getMcpId(),
                request.getMcpName(),
                request.getTransportType(),
                request.getStatus(),
                request.getPageNum(),
                request.getPageSize());
    }

    public Response<Boolean> toBooleanResponse(ApplicationResult<Boolean> result) {
        return Response.<Boolean>builder()
                .code(result.code())
                .info(result.info())
                .data(result.data())
                .build();
    }

    public Response<AiClientToolMcpResponseDTO> toResponse(
            ApplicationResult<AiClientToolMcpConfig> result) {
        return Response.<AiClientToolMcpResponseDTO>builder()
                .code(result.code())
                .info(result.info())
                .data(toResponse(result.data()))
                .build();
    }

    public Response<List<AiClientToolMcpResponseDTO>> toListResponse(
            ApplicationResult<List<AiClientToolMcpConfig>> result) {
        List<AiClientToolMcpResponseDTO> data = result.data() == null
                ? null
                : result.data().stream().map(this::toResponse).toList();
        return Response.<List<AiClientToolMcpResponseDTO>>builder()
                .code(result.code())
                .info(result.info())
                .data(data)
                .build();
    }

    private AiClientToolMcpResponseDTO toResponse(AiClientToolMcpConfig config) {
        if (config == null) {
            return null;
        }
        return AiClientToolMcpResponseDTO.builder()
                .id(config.getId())
                .mcpId(config.getMcpId())
                .mcpName(config.getMcpName())
                .transportType(config.getTransportType())
                .transportConfig(config.getTransportConfig())
                .requestTimeout(config.getRequestTimeout())
                .status(config.getStatus())
                .createTime(config.getCreateTime())
                .updateTime(config.getUpdateTime())
                .build();
    }
}
