package org.wwz.ai.domain.agent.runtime.llm;

/**
 * 请求协议分支标识。
 * function_call：Spring AI 原生 tools[]；struct_parse：schema 写入 system + JSON 解析；
 * text：无 tools 的纯文本 ask。
 */
public enum LlmAskToolProtocol {
    FUNCTION_CALL,
    STRUCT_PARSE,
    TEXT
}
