package org.wwz.ai.trigger.http.admin;

import org.wwz.ai.api.IAiClientModelAdminService;
import org.wwz.ai.api.dto.AiClientModelQueryRequestDTO;
import org.wwz.ai.api.dto.AiClientModelRequestDTO;
import org.wwz.ai.api.dto.AiClientModelResponseDTO;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.domain.agent.runtime.llm.LLMSettings;
import org.wwz.ai.domain.agent.runtime.llm.LlmChatModelResolver;
import org.wwz.ai.domain.agent.runtime.llm.LlmModelCatalog;
import org.wwz.ai.infrastructure.dao.IAiClientModelDao;
import org.wwz.ai.infrastructure.dao.po.AiClientModel;
import org.wwz.ai.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI客户端模型管理控制器
 * @description AI客户端模型配置管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ai-client-model")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class AiClientModelAdminController implements IAiClientModelAdminService {

    @Resource
    private IAiClientModelDao aiClientModelDao;

    @Resource
    private ObjectProvider<LlmModelCatalog> llmModelCatalogProvider;

    @Resource
    private ObjectProvider<LlmChatModelResolver> llmChatModelResolverProvider;

    /**
     * 真发一次极小请求测连接与延迟。
     * 失败不抛给网关：结果在 data.ok 里，便于前端展示。
     */
    @PostMapping("/test/{modelId}")
    public Response<Map<String, Object>> testConnection(@PathVariable("modelId") String modelId) {
        return testConnectionByReference(modelId);
    }

    @PostMapping("/test-by-id/{id}")
    public Response<Map<String, Object>> testConnectionById(@PathVariable("id") Long id) {
        return testConnectionByReference(LlmModelCatalog.BINDING_REF_PREFIX + id);
    }

    private Response<Map<String, Object>> testConnectionByReference(String modelReference) {
        Map<String, Object> body = new LinkedHashMap<>();
        LlmModelCatalog catalog = llmModelCatalogProvider.getIfAvailable();
        LlmChatModelResolver resolver = llmChatModelResolverProvider.getIfAvailable();
        if (catalog == null || resolver == null) {
            body.put("ok", false);
            body.put("ms", 0);
            body.put("message", "模型目录未装配");
            return Response.<Map<String, Object>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(body)
                    .build();
        }
        try {
            LLMSettings settings = catalog.resolve(modelReference)
                    .orElseThrow(() -> new IllegalStateException("找不到可用模型配置：" + modelReference + "（需启用且配齐 API Key）"));
            OpenAiChatModel chatModel = resolver.resolve(settings);
            long start = System.currentTimeMillis();
            chatModel.call(new Prompt(List.of(new UserMessage("ping"))));
            long ms = System.currentTimeMillis() - start;
            body.put("ok", true);
            body.put("ms", ms);
            body.put("message", "连接成功");
        } catch (Exception e) {
            log.warn("模型连接测试失败 modelReference={}", modelReference, e);
            body.put("ok", false);
            body.put("ms", 0);
            body.put("message", "连接失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
        return Response.<Map<String, Object>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(body)
                .build();
    }

    @Override
    @PostMapping("/create")
    public Response<Boolean> createAiClientModel(@RequestBody AiClientModelRequestDTO request) {
        try {
            log.info("创建AI客户端模型配置请求：{}", request);

            // DTO转PO
            AiClientModel aiClientModel = convertToAiClientModel(request);
            aiClientModel.setCreateTime(LocalDateTime.now());
            aiClientModel.setUpdateTime(LocalDateTime.now());

            int result = aiClientModelDao.insert(aiClientModel);
            if (result > 0) {
                invalidateLlmCatalog();
            }

            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("创建AI客户端模型配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @PutMapping("/update-by-id")
    public Response<Boolean> updateAiClientModelById(@RequestBody AiClientModelRequestDTO request) {
        try {
            log.info("根据ID更新AI客户端模型配置请求：{}", request);

            if (request.getId() == null) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("ID不能为空")
                        .data(false)
                        .build();
            }

            // DTO转PO
            AiClientModel aiClientModel = convertToAiClientModel(request);
            aiClientModel.setUpdateTime(LocalDateTime.now());

            int result = aiClientModelDao.updateById(aiClientModel);
            if (result > 0) {
                invalidateLlmCatalog();
            }

            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据ID更新AI客户端模型配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @PutMapping("/update-by-model-id")
    public Response<Boolean> updateAiClientModelByModelId(@RequestBody AiClientModelRequestDTO request) {
        try {
            log.info("根据模型ID更新AI客户端模型配置请求：{}", request);

            if (!StringUtils.hasText(request.getModelId())) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("模型ID不能为空")
                        .data(false)
                        .build();
            }

            if (request.getId() != null) {
                return updateAiClientModelById(request);
            }

            if (aiClientModelDao.queryAllByModelId(request.getModelId()).size() != 1) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("模型ID存在多条配置，请使用记录ID更新")
                        .data(false)
                        .build();
            }

            // DTO转PO
            AiClientModel aiClientModel = convertToAiClientModel(request);
            aiClientModel.setUpdateTime(LocalDateTime.now());

            int result = aiClientModelDao.updateByModelId(aiClientModel);
            if (result > 0) {
                invalidateLlmCatalog();
            }

            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据模型ID更新AI客户端模型配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @DeleteMapping("/delete-by-id/{id}")
    public Response<Boolean> deleteAiClientModelById(@PathVariable("id") Long id) {
        try {
            log.info("根据ID删除AI客户端模型配置请求：{}", id);

            int result = aiClientModelDao.deleteById(id);
            if (result > 0) {
                invalidateLlmCatalog();
            }

            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据ID删除AI客户端模型配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @DeleteMapping("/delete-by-model-id/{modelId}")
    public Response<Boolean> deleteAiClientModelByModelId(@PathVariable("modelId") String modelId) {
        try {
            log.info("根据模型ID删除AI客户端模型配置请求：{}", modelId);

            if (aiClientModelDao.queryAllByModelId(modelId).size() != 1) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("模型ID存在多条配置，请使用记录ID删除")
                        .data(false)
                        .build();
            }

            int result = aiClientModelDao.deleteByModelId(modelId);
            if (result > 0) {
                invalidateLlmCatalog();
            }

            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据模型ID删除AI客户端模型配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    private void invalidateLlmCatalog() {
        LlmModelCatalog catalog = llmModelCatalogProvider.getIfAvailable();
        if (catalog != null) {
            catalog.invalidateAll();
        }
    }

    @Override
    @GetMapping("/query-by-id/{id}")
    public Response<AiClientModelResponseDTO> queryAiClientModelById(@PathVariable("id") Long id) {
        try {
            log.info("根据ID查询AI客户端模型配置请求：{}", id);

            AiClientModel aiClientModel = aiClientModelDao.queryById(id);

            if (aiClientModel == null) {
                return Response.<AiClientModelResponseDTO>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("未找到对应的AI客户端模型配置")
                        .data(null)
                        .build();
            }

            // PO转DTO
            AiClientModelResponseDTO responseDTO = convertToAiClientModelResponseDTO(aiClientModel);

            return Response.<AiClientModelResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (Exception e) {
            log.error("根据ID查询AI客户端模型配置失败", e);
            return Response.<AiClientModelResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-model-id/{modelId}")
    public Response<AiClientModelResponseDTO> queryAiClientModelByModelId(@PathVariable("modelId") String modelId) {
        try {
            log.info("根据模型ID查询AI客户端模型配置请求：{}", modelId);

            AiClientModel aiClientModel = aiClientModelDao.queryByModelId(modelId);

            if (aiClientModel == null) {
                return Response.<AiClientModelResponseDTO>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("未找到对应的AI客户端模型配置")
                        .data(null)
                        .build();
            }

            // PO转DTO
            AiClientModelResponseDTO responseDTO = convertToAiClientModelResponseDTO(aiClientModel);

            return Response.<AiClientModelResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (Exception e) {
            log.error("根据模型ID查询AI客户端模型配置失败", e);
            return Response.<AiClientModelResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-api-id/{apiId}")
    public Response<List<AiClientModelResponseDTO>> queryAiClientModelsByApiId(@PathVariable("apiId") String apiId) {
        try {
            log.info("根据API配置ID查询AI客户端模型配置列表请求：{}", apiId);

            List<AiClientModel> aiClientModels = aiClientModelDao.queryByApiId(apiId);

            // PO转DTO
            List<AiClientModelResponseDTO> responseDTOs = aiClientModels.stream()
                    .map(this::convertToAiClientModelResponseDTO)
                    .collect(Collectors.toList());

            return Response.<List<AiClientModelResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("根据API配置ID查询AI客户端模型配置列表失败", e);
            return Response.<List<AiClientModelResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-model-type/{modelType}")
    public Response<List<AiClientModelResponseDTO>> queryAiClientModelsByModelType(@PathVariable("modelType") String modelType) {
        try {
            log.info("根据模型类型查询AI客户端模型配置列表请求：{}", modelType);

            List<AiClientModel> aiClientModels = aiClientModelDao.queryByModelType(modelType);

            // PO转DTO
            List<AiClientModelResponseDTO> responseDTOs = aiClientModels.stream()
                    .map(this::convertToAiClientModelResponseDTO)
                    .collect(Collectors.toList());

            return Response.<List<AiClientModelResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("根据模型类型查询AI客户端模型配置列表失败", e);
            return Response.<List<AiClientModelResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-enabled")
    public Response<List<AiClientModelResponseDTO>> queryEnabledAiClientModels() {
        try {
            log.info("查询所有启用的AI客户端模型配置请求");

            List<AiClientModel> aiClientModels = aiClientModelDao.queryEnabledModels();

            // PO转DTO
            List<AiClientModelResponseDTO> responseDTOs = aiClientModels.stream()
                    .map(this::convertToAiClientModelResponseDTO)
                    .collect(Collectors.toList());

            return Response.<List<AiClientModelResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("查询所有启用的AI客户端模型配置失败", e);
            return Response.<List<AiClientModelResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @PostMapping("/query-list")
    public Response<List<AiClientModelResponseDTO>> queryAiClientModelList(@RequestBody AiClientModelQueryRequestDTO request) {
        try {
            log.info("根据条件查询AI客户端模型配置列表请求：{}", request);

            List<AiClientModel> aiClientModels;

            // 根据不同条件查询
            if (StringUtils.hasText(request.getModelId())) {
                aiClientModels = aiClientModelDao.queryAllByModelId(request.getModelId());
            } else if (StringUtils.hasText(request.getApiId())) {
                aiClientModels = aiClientModelDao.queryByApiId(request.getApiId());
            } else if (StringUtils.hasText(request.getModelType())) {
                aiClientModels = aiClientModelDao.queryByModelType(request.getModelType());
            } else if (request.getStatus() != null) {
                if (request.getStatus() == 1) {
                    aiClientModels = aiClientModelDao.queryEnabledModels();
                } else {
                    aiClientModels = aiClientModelDao.queryAll();
                }
            } else {
                aiClientModels = aiClientModelDao.queryAll();
            }

            // PO转DTO
            List<AiClientModelResponseDTO> responseDTOs = aiClientModels.stream()
                    .map(this::convertToAiClientModelResponseDTO)
                    .collect(Collectors.toList());

            return Response.<List<AiClientModelResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("根据条件查询AI客户端模型配置列表失败", e);
            return Response.<List<AiClientModelResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-all")
    public Response<List<AiClientModelResponseDTO>> queryAllAiClientModels() {
        try {
            log.info("查询所有AI客户端模型配置请求");

            List<AiClientModel> aiClientModels = aiClientModelDao.queryAll();

            // PO转DTO
            List<AiClientModelResponseDTO> responseDTOs = aiClientModels.stream()
                    .map(this::convertToAiClientModelResponseDTO)
                    .collect(Collectors.toList());

            return Response.<List<AiClientModelResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("查询所有AI客户端模型配置失败", e);
            return Response.<List<AiClientModelResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    /**
     * DTO转PO对象
     */
    private AiClientModel convertToAiClientModel(AiClientModelRequestDTO requestDTO) {
        AiClientModel aiClientModel = new AiClientModel();
        BeanUtils.copyProperties(requestDTO, aiClientModel);
        return aiClientModel;
    }

    /**
     * PO转DTO对象
     */
    private AiClientModelResponseDTO convertToAiClientModelResponseDTO(AiClientModel aiClientModel) {
        AiClientModelResponseDTO responseDTO = new AiClientModelResponseDTO();
        BeanUtils.copyProperties(aiClientModel, responseDTO);
        return responseDTO;
    }

}
