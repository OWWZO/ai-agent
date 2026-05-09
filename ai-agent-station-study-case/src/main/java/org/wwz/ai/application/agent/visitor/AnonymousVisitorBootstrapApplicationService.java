package org.wwz.ai.application.agent.visitor;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.wwz.ai.application.agent.visitor.model.AnonymousVisitorProfile;

/**
 * 匿名访客首页 bootstrap 应用服务。
 */
@Service
@RequiredArgsConstructor
public class AnonymousVisitorBootstrapApplicationService {

    private final AnonymousVisitorNamingApplicationService anonymousVisitorNamingApplicationService;

    /**
     * 返回当前 visitor 首屏进入所需的最小状态视图。
     */
    public AnonymousVisitorProfile bootstrap(String visitorId) {
        return anonymousVisitorNamingApplicationService.queryCurrentVisitorProfile(visitorId);
    }
}
