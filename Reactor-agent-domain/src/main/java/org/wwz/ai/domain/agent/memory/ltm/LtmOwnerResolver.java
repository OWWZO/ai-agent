package org.wwz.ai.domain.agent.memory.ltm;

import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.types.agent.visitor.VisitorRequestContext;

/**
 * 解析策展记忆 owner：优先 userId/erp，其次 visitorId，最后 thread-local visitor。
 */
public final class LtmOwnerResolver {

    private LtmOwnerResolver() {
    }

    public static LtmOwner resolve(String visitorId, String userIdOrErp) {
        if (StringUtils.isNotBlank(userIdOrErp)) {
            return LtmOwner.user(userIdOrErp.trim());
        }
        if (StringUtils.isNotBlank(visitorId)) {
            return LtmOwner.visitor(visitorId.trim());
        }
        String current = VisitorRequestContext.currentVisitorId();
        if (StringUtils.isNotBlank(current)) {
            return LtmOwner.visitor(current.trim());
        }
        return LtmOwner.visitor("anonymous");
    }
}
