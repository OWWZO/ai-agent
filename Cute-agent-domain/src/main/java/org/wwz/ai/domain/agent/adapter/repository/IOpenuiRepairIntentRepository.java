package org.wwz.ai.domain.agent.adapter.repository;

import org.wwz.ai.domain.agent.runtime.openui.OpenUiRepairIntent;

import java.util.List;

/**
 * openui 修复编辑意图的仓储端口（plans/038 §10）。
 *
 * <p>key = sessionId + messageId + programHash：意图的 SEARCH 针对特定原文，
 * 原文 hash 是重放时判断"意图是否还有效"的依据。messageId 沿用会话的 requestId。</p>
 */
public interface IOpenuiRepairIntentRepository {

    /**
     * 覆盖式保存一个 key 下的全部意图（先删旧再插新，intent_index 按列表顺序 0..n）。
     * 只应保存前端验收通过的意图。
     */
    void saveAll(String sessionId, String messageId, String programHash,
                 List<OpenUiRepairIntent> intents, String model);

    /** 按 key 拉全部意图，按 intent_index 升序；无记录返回空列表。 */
    List<OpenUiRepairIntent> listByProgram(String sessionId, String messageId, String programHash);
}
