package org.wwz.ai.domain.agent.adapter.repository;

import org.wwz.ai.domain.agent.visitor.entity.AnonymousVisitorRecord;

import java.time.LocalDateTime;

/**
 * 匿名访客仓储端口。
 */
public interface IAnonymousVisitorRepository {

    AnonymousVisitorRecord queryByTokenDigest(String tokenDigest);

    AnonymousVisitorRecord queryByVisitorId(String visitorId);

    void save(AnonymousVisitorRecord record);

    void touchVisitor(String visitorId, LocalDateTime lastSeenAt, String lastIp, String lastUserAgent);

    boolean bindUsernameIfAbsent(String visitorId, String username, LocalDateTime updateTime);
}
