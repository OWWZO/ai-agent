package org.wwz.ai.domain.agent.runtime.tool;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 单工具 observation 预算：超限把完整内容落到工作区，只回一个可续读的信封。
 * <p>
 * 对齐 zCode 的 {@code resultBudget} + {@code <persisted-output>}。落本地工作区而不是上传文件服务，
 * 是因为模型有 {@code workspace_read}，可以直接续读 —— 这才是“可执行的续读指令”。
 * 落盘失败一律退回截断，绝不丢内容。
 */
@Slf4j
public final class ToolObservationBudget {

    /** grep / glob / list 上限，对齐 zCode 的 MAX_GREP_MODEL_BYTES。 */
    public static final int GREP_GLOB_LIST_CAP = 20_000;

    /** read 上限：正文本身是模型要用的，比检索类宽松一些。 */
    public static final int READ_CAP = 30_000;

    /** 信封里的预览字符数，对齐 zCode 的 PERSISTED_OUTPUT_PREVIEW_CHARS。 */
    static final int PREVIEW_CHARS = 2_000;

    private static final Map<String, Integer> CAPS_BY_TOOL = Map.of(
            "workspace_grep", GREP_GLOB_LIST_CAP,
            "workspace_glob", GREP_GLOB_LIST_CAP,
            "workspace_list", GREP_GLOB_LIST_CAP,
            "workspace_read", READ_CAP
    );

    private static final String OBSERVATION_DIR = ".reactor/observations";

    /** 批量工具并行执行时同一毫秒可能撞名，序号保证文件互不覆盖。 */
    private static final java.util.concurrent.atomic.AtomicLong PERSIST_SEQ =
            new java.util.concurrent.atomic.AtomicLong();

    private ToolObservationBudget() {
    }

    /**
     * 未注册工具或工作区不可用时返回原文本（截断由 {@code maxObserve} 兜底）。
     */
    public static String apply(AgentContext context, String toolName, String observation) {
        if (context == null || StringUtils.isBlank(observation) || toolName == null) {
            return observation;
        }
        Integer cap = CAPS_BY_TOOL.get(toolName);
        if (cap == null || observation.length() <= cap) {
            return observation;
        }
        Path file = persist(context, toolName, observation);
        if (file == null) {
            return ToolObservationSerializer.truncateForLlm(observation, cap);
        }
        String relative = context.getWorkspaceRoot() == null
                ? file.toString()
                : relativize(Path.of(context.getWorkspaceRoot()), file);
        return envelope(observation.length(), relative, observation.substring(0, PREVIEW_CHARS));
    }

    private static Path persist(AgentContext context, String toolName, String observation) {
        if (StringUtils.isBlank(context.getWorkspaceRoot())) {
            return null;
        }
        try {
            Path dir = Path.of(context.getWorkspaceRoot()).resolve(OBSERVATION_DIR);
            Files.createDirectories(dir);
            Path file = dir.resolve(toolName + "-" + System.currentTimeMillis()
                    + "-" + PERSIST_SEQ.incrementAndGet() + ".txt");
            Files.writeString(file, observation, StandardCharsets.UTF_8);
            return file;
        } catch (Exception e) {
            log.warn("persist oversized observation failed, tool={}", toolName, e);
            return null;
        }
    }

    private static String envelope(int length, String path, String preview) {
        return "<persisted-output>\n"
                + "Output too large (" + (length / 1024) + " KB). Full output saved to: " + path + "\n\n"
                + "Preview (first 2 KB):\n"
                + preview + "\n"
                + "</persisted-output>";
    }

    private static String relativize(Path root, Path file) {
        try {
            return root.toAbsolutePath().normalize()
                    .relativize(file.toAbsolutePath().normalize())
                    .toString()
                    .replace('\\', '/');
        } catch (Exception e) {
            return file.toString();
        }
    }
}
