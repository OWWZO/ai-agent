package org.wwz.ai.types.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Code Mode（GraalJS 同 JVM 执行）配置。
 * <p>
 * 默认关闭：本期只做能力装配，开关由部署环境显式打开。
 * 所有上限都是硬限制，超限时返回结构化错误，不静默截断。
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "cute.code-mode")
public class CodeModeProperties {

    private boolean enabled = false;

    private long defaultTimeoutMs = 180_000L;

    private long maxTimeoutMs = 540_000L;

    /** 单段源码最大字符数（source 与 workspaceFile 读出的源码都受此限制）。 */
    private int maxSourceChars = 262_144;

    /** main 返回值序列化后的最大字符数。 */
    private int maxOutputChars = 1_048_576;

    /** 单次执行允许的内部 capability 调用总次数。 */
    private int maxToolCalls = 100;

    /** 单次执行累计写回 workspace 的字节预算。 */
    private long maxTotalMemoryBytes = 134_217_728L;

    /**
     * 堆水位熔断阈值（占最大堆比例）。堆使用超过该比例时终止本次执行。
     * 小于等于 0 表示关闭熔断。
     */
    private double maxHeapUsedRatio = 0.6D;

    /** 堆水位采样间隔（毫秒）。 */
    private long heapCheckIntervalMs = 100L;

    /** 熔断后拒绝新执行的冷却时长（毫秒）。 */
    private long heapTripCooldownMs = 30_000L;

    private Workspace workspace = new Workspace();

    @Data
    public static class Workspace {

        private long maxReadBytes = 33_554_432L;

        private long maxWriteBytes = 33_554_432L;

        private int maxCsvRows = 100_000;

        private int maxCsvColumns = 200;

        /** workspaceFile 只允许落在这些前缀下。 */
        private List<String> allowedScriptPrefixes = new ArrayList<>(List.of("scripts/", "jobs/"));

        /** 写操作默认前缀；不在其中时仍允许写会话 workspace，但必须由调用方显式声明。 */
        private List<String> allowedOutputPrefixes = new ArrayList<>(List.of("outputs/", "artifacts/"));
    }
}
