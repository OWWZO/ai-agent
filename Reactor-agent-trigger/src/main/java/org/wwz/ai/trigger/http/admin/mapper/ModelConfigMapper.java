package org.wwz.ai.trigger.http.admin.mapper;

import org.springframework.stereotype.Component;
import org.wwz.ai.api.dto.AiClientModelQueryRequestDTO;
import org.wwz.ai.api.dto.AiClientModelRequestDTO;
import org.wwz.ai.api.dto.AiClientModelResponseDTO;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.catalog.ApplicationResult;
import org.wwz.ai.application.catalog.model.ModelConfigCommand;
import org.wwz.ai.application.catalog.model.ModelConfigQueryCommand;
import org.wwz.ai.domain.agent.catalog.model.AiClientModelConfig;
import org.wwz.ai.domain.agent.catalog.model.ModelConnectionTestResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 模型 Admin HTTP DTO 与 Catalog 应用命令/结果的转换。 */
@Component
public class ModelConfigMapper {

    public ModelConfigCommand toCommand(AiClientModelRequestDTO request) {
        if (request == null) {
            return null;
        }
        return new ModelConfigCommand(
                request.getId(),
                request.getModelId(),
                request.getApiId(),
                request.getModelName(),
                request.getModelType(),
                request.getModelUsage(),
                request.getSupportsThinking(),
                request.getContextWindow(),
                request.getStatus());
    }

    public ModelConfigQueryCommand toQueryCommand(AiClientModelQueryRequestDTO request) {
        if (request == null) {
            return null;
        }
        return new ModelConfigQueryCommand(
                request.getModelId(),
                request.getApiId(),
                request.getModelType(),
                request.getStatus());
    }

    public Response<Map<String, Object>> toTestResponse(ApplicationResult<ModelConnectionTestResult> result) {
        ModelConnectionTestResult test = result.data();
        Map<String, Object> body = new LinkedHashMap<>();
        if (test != null) {
            body.put("ok", test.ok());
            body.put("ms", test.ms());
            body.put("message", test.message());
        }
        return Response.<Map<String, Object>>builder()
                .code(result.code())
                .info(result.info())
                .data(body)
                .build();
    }

    public Response<Boolean> toBooleanResponse(ApplicationResult<Boolean> result) {
        return Response.<Boolean>builder()
                .code(result.code())
                .info(result.info())
                .data(result.data())
                .build();
    }

    public Response<AiClientModelResponseDTO> toResponse(ApplicationResult<AiClientModelConfig> result) {
        return Response.<AiClientModelResponseDTO>builder()
                .code(result.code())
                .info(result.info())
                .data(toResponse(result.data()))
                .build();
    }

    public Response<List<AiClientModelResponseDTO>> toListResponse(
            ApplicationResult<List<AiClientModelConfig>> result) {
        List<AiClientModelResponseDTO> data = result.data() == null
                ? null
                : result.data().stream().map(this::toResponse).toList();
        return Response.<List<AiClientModelResponseDTO>>builder()
                .code(result.code())
                .info(result.info())
                .data(data)
                .build();
    }

    private AiClientModelResponseDTO toResponse(AiClientModelConfig config) {
        if (config == null) {
            return null;
        }
        return AiClientModelResponseDTO.builder()
                .id(config.getId())
                .modelId(config.getModelId())
                .apiId(config.getApiId())
                .modelName(config.getModelName())
                .modelType(config.getModelType())
                .modelUsage(config.getModelUsage())
                .supportsThinking(config.getSupportsThinking())
                .contextWindow(config.getContextWindow())
                .status(config.getStatus())
                .createTime(config.getCreateTime())
                .updateTime(config.getUpdateTime())
                .build();
    }
}
