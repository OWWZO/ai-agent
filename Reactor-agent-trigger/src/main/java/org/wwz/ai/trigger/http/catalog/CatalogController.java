package org.wwz.ai.trigger.http.catalog;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.wwz.ai.api.ICatalogService;
import org.wwz.ai.api.dto.CatalogCapabilitiesResponseDTO;
import org.wwz.ai.api.dto.CatalogMcpCreateRequestDTO;
import org.wwz.ai.api.dto.CatalogModelCreateRequestDTO;
import org.wwz.ai.api.dto.CatalogModelResponseDTO;
import org.wwz.ai.api.dto.CatalogSubAgentCreateRequestDTO;
import org.wwz.ai.api.dto.CatalogSubAgentResponseDTO;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelCatalog;
import org.wwz.ai.domain.agent.runtime.tool.mcp.runtime.McpRegistry;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillLoadException;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPackageParser;
import org.wwz.ai.domain.agent.runtime.tool.skill.SkillPackageService;
import org.wwz.ai.infrastructure.dao.IAiClientApiDao;
import org.wwz.ai.infrastructure.dao.IAiClientModelDao;
import org.wwz.ai.infrastructure.dao.IAiClientToolMcpDao;
import org.wwz.ai.infrastructure.dao.po.AiClientApi;
import org.wwz.ai.infrastructure.dao.po.AiClientModel;
import org.wwz.ai.infrastructure.dao.po.AiClientToolMcp;
import org.wwz.ai.types.enums.ResponseCode;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 登录用户可读的安全目录接口。
 *
 * <p>普通用户只允许通过本控制器创建新资源；更新、删除和敏感的完整查询仍由
 * admin 控制器负责，并由认证过滤器拦截。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/catalog")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS
})
public class CatalogController {

    private final ICatalogService catalogService;
    private final IAiClientApiDao aiClientApiDao;
    private final IAiClientModelDao aiClientModelDao;
    private final IAiClientToolMcpDao aiClientToolMcpDao;
    private final ObjectProvider<LlmModelCatalog> llmModelCatalogProvider;
    private final McpRegistry mcpRegistry;
    private final SkillPackageService skillPackageService;

    @GetMapping("/models")
    public Response<List<CatalogModelResponseDTO>> models() {
        return catalogService.listModels();
    }

    @GetMapping("/sub-agents")
    public Response<List<CatalogSubAgentResponseDTO>> subAgents() {
        return catalogService.listSubAgents();
    }

    @PostMapping("/models")
    @Transactional
    public Response<Boolean> createModel(@RequestBody CatalogModelCreateRequestDTO request) {
        try {
            requireText(request == null ? null : request.getBaseUrl(), "Base URL 不能为空");
            requireText(request == null ? null : request.getApiKey(), "API Key 不能为空");
            String modelId = requireText(request == null ? null : request.getModelId(), "模型 ID 不能为空");
            String modelName = requireText(request == null ? null : request.getModelName(), "上游模型名不能为空");
            String apiId = resolveApiId(request.getApiId(), modelId);
            LocalDateTime now = LocalDateTime.now();

            AiClientApi api = AiClientApi.builder()
                    .apiId(apiId)
                    .baseUrl(request.getBaseUrl().trim())
                    .apiKey(request.getApiKey().trim())
                    .completionsPath(defaultIfBlank(request.getCompletionsPath(), "/chat/completions"))
                    .embeddingsPath(defaultIfBlank(request.getEmbeddingsPath(), "/embeddings"))
                    .status(normalizeStatus(request.getStatus()))
                    .createTime(now)
                    .updateTime(now)
                    .build();
            AiClientModel model = AiClientModel.builder()
                    .modelId(modelId)
                    .apiId(apiId)
                    .modelName(modelName)
                    .modelType(defaultIfBlank(request.getModelType(), "openai"))
                    .modelUsage("default")
                    .supportsThinking(request.getSupportsThinking() == null ? 0 : request.getSupportsThinking())
                    .contextWindow(request.getContextWindow())
                    .status(normalizeStatus(request.getStatus()))
                    .createTime(now)
                    .updateTime(now)
                    .build();

            if (aiClientApiDao.insert(api) <= 0 || aiClientModelDao.insert(model) <= 0) {
                throw new IllegalStateException("模型配置写入失败");
            }
            invalidateLlmCatalog();
            return success(true);
        } catch (IllegalArgumentException ex) {
            return failure(ex.getMessage());
        } catch (Exception ex) {
            return failure(ex.getMessage() == null ? "新增模型失败" : ex.getMessage());
        }
    }

    @PostMapping("/sub-agents")
    public Response<Boolean> createSubAgent(@RequestBody CatalogSubAgentCreateRequestDTO request) {
        try {
            return catalogService.createSubAgent(request);
        } catch (IllegalArgumentException ex) {
            return failure(ex.getMessage());
        }
    }

