package org.wwz.ai.domain.agent.adapter.port;

public interface KernelBrowserSessionPort {

    boolean isConfigured();

    KernelBrowserSession resolveForUser(String userId);

    KernelBrowserSessionStatus statusForUser(String userId);

    KernelBrowserLiveView ensureLiveView(String userId);

    void deleteForUser(String userId);
}
