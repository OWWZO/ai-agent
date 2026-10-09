package org.wwz.ai.domain.agent.ledger.replay.projector.impl;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.ledger.model.ArtifactView;
import org.wwz.ai.domain.agent.ledger.model.ToolInvocationView;
import org.wwz.ai.domain.agent.ledger.model.replay.ProjectedReplayEvent;
import org.wwz.ai.domain.agent.runtime.stream.AgentStreamAccumulator;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.DesktopControlObservationSupport;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.DesktopControlRecord;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.IDesktopControlRepository;
import org.wwz.ai.domain.agent.runtime.tool.common.planmode.RequestDesktopControlTool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RequestDesktopControlToolInvocationProjector extends AbstractToolInvocationProjector {

    private final IDesktopControlRepository desktopControlRepository;

    public RequestDesktopControlToolInvocationProjector() {
        this(null);
    }

    public RequestDesktopControlToolInvocationProjector(IDesktopControlRepository desktopControlRepository) {
        this.desktopControlRepository = desktopControlRepository;
    }

    @Override
    public boolean supports(String toolName) {
        return StringUtils.equalsIgnoreCase(RequestDesktopControlTool.NAME, toolName);
    }

    @Override
    public List<ProjectedReplayEvent> project(ToolInvocationView invocation,
                                              List<ArtifactView> artifacts,
                                              AgentStreamAccumulator state) {
        Map<String, Object> input = readMap(invocation == null ? null : invocation.getInputJson());
        Map<String, Object> observation = readMap(invocation == null ? null : invocation.getLlmObservation());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageType", "desktop_control");

        String controlId = stringValue(observation.get("controlId"));
        DesktopControlRecord persisted = findPersisted(controlId, invocation);
        String resolvedControlId = StringUtils.defaultIfBlank(
                controlId,
                persisted == null ? null : persisted.getControlId()
        );

        String reason = persisted == null ? null : persisted.getReason();
        if (StringUtils.isBlank(reason)) {
            reason = stringValue(observation.get("reason"));
        }
        if (StringUtils.isBlank(reason)) {
            reason = stringValue(input.get("reason"));
        }
        if (StringUtils.isNotBlank(reason)) {
            payload.put("reason", reason);
        }
        payload.put("input", input);

        String status = persisted == null
                ? (observation.containsKey("status") && "completed".equals(stringValue(observation.get("status")))
                ? "completed" : "pending")
                : DesktopControlObservationSupport.toClientStatus(persisted.getStatus());
        payload.put("status", status);
        if (persisted != null && StringUtils.isNotBlank(persisted.getStatus())) {
            payload.put("persistenceStatus", persisted.getStatus());
        }
        if (StringUtils.isNotBlank(resolvedControlId)) {
            payload.put("controlId", resolvedControlId);
        }

        return List.of(buildTaskEvent(
                state,
                invocation,
                "desktop_control",
                buildStructuredToolResponse(invocation, "desktop_control", payload),
                buildArtifactRefs(artifacts)
        ));
    }

    private DesktopControlRecord findPersisted(String controlId, ToolInvocationView invocation) {
        if (desktopControlRepository == null) {
            return null;
        }
        try {
            if (StringUtils.isNotBlank(controlId)) {
                DesktopControlRecord byId = desktopControlRepository.findByControlId(controlId).orElse(null);
                if (byId != null) {
                    return byId;
                }
            }
            if (invocation == null || StringUtils.isBlank(invocation.getSessionId())) {
                return null;
            }
            for (DesktopControlRecord record : desktopControlRepository.listOpenBySessionId(invocation.getSessionId())) {
                if (record == null) {
                    continue;
                }
                if (invocation.getId() != null && invocation.getId().equals(record.getToolInvocationId())) {
                    return record;
                }
                if (StringUtils.isNotBlank(invocation.getToolCallId())
                        && invocation.getToolCallId().equals(record.getToolCallId())) {
                    return record;
                }
            }
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
