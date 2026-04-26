package org.wwz.ai.trigger.http.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.wwz.ai.api.response.Response;
import org.wwz.ai.domain.agent.model.valobj.ConversationRoleVO;
import org.wwz.ai.domain.agent.model.valobj.FixRoleVO;
import org.wwz.ai.domain.agent.reactor.model.history.ConversationEventDetail;
import org.wwz.ai.domain.agent.reactor.model.history.ConversationTurnDetail;
import org.wwz.ai.domain.agent.reactor.service.IAgentConversationService;
import org.wwz.ai.domain.agent.reactor.entity.AgentConversation;
import org.wwz.ai.domain.agent.service.IFixRoleService;
import org.wwz.ai.trigger.http.agent.vo.*;
import org.wwz.ai.types.enums.ResponseCode;

import javax.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    @Resource
    private IFixRoleService fixRoleService;

    /**
     * 会话列表
     */
    @GetMapping("/list")
    public Response<PageRespVO<ConversationListRespVO>> list(
            HttpServletRequest request,
            @RequestParam(name = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize) {
        String deviceId = resolveDeviceId(request);
        List<AgentConversation> conversations = conversationService.listConversations(deviceId, null, pageNo, pageSize);
        int total = conversationService.countConversations(deviceId, null);

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
            HttpServletRequest request,
            @RequestParam("sessionId") String sessionId) {
        String deviceId = resolveDeviceId(request);
        AgentConversation conversation = conversationService.getAccessibleConversation(sessionId, deviceId, null);
        if (conversation == null) {
            return Response.<ConversationDetailRespVO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info("会话不存在")
                    .build();
        }

        List<ConversationTurnRespVO> turnVOs = conversationService.getConversationTurns(sessionId, deviceId, null).stream()
                .map(this::toTurnVO)
                .collect(Collectors.toList());

        return Response.<ConversationDetailRespVO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info("success")
                .data(ConversationDetailRespVO.builder()
                        .conversation(toListVO(conversation))
                        .turns(turnVOs)
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
        FixRoleVO roleVO = resolveCreateRole(reqVO);
        if ("chat".equalsIgnoreCase(reqVO.getProductType()) && roleVO == null) {
            return Response.<ConversationListRespVO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(StringUtils.hasText(reqVO.getAiAgentId()) ? "角色不可用" : "当前暂无可用角色")
                    .build();
        }
        AgentConversation conversation = conversationService.createConversation(
                reqVO.getSessionId(), deviceId, reqVO.getTitle(),
                reqVO.getAgentType(), reqVO.getProductType(),
                roleVO != null ? roleVO.getAgentId() : null,
                roleVO != null ? roleVO.getAgentName() : null);
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
        ConversationRoleVO roleVO = conversationService.buildConversationRole(c);
        return ConversationListRespVO.builder()
                .id(c.getId())
                .sessionId(c.getSessionId())
                .title(c.getTitle())
                .agentType(c.getAgentType())
                .productType(c.getProductType())
                .messageCount(c.getMessageCount())
                .pinned(c.getPinned())
                .lastMessagePreview(c.getLastMessagePreview())
                .role(toConversationRoleRespVO(roleVO))
                .createTime(c.getCreateTime())
                .updateTime(c.getUpdateTime())
                .build();
    }

    private ConversationRoleRespVO toConversationRoleRespVO(ConversationRoleVO roleVO) {
        if (roleVO == null) {
            return null;
        }
        return ConversationRoleRespVO.builder()
                .agentId(roleVO.getAgentId())
                .agentName(roleVO.getAgentName())
                .available(roleVO.isAvailable())
                .defaultRole(roleVO.isDefaultRole())
                .build();
    }

    private FixRoleVO resolveCreateRole(ConversationCreateReqVO reqVO) {
        if (!"chat".equalsIgnoreCase(reqVO.getProductType())) {
            return null;
        }
        if (StringUtils.hasText(reqVO.getAiAgentId())) {
            return fixRoleService.queryRole(reqVO.getAiAgentId());
        }
        return fixRoleService.queryDefaultRole();
    }

    private ConversationTurnRespVO toTurnVO(ConversationTurnDetail turn) {
        return ConversationTurnRespVO.builder()
                .requestId(turn.getRequestId())
                .sortOrder(turn.getSortOrder())
                .query(turn.getQuery())
                .files(turn.getFiles())
                .generatedFiles(turn.getGeneratedFiles())
                .agentType(turn.getAgentType())
                .response(turn.getResponse())
                .status(turn.getStatus())
                .forceStop(turn.getForceStop())
                .metrics(turn.getMetrics())
                .startedAt(turn.getStartedAt())
                .finishedAt(turn.getFinishedAt())
                .events(turn.getEvents().stream().map(this::toEventVO).collect(Collectors.toList()))
                .build();
    }

    private ConversationEventRespVO toEventVO(ConversationEventDetail event) {
        return ConversationEventRespVO.builder()
                .seqNo(event.getSeqNo())
                .eventType(event.getEventType())
                .eventSubType(event.getEventSubType())
                .displayArea(event.getDisplayArea())
                .taskId(event.getTaskId())
                .taskOrder(event.getTaskOrder())
                .messageIdExt(event.getMessageIdExt())
                .title(event.getTitle())
                .contentText(event.getContentText())
                .status(event.getStatus())
                .isFinal(event.getIsFinal())
                .payload(toPayloadMap(event.getPayload()))
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toPayloadMap(Object payload) {
        if (!(payload instanceof Map)) {
            return null;
        }

        Map<String, Object> payloadMap = new LinkedHashMap<>((Map<String, Object>) payload);
        Object artifactRefs = payloadMap.get("artifactRefs");
        if (artifactRefs instanceof List) {
            List<ArtifactReferenceRespVO> normalizedRefs = ((List<?>) artifactRefs).stream()
                    .map(this::toArtifactRefVO)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            payloadMap.put("artifactRefs", normalizedRefs);
        }
        return payloadMap;
    }

    private ArtifactReferenceRespVO toArtifactRefVO(Object artifactRef) {
        if (!(artifactRef instanceof Map)) {
            return null;
        }

        Map<?, ?> refMap = (Map<?, ?>) artifactRef;
        return ArtifactReferenceRespVO.builder()
                .artifactType(stringValue(refMap.get("artifactType")))
                .displayName(stringValue(refMap.get("displayName")))
                .resourceKey(stringValue(refMap.get("resourceKey")))
                .downloadUrl(stringValue(refMap.get("downloadUrl")))
                .previewUrl(stringValue(refMap.get("previewUrl")))
                .fileSize(longValue(refMap.get("fileSize")))
                .mimeType(stringValue(refMap.get("mimeType")))
                .missing(booleanValue(refMap.get("missing")))
                .missingReason(stringValue(refMap.get("missingReason")))
                .build();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }
}
