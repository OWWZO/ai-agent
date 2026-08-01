package org.wwz.ai.trigger.http.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.wwz.ai.api.IAiClientAdminService;
import org.wwz.ai.api.dto.AiClientQueryRequestDTO;
import org.wwz.ai.api.dto.AiClientRequestDTO;
import org.wwz.ai.api.dto.AiClientResponseDTO;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.admin.AiClientAdminApplicationService;
import org.wwz.ai.types.enums.ResponseCode;

import javax.annotation.Resource;
import java.util.List;

/**
 * AI客户端管理控制器。
 * 只依赖 case 应用服务，不再注入 infrastructure DAO。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ai-client")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class AiClientAdminController implements IAiClientAdminService {

    @Resource
    private AiClientAdminApplicationService aiClientAdminApplicationService;

    @Override
    @PostMapping("/create")
    public Response<Boolean> createAiClient(@RequestBody AiClientRequestDTO request) {
        try {
            log.info("创建AI客户端配置请求：{}", request);
            boolean ok = aiClientAdminApplicationService.create(request);
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ok)
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
            boolean ok = aiClientAdminApplicationService.updateById(request);
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ok)
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.<Boolean>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(e.getMessage())
                    .data(false)
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
            boolean ok = aiClientAdminApplicationService.updateByClientId(request);
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ok)
                    .build();
        } catch (IllegalArgumentException e) {
            return Response.<Boolean>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(e.getMessage())
                    .data(false)
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
            boolean ok = aiClientAdminApplicationService.deleteById(id);
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ok)
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
            boolean ok = aiClientAdminApplicationService.deleteByClientId(clientId);
            return Response.<Boolean>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(ok)
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
            AiClientResponseDTO data = aiClientAdminApplicationService.queryById(id);
            if (data == null) {
                return Response.<AiClientResponseDTO>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("未找到对应的AI客户端配置")
                        .data(null)
                        .build();
            }
            return Response.<AiClientResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(data)
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
            AiClientResponseDTO data = aiClientAdminApplicationService.queryByClientId(clientId);
            if (data == null) {
                return Response.<AiClientResponseDTO>builder()
                        .code(ResponseCode.UN_ERROR.getCode())
                        .info("未找到对应的AI客户端配置")
                        .data(null)
                        .build();
            }
            return Response.<AiClientResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(data)
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
            return Response.<List<AiClientResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(aiClientAdminApplicationService.queryEnabled())
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
            return Response.<List<AiClientResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(aiClientAdminApplicationService.queryList(request))
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
            return Response.<List<AiClientResponseDTO>>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(aiClientAdminApplicationService.queryAll())
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
}
