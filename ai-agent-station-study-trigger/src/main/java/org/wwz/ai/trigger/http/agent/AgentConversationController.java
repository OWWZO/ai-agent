package org.wwz.ai.trigger.http.agent;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.domain.agent.reactor.service.IAgentConversationService;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.reactor.entity.AgentMessage;
import org.wwz.ai.trigger.http.agent.vo.*;
import org.wwz.ai.types.enums.ResponseCode;

import javax.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 会话管理 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/conversation")
public class AgentConversationController {

    @Resource
    private IAgentConversationService conversationService;

    /**
     * 会话列表
     */
    @GetMapping("/list")
    public Response<PageRespVO<ConversationListRespVO>> list(
            @RequestParam(name = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        List<AgentConversation> conversations = conversationService.listConversations(null, null, pageNo, pageSize);
        int total = conversationService.countConversations(null, null);

        List<ConversationListRespVO> list = conversations.stream()
                .map(this::toListVO)
                .collect(Collectors.toList());

        return Response.<PageRespVO<ConversationListRespVO>>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info("success")
                .data(PageRespVO.<ConversationListRespVO>builder().total(total).list(list).build())
                .build();
    }

    /**
     * 会话详情(含所有消息)
     */
    @GetMapping("/detail")
    public Response<ConversationDetailRespVO> detail(
            @RequestParam("sessionId") String sessionId) {
        AgentConversation conversation = conversationService.getBySessionId(sessionId);
        if (conversation == null) {
            return Response.<ConversationDetailRespVO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("会话不存在")
                    .build();
        }

        List<AgentMessage> messages = conversationService.getConversationMessages(sessionId);
        List<MessageRespVO> messageVOs = messages.stream()
                .map(this::toMessageVO)
                .collect(Collectors.toList());

        return Response.<ConversationDetailRespVO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info("success")
                .data(ConversationDetailRespVO.builder()
                        .conversation(toListVO(conversation))
                        .messages(messageVOs)
                        .build())
                .build();
    }

    /**
     * 创建会话
     */
    @PostMapping("/create")
    public Response<ConversationListRespVO> create(
            HttpServletRequest request,
            @RequestBody ConversationCreateReqVO reqVO) {
        String deviceId = resolveDeviceId(request);
        AgentConversation conversation = conversationService.createConversation(
                reqVO.getSessionId(), deviceId, reqVO.getTitle(),
                reqVO.getAgentType(), reqVO.getProductType());
        return Response.<ConversationListRespVO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info("success")
                .data(toListVO(conversation))
                .build();
    }

    /**
     * 重命名
     */
    @PutMapping("/rename")
    public Response<Boolean> rename(
            HttpServletRequest request,
            @RequestBody ConversationRenameReqVO reqVO) {
        String deviceId = resolveDeviceId(request);
        conversationService.renameConversation(reqVO.getSessionId(), deviceId, reqVO.getTitle());
        return Response.<Boolean>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info("success")
                .data(true)
                .build();
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/{sessionId}")
    public Response<Boolean> delete(
            HttpServletRequest request,
            @PathVariable String sessionId) {
        String deviceId = resolveDeviceId(request);
        conversationService.deleteConversation(sessionId, deviceId);
        return Response.<Boolean>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info("success")
                .data(true)
                .build();
    }

    /**
     * 置顶/取消置顶
     */
    @PutMapping("/pin")
    public Response<Boolean> pin(
            HttpServletRequest request,
            @RequestParam("sessionId") String sessionId,
            @RequestParam("pinned") boolean pinned) {
        String deviceId = resolveDeviceId(request);
        conversationService.togglePin(sessionId, deviceId, pinned);
        return Response.<Boolean>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info("success")
                .data(true)
                .build();
    }

    /**
     * 匿名会话迁移到用户
     */
    @PostMapping("/migrate")
    public Response<Integer> migrate(
            HttpServletRequest request,
            @RequestParam("userId") Long userId) {
        String deviceId = resolveDeviceId(request);
        int count = conversationService.migrateToUser(deviceId, userId);
        return Response.<Integer>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info("success")
                .data(count)
                .build();
    }

    // ---- 私有方法 ----

    private String resolveDeviceId(HttpServletRequest request) {
        String deviceId = request.getHeader("X-Device-Id");
        if (deviceId == null || deviceId.isBlank()) {
            deviceId = request.getParameter("deviceId");
        }
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalArgumentException("X-Device-Id header is required");
        }
        return deviceId;
    }

    private ConversationListRespVO toListVO(AgentConversation c) {
        return ConversationListRespVO.builder()
                .id(c.getId())
                .sessionId(c.getSessionId())
                .title(c.getTitle())
                .agentType(c.getAgentType())
                .productType(c.getProductType())
                .messageCount(c.getMessageCount())
                .pinned(c.getPinned())
                .lastMessagePreview(c.getLastMessagePreview())
                .createTime(c.getCreateTime())
                .updateTime(c.getUpdateTime())
                .build();
    }

    private MessageRespVO toMessageVO(AgentMessage m) {
        return MessageRespVO.builder()
                .requestId(m.getRequestId())
                .sessionId(m.getSessionId())
                .sortOrder(m.getSortOrder())
                .query(m.getQuery())
                .agentType(m.getAgentType())
                .status(m.getStatus())
                .forceStop(m.getForceStop())
                .response(m.getResponse())
                .thought(m.getThought())
                .planJson(m.getPlanJson())
                .tasksJson(m.getTasksJson())
                .multiAgentJson(m.getMultiAgentJson())
                .conclusionJson(m.getConclusionJson())
                .planListJson(m.getPlanListJson())
                .renderSnapshotJson(m.getRenderSnapshotJson())
                .metricsJson(m.getMetricsJson())
                .filesJson(m.getFilesJson())
                .startedAt(m.getStartedAt())
                .finishedAt(m.getFinishedAt())
                .createTime(m.getCreateTime())
                .build();
    }
}
