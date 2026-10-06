package org.wwz.ai.application.agent.kernelbrowser;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.wwz.ai.domain.agent.adapter.port.KernelBrowserLiveView;
import org.wwz.ai.domain.agent.adapter.port.KernelBrowserSessionPort;
import org.wwz.ai.domain.agent.adapter.port.KernelBrowserSessionStatus;


@Service
public class KernelBrowserApplicationService {

    private final KernelBrowserSessionPort kernelBrowserSessionPort;

    public KernelBrowserApplicationService(ObjectProvider<KernelBrowserSessionPort> portProvider) {
        this.kernelBrowserSessionPort = portProvider.getIfAvailable();
    }

    public KernelBrowserLiveView ensure(String userId) {
        KernelBrowserSessionPort port = requireConfiguredPort(userId);
        return port.ensureLiveView(userId.trim());
    }

    public KernelBrowserSessionStatus status(String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (kernelBrowserSessionPort == null || !kernelBrowserSessionPort.isConfigured()) {
            return KernelBrowserSessionStatus.builder()
                    .exists(false)
                    .reconstructed(false)
                    .build();
        }
        return kernelBrowserSessionPort.statusForUser(userId);
    }

    public void reset(String userId) {
        KernelBrowserSessionPort port = requireConfiguredPort(userId);
        port.deleteForUser(userId.trim());
    }

    private KernelBrowserSessionPort requireConfiguredPort(String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (kernelBrowserSessionPort == null || !kernelBrowserSessionPort.isConfigured()) {
            throw new IllegalStateException("Kernel 云端浏览器未配置，请检查 reactor.kernel-browser.enabled 和 KERNEL_API_KEY");
        }
        return kernelBrowserSessionPort;
    }
}
