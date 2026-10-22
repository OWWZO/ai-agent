package org.wwz.ai.trigger.http.catalog;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.wwz.ai.api.dto.CatalogCapabilitiesResponseDTO;
import org.wwz.ai.api.dto.CatalogMcpCreateRequestDTO;
import org.wwz.ai.api.dto.CatalogModelCreateRequestDTO;
import org.wwz.ai.api.dto.CatalogModelResponseDTO;
import org.wwz.ai.api.dto.CatalogSubAgentCreateRequestDTO;
import org.wwz.ai.api.dto.CatalogSubAgentResponseDTO;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.catalog.ApplicationResult;
import org.wwz.ai.application.catalog.capability.CatalogCapabilitiesApplicationService;
import org.wwz.ai.application.catalog.mcp.McpCatalogApplicationService;
import org.wwz.ai.application.catalog.model.ModelCatalogApplicationService;
import org.wwz.ai.application.catalog.skill.SkillCatalogApplicationService;
import org.wwz.ai.application.catalog.subagent.SubAgentCatalogApplicationService;
import org.wwz.ai.trigger.http.catalog.mapper.CatalogCommandMapper;
import org.wwz.ai.trigger.http.catalog.mapper.CatalogResponseMapper;
import org.wwz.ai.types.enums.ResponseCode;

import java.io.IOException;
import java.util.Map;
import java.util.List;

/**
 * 登录用户可读的安全目录接口。
 *
 * <p>本入口只做 HTTP/API DTO 转换和 Case 调用；目录写入、校验、事务及运行时刷新由 Case 负责。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/catalog")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS
})
public class CatalogController {

    private final ModelCatalogApplicationService modelCatalogApplicationService;
    private final McpCatalogApplicationService mcpCatalogApplicationService;
    private final SkillCatalogApplicationService skillCatalogApplicationService;
    private final SubAgentCatalogApplicationService subAgentCatalogApplicationService;
    private final CatalogCapabilitiesApplicationService capabilitiesApplicationService;
    private final CatalogCommandMapper commandMapper;
    private final CatalogResponseMapper responseMapper;

    @GetMapping("/models")
    public Response<List<CatalogModelResponseDTO>> models() {
        return responseMapper.toModels(modelCatalogApplicationService.listModels());
    }

    @GetMapping("/sub-agents")
    public Response<List<CatalogSubAgentResponseDTO>> subAgents() {
        return responseMapper.toSubAgents(subAgentCatalogApplicationService.listSubAgents());
    }

    @PostMapping("/models")
    public Response<Boolean> createModel(@RequestBody CatalogModelCreateRequestDTO request) {
        return responseMapper.toBoolean(modelCatalogApplicationService.createModel(
                commandMapper.toModelCreateCommand(request)));
    }

    @PostMapping("/sub-agents")
    public Response<Boolean> createSubAgent(@RequestBody CatalogSubAgentCreateRequestDTO request) {
        return responseMapper.toBoolean(subAgentCatalogApplicationService.createSubAgent(
                commandMapper.toSubAgentCreateCommand(request)));
    }

    @PostMapping("/mcps")
    public Response<Boolean> createMcp(@RequestBody CatalogMcpCreateRequestDTO request) {
        return responseMapper.toBoolean(mcpCatalogApplicationService.createMcp(
                commandMapper.toMcpCreateCommand(request)));
    }

    @GetMapping("/capabilities")
    public Response<CatalogCapabilitiesResponseDTO> capabilities() {
        return responseMapper.toCapabilities(capabilitiesApplicationService.listCapabilities());
    }

    @PostMapping(value = "/skills/parse-package", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<Map<String, Object>> parseSkillPackage(@RequestPart("file") MultipartFile file) {
        try {
            byte[] packageBytes = readPackage(file);
            return responseMapper.toMap(skillCatalogApplicationService.previewSkillPackage(
                    commandMapper.toSkillPackageCommand(packageBytes, originalFilename(file))));
        } catch (Exception e) {
            return responseMapper.toMap(ApplicationResult.failure(
                    ResponseCode.ILLEGAL_PARAMETER,
                    messageOrDefault(e, "技能包解析失败"),
                    null));
        }
    }

    @PostMapping(value = "/skills/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<Map<String, Object>> uploadSkill(@RequestPart("file") MultipartFile file) {
        try {
            byte[] packageBytes = readPackage(file);
            return responseMapper.toMap(skillCatalogApplicationService.uploadSkill(
                    commandMapper.toSkillPackageCommand(packageBytes, originalFilename(file))));
        } catch (Exception e) {
            return responseMapper.toMap(ApplicationResult.failure(
                    ResponseCode.ILLEGAL_PARAMETER,
                    messageOrDefault(e, "技能包安装失败"),
                    null));
        }
    }

    @PostMapping("/skills/create")
    public Response<Map<String, Object>> createSkill(@RequestBody CreateSkillBody body) {
        return responseMapper.toMap(skillCatalogApplicationService.createSkill(
                commandMapper.toSkillCreateCommand(
                        body == null ? null : body.name,
                        body == null ? null : body.description,
                        body == null ? null : body.content)));
    }

    @PostMapping("/skills/import-url")
    public Response<Map<String, Object>> importSkill(@RequestBody ImportSkillBody body) {
        return responseMapper.toMap(skillCatalogApplicationService.importSkill(
                commandMapper.toSkillImportCommand(body == null ? null : body.url)));
    }

    private static byte[] readPackage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() <= 0) {
            throw new IllegalArgumentException("技能包不能为空");
        }
        if (file.getSize() > CatalogCommandMapper.MAX_PACKAGE_BYTES) {
            throw new IllegalArgumentException("技能包不能超过 32MB");
        }
        return file.getBytes();
    }

    private static String originalFilename(MultipartFile file) {
        return file == null || file.getOriginalFilename() == null ? "skill.zip" : file.getOriginalFilename();
    }

    private static String messageOrDefault(Exception exception, String fallback) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? fallback
                : exception.getMessage();
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
