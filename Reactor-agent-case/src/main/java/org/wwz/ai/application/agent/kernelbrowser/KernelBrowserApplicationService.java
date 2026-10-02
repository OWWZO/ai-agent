package org.wwz.ai.application.agent.kernelbrowser;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.adapter.port.BrowserOperationKind;
import org.wwz.ai.domain.agent.adapter.port.BrowserOperationLockPort;
import org.wwz.ai.domain.agent.adapter.port.BrowserOperationLockTimeoutException;
import org.wwz.ai.domain.agent.adapter.port.KernelBrowserLiveView;
import org.wwz.ai.domain.agent.adapter.port.KernelBrowserSessionPort;
import org.wwz.ai.domain.agent.adapter.port.KernelBrowserSessionStatus;

import java.util.Objects;
import java.util.concurrent.Callable;

@Service
public class KernelBrowserApplicationService {

    static final long ENSURE_RESET_LOCK_WAIT_MS = 60_000L;

    private final KernelBrowserSessionPort kernelBrowserSessionPort;
    private final BrowserOperationLockPort browserOperationLockPort;

    public KernelBrowserApplicationService(ObjectProvider<KernelBrowserSessionPort> portProvider,
                                           BrowserOperationLockPort browserOperationLockPort) {
        this.kernelBrowserSessionPort = portProvider.getIfAvailable();
        this.browserOperationLockPort = Objects.requireNonNull(browserOperationLockPort, "浏览器操作锁未装配");
    }

    public KernelBrowserLiveView ensure(String ownerKey) {
        KernelBrowserSessionPort port = requireConfiguredPort(ownerKey);
        String owner = ownerKey.trim();
        return callWithLock(owner, () -> port.ensureLiveView(owner));
    }

    public KernelBrowserSessionStatus status(String ownerKey) {
        if (StringUtils.isBlank(ownerKey)) {
            throw new IllegalArgumentException("访客身份不能为空");
        }
        if (kernelBrowserSessionPort == null || !kernelBrowserSessionPort.isConfigured()) {
            return KernelBrowserSessionStatus.builder()
                    .exists(false)
                    .reconstructed(false)
                    .build();
        }
        return kernelBrowserSessionPort.statusForOwner(ownerKey);
    }

    public void reset(String ownerKey) {
        KernelBrowserSessionPort port = requireConfiguredPort(ownerKey);
        String owner = ownerKey.trim();
        callWithLock(owner, () -> {
            port.deleteForOwner(owner);
            return null;
        });
    }

    private <T> T callWithLock(String owner, Callable<T> action) {
        try {
            return browserOperationLockPort.execute(
                    BrowserOperationKind.KERNEL_BROWSER, owner, ENSURE_RESET_LOCK_WAIT_MS, action);
        } catch (BrowserOperationLockTimeoutException e) {
            throw new IllegalStateException("云端浏览器正被占用，请稍后重试", e);
        }
    }

    private KernelBrowserSessionPort requireConfiguredPort(String ownerKey) {
        if (StringUtils.isBlank(ownerKey)) {
            throw new IllegalArgumentException("访客身份不能为空");
        }
        if (kernelBrowserSessionPort == null || !kernelBrowserSessionPort.isConfigured()) {
            throw new IllegalStateException("Kernel 云端浏览器未配置，请检查 reactor.kernel-browser.enabled 和 KERNEL_API_KEY");
        }
        return kernelBrowserSessionPort;
    }
}
