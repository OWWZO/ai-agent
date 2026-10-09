package org.wwz.ai.trigger.http.admin.mapper;

import org.springframework.stereotype.Component;
import org.wwz.ai.api.dto.AiClientApiQueryRequestDTO;
import org.wwz.ai.api.dto.AiClientApiRequestDTO;
import org.wwz.ai.api.dto.AiClientApiResponseDTO;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.catalog.ApplicationResult;
import org.wwz.ai.application.catalog.api.ApiConfigCommand;
import org.wwz.ai.application.catalog.api.ApiConfigQueryCommand;
import org.wwz.ai.domain.agent.catalog.model.AiClientApiConfig;

import java.util.List;

/** API Admin HTTP DTO 与 Catalog 应用命令/结果的转换。 */
@Component
public class ApiConfigMapper {

    public ApiConfigCommand toCommand(AiClientApiRequestDTO request) {
        if (request == null) {
            return null;
        }
        return new ApiConfigCommand(
                request.getId(),
                request.getApiId(),
                request.getBaseUrl(),
                request.getApiKey(),
                request.getCompletionsPath(),
                request.getEmbeddingsPath(),
                request.getStatus());
    }

    public ApiConfigQueryCommand toQueryCommand(AiClientApiQueryRequestDTO request) {
        if (request == null) {
            return null;
        }
        return new ApiConfigQueryCommand(
                request.getApiId(),
                request.getBaseUrl(),
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

    public Response<AiClientApiResponseDTO> toResponse(ApplicationResult<AiClientApiConfig> result) {
        return Response.<AiClientApiResponseDTO>builder()
                .code(result.code())
                .info(result.info())
                .data(toResponse(result.data()))
                .build();
    }

    public Response<List<AiClientApiResponseDTO>> toListResponse(ApplicationResult<List<AiClientApiConfig>> result) {
        List<AiClientApiResponseDTO> data = result.data() == null
                ? null
                : result.data().stream().map(this::toResponse).toList();
        return Response.<List<AiClientApiResponseDTO>>builder()
                .code(result.code())
                .info(result.info())
                .data(data)
                .build();
    }

    private AiClientApiResponseDTO toResponse(AiClientApiConfig config) {
        if (config == null) {
            return null;
        }
        return AiClientApiResponseDTO.builder()
                .id(config.getId())
                .apiId(config.getApiId())
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .completionsPath(config.getCompletionsPath())
                .embeddingsPath(config.getEmbeddingsPath())
                .status(config.getStatus())
                .createTime(config.getCreateTime())
                .updateTime(config.getUpdateTime())
                .build();
    }
}
