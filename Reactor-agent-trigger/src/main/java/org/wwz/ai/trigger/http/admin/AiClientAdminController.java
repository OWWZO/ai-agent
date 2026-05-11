package org.wwz.ai.trigger.http.admin;

import org.wwz.ai.api.IAiClientAdminService;
import org.wwz.ai.api.dto.AiClientQueryRequestDTO;
import org.wwz.ai.api.dto.AiClientRequestDTO;
import org.wwz.ai.api.dto.AiClientResponseDTO;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.infrastructure.dao.IAiClientDao;
import org.wwz.ai.infrastructure.dao.po.AiClient;
import org.wwz.ai.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI客户端管理控制器
 * @description AI客户端配置管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ai-client")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class AiClientAdminController implements IAiClientAdminService {

    @Resource
    private IAiClientDao aiClientDao;

    @Override
    @PostMapping("/create")
    public Response<Boolean> createAiClient(@RequestBody AiClientRequestDTO request) {
        try {
            log.info("创建AI客户端配置请求：{}", request);

            // DTO转PO
            AiClient aiClient = convertToAiClient(request);
            aiClient.setCreateTime(LocalDateTime.now());
            aiClient.setUpdateTime(LocalDateTime.now());

            int result = aiClientDao.insert(aiClient);

            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("创建AI客户端配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @PutMapping("/update-by-id")
    public Response<Boolean> updateAiClientById(@RequestBody AiClientRequestDTO request) {
        try {
            log.info("根据ID更新AI客户端配置请求：{}", request);

            if (request.getId() == null) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("ID不能为空")
                        .data(false)
                        .build();
            }

            // DTO转PO
            AiClient aiClient = convertToAiClient(request);
            aiClient.setUpdateTime(LocalDateTime.now());

            int result = aiClientDao.updateById(aiClient);

            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据ID更新AI客户端配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @PutMapping("/update-by-client-id")
    public Response<Boolean> updateAiClientByClientId(@RequestBody AiClientRequestDTO request) {
        try {
            log.info("根据客户端ID更新AI客户端配置请求：{}", request);

            if (!StringUtils.hasText(request.getClientId())) {
                return Response.<Boolean>builder()
                        .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                        .info("客户端ID不能为空")
                        .data(false)
                        .build();
            }

            // DTO转PO
            AiClient aiClient = convertToAiClient(request);
            aiClient.setUpdateTime(LocalDateTime.now());

            int result = aiClientDao.updateByClientId(aiClient);

            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据客户端ID更新AI客户端配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @DeleteMapping("/delete-by-id/{id}")
    public Response<Boolean> deleteAiClientById(@PathVariable("id") Long id) {
        try {
            log.info("根据ID删除AI客户端配置请求：{}", id);

            int result = aiClientDao.deleteById(id);

            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据ID删除AI客户端配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @DeleteMapping("/delete-by-client-id/{clientId}")
    public Response<Boolean> deleteAiClientByClientId(@PathVariable("clientId") String clientId) {
        try {
            log.info("根据客户端ID删除AI客户端配置请求：{}", clientId);

            int result = aiClientDao.deleteByClientId(clientId);

            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(result > 0)
                    .build();
        } catch (Exception e) {
            log.error("根据客户端ID删除AI客户端配置失败", e);
            return Response.<Boolean>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(false)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-id/{id}")
    public Response<AiClientResponseDTO> queryAiClientById(@PathVariable("id") Long id) {
        try {
            log.info("根据ID查询AI客户端配置请求：{}", id);

            AiClient aiClient = aiClientDao.queryById(id);

            if (aiClient == null) {
                return Response.<AiClientResponseDTO>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("未找到对应的AI客户端配置")
                        .data(null)
                        .build();
            }

            // PO转DTO
            AiClientResponseDTO responseDTO = convertToAiClientResponseDTO(aiClient);

            return Response.<AiClientResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (Exception e) {
            log.error("根据ID查询AI客户端配置失败", e);
            return Response.<AiClientResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-by-client-id/{clientId}")
    public Response<AiClientResponseDTO> queryAiClientByClientId(@PathVariable("clientId") String clientId) {
        try {
            log.info("根据客户端ID查询AI客户端配置请求：{}", clientId);

            AiClient aiClient = aiClientDao.queryByClientId(clientId);

            if (aiClient == null) {
                return Response.<AiClientResponseDTO>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("未找到对应的AI客户端配置")
                        .data(null)
                        .build();
            }

            // PO转DTO
            AiClientResponseDTO responseDTO = convertToAiClientResponseDTO(aiClient);

            return Response.<AiClientResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTO)
                    .build();
        } catch (Exception e) {
            log.error("根据客户端ID查询AI客户端配置失败", e);
            return Response.<AiClientResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-enabled")
    public Response<List<AiClientResponseDTO>> queryEnabledAiClients() {
        try {
            log.info("查询所有启用的AI客户端配置");

            List<AiClient> aiClients = aiClientDao.queryEnabledClients();

            List<AiClientResponseDTO> responseDTOs = aiClients.stream()
                    .map(this::convertToAiClientResponseDTO)
                    .collect(Collectors.toList());

            return Response.<List<AiClientResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("查询所有启用的AI客户端配置失败", e);
            return Response.<List<AiClientResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @PostMapping("/query-list")
    public Response<List<AiClientResponseDTO>> queryAiClientList(@RequestBody AiClientQueryRequestDTO request) {
        try {
            log.info("根据条件查询AI客户端配置列表请求：{}", request);

            List<AiClient> aiClients;

            // 根据不同条件查询
            if (StringUtils.hasText(request.getClientId())) {
                AiClient aiClient = aiClientDao.queryByClientId(request.getClientId());
                aiClients = aiClient != null ? List.of(aiClient) : List.of();
            } else if (StringUtils.hasText(request.getClientName())) {
                aiClients = aiClientDao.queryByClientName(request.getClientName());
            } else {
                aiClients = aiClientDao.queryAll();
            }

            // 状态过滤
            if (request.getStatus() != null) {
                aiClients = aiClients.stream()
                        .filter(client -> request.getStatus().equals(client.getStatus()))
                        .collect(Collectors.toList());
            }

            // 分页处理（简单实现）
            if (request.getPageNum() != null && request.getPageSize() != null) {
                int start = (request.getPageNum() - 1) * request.getPageSize();
                int end = Math.min(start + request.getPageSize(), aiClients.size());
                if (start < aiClients.size()) {
                    aiClients = aiClients.subList(start, end);
                } else {
                    aiClients = List.of();
                }
            }

            List<AiClientResponseDTO> responseDTOs = aiClients.stream()
                    .map(this::convertToAiClientResponseDTO)
                    .collect(Collectors.toList());

            return Response.<List<AiClientResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("根据条件查询AI客户端配置列表失败", e);
            return Response.<List<AiClientResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    @Override
    @GetMapping("/query-all")
    public Response<List<AiClientResponseDTO>> queryAllAiClients() {
        try {
            log.info("查询所有AI客户端配置");

            List<AiClient> aiClients = aiClientDao.queryAll();

            List<AiClientResponseDTO> responseDTOs = aiClients.stream()
                    .map(this::convertToAiClientResponseDTO)
                    .collect(Collectors.toList());

            return Response.<List<AiClientResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(responseDTOs)
                    .build();
        } catch (Exception e) {
            log.error("查询所有AI客户端配置失败", e);
            return Response.<List<AiClientResponseDTO>>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .data(null)
                    .build();
        }
    }

    /**
     * DTO转PO对象
     */
    private AiClient convertToAiClient(AiClientRequestDTO requestDTO) {
        AiClient aiClient = new AiClient();
        BeanUtils.copyProperties(requestDTO, aiClient);
        return aiClient;
    }

    /**
     * PO转DTO对象
     */
    private AiClientResponseDTO convertToAiClientResponseDTO(AiClient aiClient) {
        AiClientResponseDTO responseDTO = new AiClientResponseDTO();
        BeanUtils.copyProperties(aiClient, responseDTO);
        return responseDTO;
    }

}
