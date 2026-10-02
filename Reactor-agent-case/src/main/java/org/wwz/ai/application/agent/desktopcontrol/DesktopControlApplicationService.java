package org.wwz.ai.application.agent.desktopcontrol;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.adapter.port.RemoteHttpPort;
import org.wwz.ai.domain.agent.reactor.config.ReactorConfig;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.DesktopControlObservationSupport;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.DesktopControlRecord;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.DesktopControlStatuses;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.DesktopSessionRemote;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.IDesktopControlRepository;
import org.wwz.ai.types.agent.visitor.VisitorRequestContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DesktopControlApplicationService {

    private final IDesktopControlRepository desktopControlRepository;
    private final RemoteHttpPort remoteHttpPort;
    private final ReactorConfig reactorConfig;

    public Map<String, Object> complete(String controlId) {
        if (StringUtils.isBlank(controlId)) {
            throw new IllegalArgumentException("controlId 不能为空");
        }
        String visitorId = VisitorRequestContext.currentVisitorId();
        DesktopControlRecord existing = desktopControlRepository.findByControlId(controlId.trim())
                .orElse(null);
        if (existing == null) {
            return rejected(controlId, "桌面控制不存在");
        }
        if (StringUtils.isNotBlank(existing.getVisitorId())
                && StringUtils.isNotBlank(visitorId)
                && !existing.getVisitorId().equals(visitorId)) {
            return rejected(controlId, "无权完成该桌面控制");
        }
        if (DesktopControlStatuses.RESUME_PENDING.equals(existing.getStatus())
                || DesktopControlStatuses.RESUMING.equals(existing.getStatus())
                || DesktopControlStatuses.COMPLETED.equals(existing.getStatus())) {
            closeQuietly(existing);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("controlId", controlId);
            result.put("accepted", true);
            result.put("idempotent", true);
            result.put("resumeRequestId", existing.getResumeRequestId());
            result.put("status", existing.getStatus());
            result.put("message", "桌面操作已完成，请连接 resume SSE 继续");
            return result;
        }
        if (!DesktopControlStatuses.PENDING.equals(existing.getStatus())) {
            return rejected(controlId, "桌面控制不存在或已结束");
        }

        String resumeRequestId = "resume_" + UUID.randomUUID().toString().replace("-", "");
        boolean ok = desktopControlRepository.casCompletePending(
                controlId.trim(), visitorId, resumeRequestId);
        if (!ok) {
            DesktopControlRecord latest = desktopControlRepository.findByControlId(controlId.trim()).orElse(existing);
            if (StringUtils.isNotBlank(latest.getResumeRequestId())) {
                closeQuietly(latest);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("controlId", controlId);
                result.put("accepted", true);
                result.put("idempotent", true);
                result.put("resumeRequestId", latest.getResumeRequestId());
                result.put("status", latest.getStatus());
                result.put("message", "桌面操作已完成，请连接 resume SSE 继续");
                return result;
            }
            return rejected(controlId, "桌面控制不存在或已结束");
        }
        closeQuietly(existing);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("controlId", controlId);
        result.put("accepted", true);
        result.put("idempotent", false);
        result.put("resumeRequestId", resumeRequestId);
        result.put("status", DesktopControlStatuses.RESUME_PENDING);
        result.put("sessionId", existing.getSessionId());
        result.put("message", "桌面操作已完成，请连接 resume SSE 继续执行");
        return result;
    }

    public List<Map<String, Object>> listPending(String sessionId) {
        return desktopControlRepository.listOpenBySessionId(sessionId).stream()
                .map(DesktopControlObservationSupport::toClientPayload)
                .collect(Collectors.toList());
    }

    public Map<String, Object> cancel(String controlId, String reason) {
        String visitorId = VisitorRequestContext.currentVisitorId();
        DesktopControlRecord existing = StringUtils.isBlank(controlId)
                ? null
                : desktopControlRepository.findByControlId(controlId.trim()).orElse(null);
        boolean ok = desktopControlRepository.casCancel(controlId, visitorId);
        if (ok && existing != null) {
            closeQuietly(existing);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("controlId", controlId);
        result.put("cancelled", ok);
        result.put("reason", reason);
        return result;
    }

    private void closeQuietly(DesktopControlRecord record) {
        if (record == null) {
            return;
        }
        DesktopSessionRemote.closeQuietly(
                remoteHttpPort,
                reactorConfig,
                record.getSourceRequestId(),
                record.getSessionId(),
                record.getOwnerKey());
    }

    private static Map<String, Object> rejected(String controlId, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("controlId", controlId);
        result.put("accepted", false);
        result.put("message", message);
        return result;
    }
}
