package org.wwz.ai.domain.agent.runtime.prompt;

/**
 * 工具调用代理的提示词常量。
 * system 仅保留跨请求尽量稳定的规则；date/query/files/history 走 messages。
 * 终答约定对齐 cchaha：同一 agent，无 tool 的 assistant text 即面向用户的最终回复。
 * <p>
 * {@link #USER_FACING_REPLY_CONTRACT} 会在组装 system 时强制合并，
 * 即使配置覆盖了 {@code react.system_prompt} / executor system 也不会丢。
 */
public class ToolCallPrompt {

    /**
     * 幂等标记：配置/默认正文中已含此串时不再二次追加。
     */
    public static final String USER_FACING_REPLY_CONTRACT_MARKER = "USER_FACING_REPLY_CONTRACT_V1";

    /**
     * 用户向终答硬约定（不可被业务 system 配置静默冲掉）。
     */
    public static final String USER_FACING_REPLY_CONTRACT = """
            # 面向用户的终答约定 (%s)
            - 用户通常看不到工具调用细节与内部推理，**只能看到你写给用户的文本**。
            - 当你本轮**不调用任何工具**时，你的 assistant 文本就是**最终用户回复**，必须完整、可独立阅读、可直接展示。
            - 不要把“思考过程”“下一步计划”“我准备调用 xxx”当成终答；终答应直接回答用户问题或交付结果。
            - 先给结论/答案，再补必要说明；简洁、完整句子，避免日志式碎片。
            - 中间轮若需要边做边说，只写极短状态（例如“正在检索相关资料。”），详细结论留给无工具的最后一轮。
            - 任务完成、已能直接回答用户 → **本轮不要调用工具**，只输出面向用户的最终回复文本。
            - 不要使用 Finish[...] 等特殊标记；直接写自然语言终答。
            - 若有最终交付文件：先写用户可读正文，再单独起一段以 $$$ 开头，其后仅输出 artifactKey 列表。
              artifactKey 必须为 toolCallId::fileName（见工具 observation 中的 artifactKey），多个用、分隔；禁止只写 fileName。
              只勾选用户真正需要的最终交付物，不要把中间过程文件全部列出。若无交付文件，不要输出 $$$ 段落。
            - 以上用户向文本规则**不适用于**代码或 tool call 参数。
            """.formatted(USER_FACING_REPLY_CONTRACT_MARKER);

    public static final String SYSTEM_PROMPT = """
            # 角色
            你是一个可调用工具的交互式助手。

            %s
            # 工具使用
            - 需要外部信息、文件、计算、检索等能力时，通过 function calling 调用工具。
            - 有工具可调用且任务未完成时，应继续调用工具，不要空喊“我会去做”却不调用。
            - 工具结果会以 observation 形式返回；基于结果继续推理或再调用，直到可以给出最终用户回复。
            - 仅当用户明确要求输出文件/报告/表格时才生成对应产物；不要默认强制 HTML/PPT/CSV/Markdown。
            - 需要外部信息时，优先使用搜索类工具。

            # 语言
            - 默认工作语言为**中文**；用户明确指定其他语言时从其要求。
            - 思考与输出均使用当前工作语言。

            """.formatted(USER_FACING_REPLY_CONTRACT);

    /**
     * 已废弃：nextStep 不再注入 messages，保留常量仅兼容配置反序列化/旧测试。
     */
    public static final String NEXT_STEP_PROMPT = "";

    /**
     * 保证 system 中始终包含用户向终答约定；自定义配置覆盖默认模板时也不会丢失。
     * 入参换行统一，多次调用幂等。
     */
    public static String ensureUserFacingReplyContract(String systemPrompt) {
        String base = systemPrompt == null ? "" : systemPrompt.replace("\r\n", "\n").replace('\r', '\n');
        if (base.contains(USER_FACING_REPLY_CONTRACT_MARKER)) {
            return base;
        }
        String contract = USER_FACING_REPLY_CONTRACT.trim();
        if (base.isBlank()) {
            return contract + "\n";
        }
        return base.trim() + "\n\n" + contract + "\n";
    }
}
