package org.wwz.ai.domain.agent.adapter.port;

import java.util.concurrent.Callable;

public interface BrowserOperationLockPort {

    /**
     * 按浏览器类型和 owner 排队执行。等待超时或被中断时抛出 {@link BrowserOperationLockTimeoutException}，且不执行 action。
     */
    <T> T execute(BrowserOperationKind kind, String owner, long waitMillis, Callable<T> action);
}
