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
import org.wwz.ai.api.dto.AiClientToolMcpQueryRequestDTO;
import org.wwz.ai.api.dto.AiClientToolMcpRequestDTO;
import org.wwz.ai.api.dto.AiClientToolMcpResponseDTO;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.catalog.mcp.McpConfigApplicationService;
import org.wwz.ai.trigger.http.admin.mapper.McpConfigMapper;

import java.util.List;

/**
 * MCP 客户端配置管理控制器。
 */
@RestController
@RequestMapping("/api/v1/admin/ai-client-tool-mcp")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS
})
public class AiClientToolMcpAdminController {

    private final McpConfigApplicationService applicationService;
    private final McpConfigMapper mapper;

    public AiClientToolMcpAdminController(McpConfigApplicationService applicationService, McpConfigMapper mapper) {
        this.applicationService = applicationService;
        this.mapper = mapper;
    }

    @PostMapping("/create")
    public Response<Boolean> createAiClientToolMcp(@RequestBody AiClientToolMcpRequestDTO request) {
        return mapper.toBooleanResponse(applicationService.create(mapper.toCommand(request)));
    }

    @PutMapping("/update-by-id")
    public Response<Boolean> updateAiClientToolMcpById(@RequestBody AiClientToolMcpRequestDTO request) {
        return mapper.toBooleanResponse(applicationService.updateById(mapper.toCommand(request)));
    }

    @PutMapping("/update-by-mcp-id")
    public Response<Boolean> updateAiClientToolMcpByMcpId(@RequestBody AiClientToolMcpRequestDTO request) {
        return mapper.toBooleanResponse(applicationService.updateByMcpId(mapper.toCommand(request)));
    }

    @DeleteMapping("/delete-by-id/{id}")
    public Response<Boolean> deleteAiClientToolMcpById(@PathVariable("id") Long id) {
        return mapper.toBooleanResponse(applicationService.deleteById(id));
    }

    @DeleteMapping("/delete-by-mcp-id/{mcpId}")
    public Response<Boolean> deleteAiClientToolMcpByMcpId(@PathVariable("mcpId") String mcpId) {
        return mapper.toBooleanResponse(applicationService.deleteByMcpId(mcpId));
    }

    @GetMapping("/query-by-id/{id}")
    public Response<AiClientToolMcpResponseDTO> queryAiClientToolMcpById(@PathVariable("id") Long id) {
        return mapper.toResponse(applicationService.queryById(id));
    }

    @GetMapping("/query-by-mcp-id/{mcpId}")
    public Response<AiClientToolMcpResponseDTO> queryAiClientToolMcpByMcpId(
            @PathVariable("mcpId") String mcpId) {
        return mapper.toResponse(applicationService.queryByMcpId(mcpId));
    }

    @GetMapping("/query-all")
    public Response<List<AiClientToolMcpResponseDTO>> queryAllAiClientToolMcps() {
        return mapper.toListResponse(applicationService.queryAll());
    }

    @GetMapping("/query-by-status/{status}")
    public Response<List<AiClientToolMcpResponseDTO>> queryAiClientToolMcpsByStatus(
            @PathVariable("status") Integer status) {
        return mapper.toListResponse(applicationService.queryByStatus(status));
    }

    @GetMapping("/query-by-transport-type/{transportType}")
    public Response<List<AiClientToolMcpResponseDTO>> queryAiClientToolMcpsByTransportType(
            @PathVariable("transportType") String transportType) {
        return mapper.toListResponse(applicationService.queryByTransportType(transportType));
    }

    @GetMapping("/query-enabled")
    public Response<List<AiClientToolMcpResponseDTO>> queryEnabledAiClientToolMcps() {
        return mapper.toListResponse(applicationService.queryEnabled());
    }

    @PostMapping("/query-list")
    public Response<List<AiClientToolMcpResponseDTO>> queryAiClientToolMcpList(
            @RequestBody AiClientToolMcpQueryRequestDTO request) {
        return mapper.toListResponse(applicationService.queryList(mapper.toQueryCommand(request)));
    }
}
