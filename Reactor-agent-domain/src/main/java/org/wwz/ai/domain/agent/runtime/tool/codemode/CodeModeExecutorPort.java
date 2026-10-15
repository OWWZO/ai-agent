package org.wwz.ai.domain.agent.runtime.tool.codemode;

import org.wwz.ai.domain.agent.runtime.agent.AgentContext;

/**
 * Code Mode 执行引擎的稳定接口。
 * <p>
 * domain 只依赖这个接口，执行器实现放在 infrastructure，
 * 本接口及其入参出参<b>不允许出现任何 GraalJS / Truffle 类型</b>，
 * 否则 GraalJS 依赖会顺着编译期传回 domain。
 * </p>
 */
public interface CodeModeExecutorPort {

    /**
     * 执行一段 JavaScript 或 workspace 内的 .js 脚本。
     *
     * @param request      已校验的执行请求
     * @param agentContext 当前 Agent 上下文（身份、workspace、取消令牌、账本）
     */
    CodeModeResult execute(CodeModeRequest request, AgentContext agentContext);
}
