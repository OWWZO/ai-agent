package org.wwz.ai.trigger.http.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.application.agent.file.ConversationFileApplicationService;
import org.wwz.ai.domain.agent.model.valobj.ConversationUploadedFile;
import org.wwz.ai.trigger.http.agent.vo.AgentFileUploadRespVO;
import org.wwz.ai.types.agent.visitor.VisitorRequestContext;
import org.wwz.ai.types.enums.ResponseCode;

import javax.annotation.Resource;

/**
 * 对话附件上传 Controller。
 * 仅依赖 case 编排，不直连 infrastructure 网关。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/file")
public class AgentFileController {

    @Resource
    private ConversationFileApplicationService conversationFileApplicationService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<AgentFileUploadRespVO> upload(@RequestParam("sessionId") String sessionId,
                                                  @RequestParam("file") MultipartFile file) {
        if (!StringUtils.hasText(sessionId)) {
            return Response.<AgentFileUploadRespVO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("sessionId不能为空")
                    .build();
        }
        if (file == null || file.isEmpty()) {
            return Response.<AgentFileUploadRespVO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("上传文件不能为空")
                    .build();
        }

        try {
            ConversationUploadedFile uploaded = conversationFileApplicationService.upload(
                    VisitorRequestContext.requireVisitorId(),
                    sessionId,
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getSize(),
                    file.getInputStream()
            );
            AgentFileUploadRespVO respVO = new AgentFileUploadRespVO();
            BeanUtils.copyProperties(uploaded, respVO);
            return Response.<AgentFileUploadRespVO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(respVO)
                    .build();
        } catch (Exception e) {
            log.error("上传对话附件失败 sessionId={}, fileName={}", sessionId,
                    file == null ? null : file.getOriginalFilename(), e);
            return Response.<AgentFileUploadRespVO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(StringUtils.hasText(e.getMessage()) ? e.getMessage() : ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }
}
