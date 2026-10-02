package org.wwz.ai.domain.agent.adapter.port;

public interface KernelBrowserSessionPort {

    boolean isConfigured();

    KernelBrowserSession resolveForOwner(String ownerKey);

    KernelBrowserSessionStatus statusForOwner(String ownerKey);

    KernelBrowserLiveView ensureLiveView(String ownerKey);

    void deleteForOwner(String ownerKey);
}