    @PostMapping("/mcps")
    @Transactional
    public Response<Boolean> createMcp(@RequestBody CatalogMcpCreateRequestDTO request) {
        try {
            String mcpId = requireText(request == null ? null : request.getMcpId(), "MCP ID 不能为空");
            String mcpName = requireText(request == null ? null : request.getMcpName(), "MCP 名称不能为空");
            if (aiClientToolMcpDao.queryByMcpId(mcpId) != null) {
                throw new IllegalArgumentException("MCP ID 已存在: " + mcpId);
            }
            AiClientToolMcp mcp = AiClientToolMcp.builder()
                    .mcpId(mcpId)
                    .mcpName(mcpName)
                    .transportType(defaultIfBlank(request.getTransportType(), "streamable_http"))
                    .transportConfig(request.getTransportConfig())
                    .requestTimeout(request.getRequestTimeout() == null ? 5 : request.getRequestTimeout())
                    .status(normalizeStatus(request.getStatus()))
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            if (aiClientToolMcpDao.insert(mcp) <= 0) {
                throw new IllegalStateException("MCP 写入失败");
            }
            reloadMcpRuntimeQuietly();
            return success(true);
        } catch (IllegalArgumentException ex) {
            return failure(ex.getMessage());
        } catch (Exception ex) {
            return failure(ex.getMessage() == null ? "新增 MCP 失败" : ex.getMessage());
        }
    }

    @GetMapping("/capabilities")
    public Response<CatalogCapabilitiesResponseDTO> capabilities() {
        return catalogService.listCapabilities();
    }

    @PostMapping(value = "/skills/parse-package", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<Map<String, Object>> parseSkillPackage(@RequestPart("file") MultipartFile file) {
        try {
            return success(skillPackageService.previewZipAsMap(readPackage(file)));
        } catch (Exception ex) {
            return failure(ex.getMessage() == null ? "技能包解析失败" : ex.getMessage());
        }
    }

    @PostMapping(value = "/skills/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<Map<String, Object>> uploadSkill(@RequestPart("file") MultipartFile file) {
        try {
            return success(skillPackageService.installZip(
                    readPackage(file),
                    file.getOriginalFilename() == null ? "skill.zip" : file.getOriginalFilename(),
                    false));
        } catch (Exception ex) {
            return failure(ex.getMessage() == null ? "技能包安装失败" : ex.getMessage());
        }
    }

    @PostMapping("/skills/create")
    public Response<Map<String, Object>> createSkill(@RequestBody CreateSkillBody body) {
        try {
            return success(skillPackageService.installFromMarkdown(
                    body == null ? null : body.name,
                    body == null ? null : body.description,
                    body == null ? null : body.content,
                    false));
        } catch (Exception ex) {
            return failure(ex.getMessage() == null ? "技能创建失败" : ex.getMessage());
        }
    }

    @PostMapping("/skills/import-url")
    public Response<Map<String, Object>> importSkill(@RequestBody ImportSkillBody body) {
        try {
            return success(skillPackageService.installFromUrl(body == null ? null : body.url, false));
        } catch (Exception ex) {
            return failure(ex.getMessage() == null ? "技能导入失败" : ex.getMessage());
        }
    }

    private String resolveApiId(String requestedApiId, String modelId) {
        String base = defaultIfBlank(requestedApiId, "api-" + modelId);
        if (aiClientApiDao.queryByApiId(base) == null) {
            return base;
        }
        String suffix = "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        int maxBaseLength = Math.max(1, 64 - suffix.length());
        return base.substring(0, Math.min(base.length(), maxBaseLength)) + suffix;
    }

    private void invalidateLlmCatalog() {
        LlmModelCatalog catalog = llmModelCatalogProvider.getIfAvailable();
        if (catalog != null) {
            catalog.invalidateAll();
        }
    }

    private void reloadMcpRuntimeQuietly() {
        if (mcpRegistry == null) {
            return;
        }
        try {
            mcpRegistry.preloadAllEnabledMcps();
        } catch (Exception ignored) {
            // 新配置已持久化；运行时刷新失败由后续 reload 或管理员处理。
        }
    }

    private static byte[] readPackage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new SkillLoadException("技能包不能为空");
        }
        if (file.getSize() > SkillPackageParser.MAX_PACKAGE_BYTES) {
            throw new SkillLoadException("技能包不能超过 32MB");
        }
        return file.getBytes();
    }

    private static int normalizeStatus(Integer status) {
        return status != null && status == 0 ? 0 : 1;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private static <T> Response<T> failure(String message) {
        return Response.<T>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(message == null || message.isBlank() ? ResponseCode.ILLEGAL_PARAMETER.getInfo() : message)
                .data(null)
                .build();
    }

    @Data
    private static final class CreateSkillBody {
        private String name;
        private String description;
        private String content;
    }

    @Data
    private static final class ImportSkillBody {
        private String url;
    }
}
