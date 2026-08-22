package org.wwz.ai.application.agent.run;

/**
 * /run/follow 一次尝试的结果。PENDING 表示 ledger 仍 RUNNING 但进程内索引尚未就绪，
 * 观察流应保持打开并短轮询续绑，而不是立即 complete。
 */
public enum FollowAttachResult {
    ATTACHED,
    IDLE,
    PENDING
}
