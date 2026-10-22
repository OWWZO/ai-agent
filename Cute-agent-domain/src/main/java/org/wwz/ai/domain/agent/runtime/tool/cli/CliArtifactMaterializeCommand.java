package org.wwz.ai.domain.agent.runtime.tool.cli;

import lombok.Builder;
import lombok.Value;
import org.wwz.ai.domain.agent.adapter.port.cli.CliArtifact;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;

import java.util.List;
import java.util.Set;

/**
 * 一次 CLI 调用的产物物化请求。
 * <p>
 * 调用方只描述"这次调用在哪个 workspace 里、CLI 报告了哪些文件、事件卡片用什么文案"，
 * 路径校验、登记、事件和 registry 绑定全部由 {@link CliArtifactMaterializer} 隐藏。
 */
@Value
@Builder
public class CliArtifactMaterializeCommand {

    AgentContext agentContext;

    /** 本次 CLI 子进程的 cwd，即 workspace 根目录。 */
    String workspaceRoot;

    /** CLI 执行结果明确报告的候选文件；优先于 workspace 前后快照。 */
    List<CliArtifact> reportedArtifacts;

    /** 前端 file 事件的 command 文案。 */
    String commandLabel;

    /**
     * 执行前的 workspace 文件快照；非空且 {@link #reportedArtifacts} 为空时才启用 diff 兜底。
     * 并发工具调用共享同一 workspace 时不要传，否则会把别的调用产生的文件登记到本次调用。
     */
    Set<String> baselineFiles;

    /**
     * 本次命令声明的产物输出目录（相对 workspace）。
     * 非空时 diff 兜底只扫描该目录，避免把整个 workspace 的无关文件登记成产物。
     */
    String outputRoot;

    /** 当前调用最终拥有的 workspace 相对 capture root；Browser 类调用必须设置。 */
    String captureRoot;

    /** capture root 的内部绝对路径，仅用于本地边界校验，不进入对外结果。 */
    String captureRootAbsolute;

    /** 单次调用最多登记的文件数。 */
    int maxFiles;

    /** 单文件最大字节数。 */
    long maxFileBytes;

    /** 单次调用登记文件的总字节上限。 */
    long maxTotalBytes;
}
