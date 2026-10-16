package org.wwz.ai.domain.agent.runtime.tasklist;

/**
 * 后台子 Agent 终态与父 SSE 流的收口策略。
 * <p>
 * 对齐 ZCode「账本权威、流可断」模型前的行为切换开关：
 * <ul>
 *   <li>{@code false}（默认）：root result 帧即 finished、投影即 complete，父 SSE 流正常关闭；
 *       后台子 Agent 终态只写 Execution Ledger（{@code persistLedgerSettlement} 早已无条件落账），
 *       父卡终态由前端 history 重拉 / replay 兜底（最终一致）。</li>
 *   <li>{@code true}（旧行为）：后台任务在飞时父 result 不打 finish、投影不 complete，
 *       父流挂着直到 {@code stream_settle}——即 2026-10-15「转圈 6 分钟」bug 的土壤。</li>
 * </ul>
 * 灰度期可一键回滚；稳定后连同 stream_settle 发射路径一起删除。
 * <p>
 * 配置项：{@code autobots.autoagent.stream.defer-background-settle}（默认 false），
 * 由 {@code ReactorConfig} 在启动时写入本持有者（消费方均为静态/普通类，无法注入 Bean）。
 */
public final class StreamSettlePolicy {

    private static volatile boolean deferBackgroundSettle = false;

    private StreamSettlePolicy() {
    }

    public static boolean deferBackgroundSettle() {
        return deferBackgroundSettle;
    }

    public static void setDeferBackgroundSettle(boolean deferBackgroundSettle) {
        StreamSettlePolicy.deferBackgroundSettle = deferBackgroundSettle;
    }
}
