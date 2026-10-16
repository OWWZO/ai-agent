package org.wwz.ai.domain.agent.runtime.tasklist;

/**
 * 后台子 Agent 终态与父 SSE 流的**传输层**收口策略（2026-10-16 对齐 ZCode 交互模型后定稿）。
 * <p>
 * 两层已分离，本开关只管辖传输层：
 * <ul>
 *   <li><b>业务态（无条件解绑）</b>：root result 帧恒打 {@code finish=true}，前端据此立即
 *       收口 loading（{@code planRootResultLoading}），不再等后台。这层没有开关。</li>
 *   <li><b>传输层（本开关）</b>：{@code true}（默认）时后台在飞则投影不 complete、
 *       父 SSE 保持打开，直到 {@code stream_settle}（background_idle）才关——
 *       后台结算 {@code tool_result} 经活连接**推送**给前端，父卡秒级刷新，
 *       等价于 ZCode 订阅存活期的 delta 推送。{@code false} 时全解耦：
 *       root result 即 complete 关流，父卡改由前端冷路径（replay 重拉）刷新。</li>
 * </ul>
 * 灰度期可一键切换；稳定后 flag=false 分支可删。
 * <p>
 * 配置项：{@code autobots.autoagent.stream.defer-background-settle}（默认 true），
 * 由 {@code ReactorConfig} 在启动时写入本持有者（消费方均为静态/普通类，无法注入 Bean）。
 */
public final class StreamSettlePolicy {

    private static volatile boolean deferBackgroundSettle = true;

    private StreamSettlePolicy() {
    }

    public static boolean deferBackgroundSettle() {
        return deferBackgroundSettle;
    }

    public static void setDeferBackgroundSettle(boolean deferBackgroundSettle) {
        StreamSettlePolicy.deferBackgroundSettle = deferBackgroundSettle;
    }
}
