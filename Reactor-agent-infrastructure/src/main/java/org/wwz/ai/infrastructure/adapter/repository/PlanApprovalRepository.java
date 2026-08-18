package org.wwz.ai.infrastructure.adapter.repository;

import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;
import org.wwz.ai.domain.agent.runtime.planmode.IPlanApprovalRepository;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalDecision;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalRecord;
import org.wwz.ai.domain.agent.runtime.planmode.PlanApprovalStatuses;
import org.wwz.ai.infrastructure.dao.reactor.IPlanApprovalDao;
import org.wwz.ai.infrastructure.dao.reactor.po.PlanApprovalPO;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class PlanApprovalRepository implements IPlanApprovalRepository {

    private final IPlanApprovalDao planApprovalDao;

    @Override
    public void insert(PlanApprovalRecord record) {
        if (record == null) {
            return;
        }
        planApprovalDao.insert(toPo(record));
    }

    @Override
    public Optional<PlanApprovalRecord> findByApprovalId(String approvalId) {
        if (StringUtils.isBlank(approvalId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(toRecord(planApprovalDao.selectByApprovalId(approvalId.trim())));
    }

    @Override
    public Optional<PlanApprovalRecord> findByResumeRequestId(String resumeRequestId) {
        if (StringUtils.isBlank(resumeRequestId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(toRecord(planApprovalDao.selectByResumeRequestId(resumeRequestId.trim())));
    }

    @Override
    public List<PlanApprovalRecord> listOpenBySessionId(String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return List.of();
        }
        List<PlanApprovalPO> rows = planApprovalDao.selectOpenBySessionId(sessionId.trim());
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
        Integer count = planApprovalDao.countOpenBySessionId(sessionId.trim());
        return count != null && count > 0;
    }

    @Override
    public boolean casDecidePending(String approvalId, String visitorId, PlanApprovalDecision decision, String resumeRequestId) {
        if (StringUtils.isBlank(approvalId) || StringUtils.isBlank(resumeRequestId) || decision == null) {
            return false;
        }
        return planApprovalDao.casDecidePending(
                approvalId.trim(),
                StringUtils.trimToNull(visitorId),
                JSON.toJSONString(decision),
                resumeRequestId.trim()) > 0;
    }

    @Override
    public boolean casClaimResume(String resumeRequestId, String visitorId) {
        if (StringUtils.isBlank(resumeRequestId)) {
            return false;
        }
        return planApprovalDao.casClaimResume(resumeRequestId.trim(), StringUtils.trimToNull(visitorId)) > 0;
    }

    @Override
    public boolean markAnswered(String approvalId) {
        if (StringUtils.isBlank(approvalId)) {
            return false;
        }
        return planApprovalDao.markAnswered(approvalId.trim()) > 0;
    }

    @Override
    public boolean markStatus(String approvalId, String status) {
        if (StringUtils.isBlank(approvalId) || StringUtils.isBlank(status)) {
            return false;
        }
        return planApprovalDao.markFailed(approvalId.trim(), status.trim()) > 0;
    }

    @Override
    public boolean casCancel(String approvalId, String visitorId) {
        if (StringUtils.isBlank(approvalId)) {
            return false;
        }
        return planApprovalDao.casCancel(
                approvalId.trim(),
                StringUtils.trimToNull(visitorId),
                PlanApprovalStatuses.CANCELABLE) > 0;
    }

    @Override
    public int cancelBySourceRequestId(String sourceRequestId, String reason) {
        if (StringUtils.isBlank(sourceRequestId)) {
            return 0;
        }
        return planApprovalDao.cancelBySourceRequestId(sourceRequestId.trim(), PlanApprovalStatuses.CANCELABLE);
    }

    private PlanApprovalPO toPo(PlanApprovalRecord record) {
        PlanApprovalPO po = new PlanApprovalPO();
        po.setApprovalId(record.getApprovalId());
        po.setVisitorId(record.getVisitorId());
        po.setSessionId(record.getSessionId());
        po.setSourceRunId(record.getSourceRunId());
        po.setSourceRequestId(record.getSourceRequestId());
        po.setToolInvocationId(record.getToolInvocationId());
        po.setToolCallId(record.getToolCallId());
        po.setPlanContent(record.getPlanContent());
        po.setPlanFilePath(record.getPlanFilePath());
        po.setDecisionJson(record.getDecision() == null ? null : JSON.toJSONString(record.getDecision()));
        po.setStatus(record.getStatus());
        po.setExpiresAt(record.getExpiresAt());
        po.setResumeRequestId(record.getResumeRequestId());
        po.setResumeContextJson(record.getResumeContextJson());
        return po;
    }

    private PlanApprovalRecord toRecord(PlanApprovalPO po) {
        if (po == null) {
            return null;
        }
        PlanApprovalDecision decision = null;
        if (StringUtils.isNotBlank(po.getDecisionJson())) {
            decision = JSON.parseObject(po.getDecisionJson(), PlanApprovalDecision.class);
        }
        return PlanApprovalRecord.builder()
                .id(po.getId())
                .approvalId(po.getApprovalId())
                .visitorId(po.getVisitorId())
                .sessionId(po.getSessionId())
                .sourceRunId(po.getSourceRunId())
                .sourceRequestId(po.getSourceRequestId())
                .toolInvocationId(po.getToolInvocationId())
                .toolCallId(po.getToolCallId())
                .planContent(po.getPlanContent())
                .planFilePath(po.getPlanFilePath())
                .decision(decision)
                .status(po.getStatus())
                .expiresAt(po.getExpiresAt())
                .resumeRequestId(po.getResumeRequestId())
                .resumeContextJson(po.getResumeContextJson())
                .build();
    }
}
