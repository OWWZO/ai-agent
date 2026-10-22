package org.wwz.ai.domain.agent.memory.ltm;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.types.agent.user.UserRequestContext;

/**
 * 解析策展记忆 owner：优先 userId，其次 ERP 标识，再回退到请求上下文。
 */
public final class LtmOwnerResolver {

    private LtmOwnerResolver() {
    }

    public static LtmOwner resolve(String userId, String erp) {
        String resolvedUserId = StringUtils.defaultIfBlank(userId, UserRequestContext.currentUserId());
        resolvedUserId = StringUtils.defaultIfBlank(resolvedUserId, erp);
        return LtmOwner.user(StringUtils.defaultIfBlank(resolvedUserId, "anonymous"));
    }
}
