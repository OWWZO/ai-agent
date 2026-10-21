package org.wwz.ai.types.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "reactor.opencli")
public class OpenCliProperties {

    private boolean enabled = true;

    private String command = "opencli";

    private List<String> prefixArgs = new ArrayList<>();

    private List<String> extraArgs = new ArrayList<>(List.of("-f", "json"));

    private long defaultTimeoutMs = 180_000L;

    private long maxTimeoutMs = 540_000L;

    private int maxOutputChars = 200_000;

    private String cacheDir = "";

    private String relayUrl = "";

    /**
     * 浏览器驱动后端（021）：opencli（默认，行为与改造前一致）或 bsk。
     * 值透传给 CLI 子进程的环境变量 BROWSER_BACKEND，由 adapter-host 的
     * getBrowserFactory() 解析；未知值由 CLI 侧报错，Java 不校验语义。
     *
     * <p>它只管「opencli 的站点适配器跑在哪个后端上」，<b>不</b>管 browser
     * 工具暴露哪套命令——那是 {@link #surface}。</p>
     */
    private String backend = "";

    /**
     * browser 工具的命令面（021）：""（默认，opencli 站点适配器）或 "bsk"。
     *
     * <p>两套命令面互不重叠：</p>
     * <ul>
     *   <li>opencli 面：{@code list} / {@code <site> <command>}（169 个站点适配器）；
     *       底层后端由 {@link #backend} 决定（可以是 bsk）。</li>
     *   <li>bsk 面：{@code navigate} / {@code click} / {@code observe} / {@code fill} …
     *       bsk 原生命令，{@code @eN} 引用语义；进程换成 bsk CLI。</li>
     * </ul>
     *
     * <p>设成 "bsk" 后，browser 工具<b>只</b>调用 bsk 命令：opencli 的站点
     * 适配器命令（{@code list}、{@code <site> --help}、{@code external register}）
     * 全部不可用，避免模型在两套词汇之间串台。</p>
     */
    private String surface = "";

    /**
     * bsk 命令面下执行的命令（通常是 node）。留空 = 复用 {@link #command}。
     */
    private String bskCommand = "";

    /**
     * bsk 命令面下 CLI 的入口参数（指向 bsk-cli.js）。
     *
     * <p>留空表示 bsk 命令面未配置：browser 工具会直接返回配置错误，
     * 不会退回去跑 opencli——那正是「两套命令串台」要避免的事。</p>
     */
    private List<String> bskPrefixArgs = new ArrayList<>();

    /**
     * bsk 模式下 CLI 连接的 bsk WS 网关地址（wss://host/...）。
     *
     * <p>留空时由 Java 按本机端口 + {@link #bskCliPath} 拼出
     * {@code ws://127.0.0.1:<port><bskCliPath>}，本机部署无需配置。</p>
     *
     * <p>注入给 CLI 的变量是 {@code OPENCLI_BSK_RELAY_URL}（新变量），
     * <b>不是</b> {@code OPENCLI_RELAY_URL}：后者在 opencli 侧是 relay 的
     * HTTP 语义（POST /internal/browser/rpc），而 bsk 侧要求 ws(s)://，
     * 同一变量两套语义会互相打断（见 specs/021 KNOWN-FIXES P0-3）。</p>
     */
    private String bskRelayUrl = "";

    /**
     * bsk 网关的 <b>CLI 侧</b> WS 端点路径。
     *
     * <p>与扩展侧路径（{@code /api/agent/browser/bsk-relay}）分开：扩展侧
     * 走配对凭证（子协议/查询串里的 token），CLI 侧走
     * {@code X-Adapter-Secret} + {@code user_id} 查询参数，两套鉴权不能混。</p>
     *
     * <p>本字段是 CLI 侧路径的唯一事实来源：Java 网关用它注册端点，
     * BrowserTool 用它拼出 CLI 该连的地址。</p>
     */
    private String bskCliPath = "/api/agent/browser/bsk-cli";
}
