package org.wwz.ai.trigger.http.admin;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.wwz.ai.api.dto.AiClientModelQueryRequestDTO;
import org.wwz.ai.api.dto.AiClientModelRequestDTO;
import org.wwz.ai.api.dto.AiClientModelResponseDTO;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.catalog.model.ModelConfigApplicationService;
import org.wwz.ai.trigger.http.admin.mapper.ModelConfigMapper;

import java.util.List;
import java.util.Map;

/**
 * AI 客户端模型配置管理控制器。
 */
@RestController
@RequestMapping("/api/v1/admin/ai-client-model")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS
})
public class AiClientModelAdminController {

    private final ModelConfigApplicationService applicationService;
    private final ModelConfigMapper mapper;

    public AiClientModelAdminController(ModelConfigApplicationService applicationService, ModelConfigMapper mapper) {
        this.applicationService = applicationService;
        this.mapper = mapper;
    }

    @PostMapping("/test/{modelId}")
    public Response<Map<String, Object>> testConnection(@PathVariable("modelId") String modelId) {
        return mapper.toTestResponse(applicationService.testConnection(modelId));
    }

    @PostMapping("/test-by-id/{id}")
    public Response<Map<String, Object>> testConnectionById(@PathVariable("id") Long id) {
        return mapper.toTestResponse(applicationService.testConnectionById(id));
    }

    @PostMapping("/create")
    public Response<Boolean> createAiClientModel(@RequestBody AiClientModelRequestDTO request) {
        return mapper.toBooleanResponse(applicationService.create(mapper.toCommand(request)));
    }

    @PutMapping("/update-by-id")
    public Response<Boolean> updateAiClientModelById(@RequestBody AiClientModelRequestDTO request) {
        return mapper.toBooleanResponse(applicationService.updateById(mapper.toCommand(request)));
    }

    @PutMapping("/update-by-model-id")
    public Response<Boolean> updateAiClientModelByModelId(@RequestBody AiClientModelRequestDTO request) {
        return mapper.toBooleanResponse(applicationService.updateByModelId(mapper.toCommand(request)));
    }

    @DeleteMapping("/delete-by-id/{id}")
    public Response<Boolean> deleteAiClientModelById(@PathVariable("id") Long id) {
        return mapper.toBooleanResponse(applicationService.deleteById(id));
    }

    @DeleteMapping("/delete-by-model-id/{modelId}")
    public Response<Boolean> deleteAiClientModelByModelId(@PathVariable("modelId") String modelId) {
        return mapper.toBooleanResponse(applicationService.deleteByModelId(modelId));
    }

    @GetMapping("/query-by-id/{id}")
    public Response<AiClientModelResponseDTO> queryAiClientModelById(@PathVariable("id") Long id) {
        return mapper.toResponse(applicationService.queryById(id));
    }

    @GetMapping("/query-by-model-id/{modelId}")
    public Response<AiClientModelResponseDTO> queryAiClientModelByModelId(
            @PathVariable("modelId") String modelId) {
        return mapper.toResponse(applicationService.queryByModelId(modelId));
    }

    @GetMapping("/query-by-api-id/{apiId}")
    public Response<List<AiClientModelResponseDTO>> queryAiClientModelsByApiId(
            @PathVariable("apiId") String apiId) {
        return mapper.toListResponse(applicationService.queryByApiId(apiId));
    }

    @GetMapping("/query-by-model-type/{modelType}")
    public Response<List<AiClientModelResponseDTO>> queryAiClientModelsByModelType(
            @PathVariable("modelType") String modelType) {
        return mapper.toListResponse(applicationService.queryByModelType(modelType));
    }

    @GetMapping("/query-enabled")
    public Response<List<AiClientModelResponseDTO>> queryEnabledAiClientModels() {
        return mapper.toListResponse(applicationService.queryEnabled());
    }

    @PostMapping("/query-list")
    public Response<List<AiClientModelResponseDTO>> queryAiClientModelList(
            @RequestBody AiClientModelQueryRequestDTO request) {
        return mapper.toListResponse(applicationService.queryList(mapper.toQueryCommand(request)));
    }

    @GetMapping("/query-all")
    public Response<List<AiClientModelResponseDTO>> queryAllAiClientModels() {
        return mapper.toListResponse(applicationService.queryAll());
    }
}
