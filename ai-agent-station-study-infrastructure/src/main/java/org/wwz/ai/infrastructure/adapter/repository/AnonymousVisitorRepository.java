package org.wwz.ai.infrastructure.adapter.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.wwz.ai.domain.agent.adapter.repository.IAnonymousVisitorRepository;
import org.wwz.ai.domain.agent.visitor.entity.AnonymousVisitorRecord;
import org.wwz.ai.infrastructure.dao.po.VisitorIdentityPO;
import org.wwz.ai.infrastructure.dao.reactor.IVisitorIdentityDao;

import java.time.LocalDateTime;

/**
 * 匿名访客仓储实现。
 */
@Repository
@RequiredArgsConstructor
public class AnonymousVisitorRepository implements IAnonymousVisitorRepository {

    private final IVisitorIdentityDao visitorIdentityDao;

    @Override
    public AnonymousVisitorRecord queryByTokenDigest(String tokenDigest) {
        VisitorIdentityPO po = visitorIdentityDao.queryByTokenDigest(tokenDigest);
        return po == null ? null : toRecord(po);
    }

    @Override
    public AnonymousVisitorRecord queryByVisitorId(String visitorId) {
        VisitorIdentityPO po = visitorIdentityDao.queryByVisitorId(visitorId);
        return po == null ? null : toRecord(po);
    }

    @Override
    public void save(AnonymousVisitorRecord record) {
        visitorIdentityDao.insert(VisitorIdentityPO.builder()
                .visitorId(record.getVisitorId())
                .tokenDigest(record.getTokenDigest())
                .status(record.getStatus())
                .firstSeenAt(record.getFirstSeenAt())
                .lastSeenAt(record.getLastSeenAt())
                .lastIp(record.getLastIp())
                .lastUserAgent(record.getLastUserAgent())
                .username(record.getUsername())
                .deleted(record.getDeleted())
                .build());
    }

    @Override
    public void touchVisitor(String visitorId, LocalDateTime lastSeenAt, String lastIp, String lastUserAgent) {
        visitorIdentityDao.updateLastSeen(visitorId, lastSeenAt, lastIp, lastUserAgent);
    }

    @Override
    public boolean bindUsernameIfAbsent(String visitorId, String username, LocalDateTime updateTime) {
        return visitorIdentityDao.bindUsernameIfAbsent(visitorId, username, updateTime) > 0;
    }

    private AnonymousVisitorRecord toRecord(VisitorIdentityPO po) {
        return AnonymousVisitorRecord.builder()
                .id(po.getId())
                .visitorId(po.getVisitorId())
                .tokenDigest(po.getTokenDigest())
                .status(po.getStatus())
                .firstSeenAt(po.getFirstSeenAt())
                .lastSeenAt(po.getLastSeenAt())
                .lastIp(po.getLastIp())
                .lastUserAgent(po.getLastUserAgent())
                .username(po.getUsername())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .deleted(po.getDeleted())
                .build();
    }
}
