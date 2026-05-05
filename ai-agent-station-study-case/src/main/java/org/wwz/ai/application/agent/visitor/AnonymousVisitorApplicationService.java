package org.wwz.ai.application.agent.visitor;

import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.visitor.model.AnonymousVisitorIdentity;
import org.wwz.ai.domain.agent.adapter.repository.IAnonymousVisitorRepository;
import org.wwz.ai.domain.agent.visitor.entity.AnonymousVisitorRecord;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 匿名访客身份应用服务。
 */
@Service
@RequiredArgsConstructor
public class AnonymousVisitorApplicationService {

    private static final int STATUS_ACTIVE = 1;

    private final IAnonymousVisitorRepository anonymousVisitorRepository;

    /**
     * 解析现有 token，找不到或失效时自动创建新访客并换发 token。
     */
    public AnonymousVisitorIdentity resolveOrCreate(String rawToken, String userAgent, String ip) {
        if (StringUtils.isNotBlank(rawToken)) {
            AnonymousVisitorRecord existing = anonymousVisitorRepository.queryByTokenDigest(sha256(rawToken));
            if (existing != null && Integer.valueOf(STATUS_ACTIVE).equals(existing.getStatus())) {
                anonymousVisitorRepository.touchVisitor(
                        existing.getVisitorId(),
                        LocalDateTime.now(),
                        ip,
                        userAgent
                );
                return AnonymousVisitorIdentity.builder()
                        .visitorId(existing.getVisitorId())
                        .rawToken(rawToken)
                        .newlyCreated(false)
                        .build();
            }
        }

        String visitorId = "visitor_" + UUID.randomUUID().toString().replace("-", "");
        String newToken = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        anonymousVisitorRepository.save(AnonymousVisitorRecord.builder()
                .visitorId(visitorId)
                .tokenDigest(sha256(newToken))
                .status(STATUS_ACTIVE)
                .firstSeenAt(now)
                .lastSeenAt(now)
                .lastIp(ip)
                .lastUserAgent(userAgent)
                .deleted(0)
                .build());
        return AnonymousVisitorIdentity.builder()
                .visitorId(visitorId)
                .rawToken(newToken)
                .newlyCreated(true)
                .build();
    }

    private String sha256(String rawToken) {
        return DigestUtils.sha256Hex(StringUtils.defaultString(rawToken));
    }
}
