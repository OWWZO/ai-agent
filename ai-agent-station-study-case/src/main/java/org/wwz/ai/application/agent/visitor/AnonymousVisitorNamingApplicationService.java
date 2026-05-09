package org.wwz.ai.application.agent.visitor;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.visitor.model.AnonymousVisitorProfile;
import org.wwz.ai.domain.agent.adapter.repository.IAnonymousVisitorRepository;
import org.wwz.ai.domain.agent.visitor.entity.AnonymousVisitorRecord;

import java.time.LocalDateTime;

/**
 * 匿名访客首次命名应用服务。
 */
@Service
@RequiredArgsConstructor
public class AnonymousVisitorNamingApplicationService {

    private static final int MAX_USERNAME_LENGTH = 32;

    private final IAnonymousVisitorRepository anonymousVisitorRepository;

    /**
     * 查询当前浏览器访客最小状态视图。
     */
    public AnonymousVisitorProfile queryCurrentVisitorProfile(String visitorId) {
        AnonymousVisitorRecord record = requireVisitor(visitorId);
        return toProfile(record);
    }

    /**
     * 为当前访客首次写入用户名；已命名访客不允许重复修改。
     */
    public AnonymousVisitorProfile bindUsername(String visitorId, String username) {
        String normalizedUsername = normalizeUsername(username);
        AnonymousVisitorRecord current = requireVisitor(visitorId);
        if (StringUtils.isNotBlank(current.getUsername())) {
            throw new IllegalStateException("当前访客已命名，不可修改");
        }
        boolean updated = anonymousVisitorRepository.bindUsernameIfAbsent(
                visitorId,
                normalizedUsername,
                LocalDateTime.now()
        );
        if (!updated) {
            throw new IllegalStateException("当前访客已命名，不可修改");
        }
        return toProfile(requireVisitor(visitorId));
    }

    private AnonymousVisitorRecord requireVisitor(String visitorId) {
        if (StringUtils.isBlank(visitorId)) {
            throw new IllegalArgumentException("visitorId不能为空");
        }
        AnonymousVisitorRecord record = anonymousVisitorRepository.queryByVisitorId(visitorId);
        if (record == null) {
            throw new IllegalStateException("当前访客不存在");
        }
        return record;
    }

    private String normalizeUsername(String username) {
        String normalized = StringUtils.trimToEmpty(username);
        if (StringUtils.isBlank(normalized)) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (normalized.length() > MAX_USERNAME_LENGTH) {
            throw new IllegalArgumentException("用户名长度不能超过32个字符");
        }
        return normalized;
    }

    private AnonymousVisitorProfile toProfile(AnonymousVisitorRecord record) {
        return AnonymousVisitorProfile.builder()
                .visitorId(record.getVisitorId())
                .username(record.getUsername())
                .named(StringUtils.isNotBlank(record.getUsername()))
                .build();
    }
}
