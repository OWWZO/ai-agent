package org.wwz.ai.domain.agent.memory.ltm;

import lombok.Value;

/**
 * 策展记忆主身份键。
 */
@Value
public class LtmOwner {
    LtmOwnerType type;
    String id;

    public static LtmOwner of(LtmOwnerType type, String id) {
        if (type == null) {
            throw new IllegalArgumentException("owner type required");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("owner id required");
        }
        return new LtmOwner(type, id.trim());
    }

    public static LtmOwner user(String userId) {
        return of(LtmOwnerType.USER, userId);
    }

    public static LtmOwner visitor(String visitorId) {
        return of(LtmOwnerType.VISITOR, visitorId);
    }
}
