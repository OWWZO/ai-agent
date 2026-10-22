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
import org.wwz.ai.api.dto.AiClientApiQueryRequestDTO;
import org.wwz.ai.api.dto.AiClientApiRequestDTO;
import org.wwz.ai.api.dto.AiClientApiResponseDTO;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.catalog.api.ApiConfigApplicationService;
import org.wwz.ai.trigger.http.admin.mapper.ApiConfigMapper;

import java.util.List;

/**
 * AI 客户端 API 配置管理控制器。
 */
@RestController
@RequestMapping("/api/v1/admin/ai-client-api")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS
})
public class AiClientApiAdminController {

    private final ApiConfigApplicationService applicationService;
    private final ApiConfigMapper mapper;

    public AiClientApiAdminController(ApiConfigApplicationService applicationService, ApiConfigMapper mapper) {
        this.applicationService = applicationService;
        this.mapper = mapper;
    }

    @PostMapping("/create")
    public Response<Boolean> createAiClientApi(@RequestBody AiClientApiRequestDTO request) {
        return mapper.toBooleanResponse(applicationService.create(mapper.toCommand(request)));
    }

    @PutMapping("/update-by-id")
    public Response<Boolean> updateAiClientApiById(@RequestBody AiClientApiRequestDTO request) {
        return mapper.toBooleanResponse(applicationService.updateById(mapper.toCommand(request)));
    }

    @PutMapping("/update-by-api-id")
    public Response<Boolean> updateAiClientApiByApiId(@RequestBody AiClientApiRequestDTO request) {
        return mapper.toBooleanResponse(applicationService.updateByApiId(mapper.toCommand(request)));
    }

    @DeleteMapping("/delete-by-id/{id}")
    public Response<Boolean> deleteAiClientApiById(@PathVariable("id") Long id) {
        return mapper.toBooleanResponse(applicationService.deleteById(id));
    }

    @DeleteMapping("/delete-by-api-id/{apiId}")
    public Response<Boolean> deleteAiClientApiByApiId(@PathVariable("apiId") String apiId) {
        return mapper.toBooleanResponse(applicationService.deleteByApiId(apiId));
    }

    @GetMapping("/query-by-id/{id}")
    public Response<AiClientApiResponseDTO> queryAiClientApiById(@PathVariable("id") Long id) {
        return mapper.toResponse(applicationService.queryById(id));
    }

    @GetMapping("/query-by-api-id/{apiId}")
    public Response<AiClientApiResponseDTO> queryAiClientApiByApiId(@PathVariable("apiId") String apiId) {
        return mapper.toResponse(applicationService.queryByApiId(apiId));
    }

    @GetMapping("/query-enabled")
    public Response<List<AiClientApiResponseDTO>> queryEnabledAiClientApis() {
        return mapper.toListResponse(applicationService.queryEnabled());
    }

    @PostMapping("/query-list")
    public Response<List<AiClientApiResponseDTO>> queryAiClientApiList(
            @RequestBody AiClientApiQueryRequestDTO request) {
        return mapper.toListResponse(applicationService.queryList(mapper.toQueryCommand(request)));
    }

    @GetMapping("/query-all")
    public Response<List<AiClientApiResponseDTO>> queryAllAiClientApis() {
        return mapper.toListResponse(applicationService.queryAll());
    }
}
