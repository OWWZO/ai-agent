package org.wwz.ai.infrastructure.adapter.repository;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.DesktopControlRecord;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.DesktopControlStatuses;
import org.wwz.ai.domain.agent.runtime.desktopcontrol.IDesktopControlRepository;
import org.wwz.ai.infrastructure.dao.reactor.IDesktopControlDao;
import org.wwz.ai.infrastructure.dao.reactor.po.DesktopControlPO;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class DesktopControlRepository implements IDesktopControlRepository {

    private final IDesktopControlDao desktopControlDao;

    @Override
    public void insert(DesktopControlRecord record) {
        if (record == null) {
            return;
        }
        desktopControlDao.insert(toPo(record));
    }

    @Override
    public Optional<DesktopControlRecord> findByControlId(String controlId) {
        if (StringUtils.isBlank(controlId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(toRecord(desktopControlDao.selectByControlId(controlId.trim())));
    }

    @Override
    public Optional<DesktopControlRecord> findByResumeRequestId(String resumeRequestId) {
        if (StringUtils.isBlank(resumeRequestId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(toRecord(desktopControlDao.selectByResumeRequestId(resumeRequestId.trim())));
    }

    @Override
    public List<DesktopControlRecord> listOpenBySessionId(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return List.of();
        }
        List<DesktopControlPO> rows = desktopControlDao.selectOpenBySessionId(sessionId.trim());
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream().map(this::toRecord).collect(Collectors.toList());
    }

    @Override
    public boolean hasOpenBySessionId(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return false;
        }
        Integer count = desktopControlDao.countOpenBySessionId(sessionId.trim());
        return count != null && count > 0;
    }

    @Override
    public boolean casCompletePending(String controlId, String userId, String resumeRequestId) {
        if (StringUtils.isBlank(controlId) || StringUtils.isBlank(resumeRequestId)) {
            return false;
        }
        return desktopControlDao.casCompletePending(
                controlId.trim(),
                StringUtils.trimToNull(userId),
                resumeRequestId.trim()) > 0;
    }

    @Override
    public boolean casClaimResume(String resumeRequestId, String userId) {
        if (StringUtils.isBlank(resumeRequestId)) {
            return false;
        }
        return desktopControlDao.casClaimResume(resumeRequestId.trim(), StringUtils.trimToNull(userId)) > 0;
    }

    @Override
    public boolean markCompleted(String controlId) {
        if (StringUtils.isBlank(controlId)) {
            return false;
        }
        return desktopControlDao.markCompleted(controlId.trim()) > 0;
    }

    @Override
    public boolean markStatus(String controlId, String status) {
        if (StringUtils.isBlank(controlId) || StringUtils.isBlank(status)) {
            return false;
        }
        return desktopControlDao.markFailed(controlId.trim(), status.trim()) > 0;
    }

    @Override
    public boolean casCancel(String controlId, String userId) {
        if (StringUtils.isBlank(controlId)) {
            return false;
        }
        return desktopControlDao.casCancel(
                controlId.trim(),
                StringUtils.trimToNull(userId),
                DesktopControlStatuses.CANCELABLE) > 0;
    }

    private DesktopControlPO toPo(DesktopControlRecord record) {
        DesktopControlPO po = new DesktopControlPO();
        po.setControlId(record.getControlId());
        po.setUserId(record.getUserId());
        po.setSessionId(record.getSessionId());
        po.setOwnerKey(record.getOwnerKey());
        po.setSourceRunId(record.getSourceRunId());
        po.setSourceRequestId(record.getSourceRequestId());
        po.setToolInvocationId(record.getToolInvocationId());
        po.setToolCallId(record.getToolCallId());
        po.setReason(record.getReason());
        po.setStreamUrl(record.getStreamUrl());
        po.setHoldUntil(record.getHoldUntil());
        po.setStatus(record.getStatus());
        po.setResumeRequestId(record.getResumeRequestId());
        po.setResumeContextJson(record.getResumeContextJson());
        return po;
    }

    private DesktopControlRecord toRecord(DesktopControlPO po) {
        if (po == null) {
            return null;
        }
        return DesktopControlRecord.builder()
                .id(po.getId())
                .controlId(po.getControlId())
                .userId(po.getUserId())
                .sessionId(po.getSessionId())
                .ownerKey(po.getOwnerKey())
                .sourceRunId(po.getSourceRunId())
                .sourceRequestId(po.getSourceRequestId())
                .toolInvocationId(po.getToolInvocationId())
                .toolCallId(po.getToolCallId())
                .reason(po.getReason())
                .streamUrl(po.getStreamUrl())
                .holdUntil(po.getHoldUntil())
                .status(po.getStatus())
                .resumeRequestId(po.getResumeRequestId())
                .resumeContextJson(po.getResumeContextJson())
                .build();
    }
}
