package org.wwz.ai.domain.agent.adapter.repository;

import org.wwz.ai.domain.agent.runtime.llm.LlmModelBinding;

import java.util.List;

/**
 * 可出站 LLM 模型配置仓储端口。
 * <p>
 * 数据源为管理台维护的 {@code ai_client_api} + {@code ai_client_model}；
 * domain 只消费已拼装好的绑定，不触碰 DAO。
 */
public interface ILlmModelConfigRepository {

    /**
     * 启用且配齐 baseUrl / apiKey 的模型绑定（可直接用于出站）。
     */
    List<LlmModelBinding> listUsable();
}
