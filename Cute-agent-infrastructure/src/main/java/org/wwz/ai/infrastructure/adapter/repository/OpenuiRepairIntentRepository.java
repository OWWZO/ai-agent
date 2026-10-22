package org.wwz.ai.infrastructure.adapter.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.wwz.ai.domain.agent.adapter.repository.IOpenuiRepairIntentRepository;
import org.wwz.ai.domain.agent.runtime.openui.OpenUiRepairIntent;
import org.wwz.ai.infrastructure.dao.IOpenuiRepairIntentDao;
import org.wwz.ai.infrastructure.dao.po.OpenuiRepairIntentPO;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 意图仓储实现：覆盖式保存（先删后插），读取按 intent_index 升序。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OpenuiRepairIntentRepository implements IOpenuiRepairIntentRepository {

    private final IOpenuiRepairIntentDao repairIntentDao;

    @Override
    @Transactional
    public void saveAll(String sessionId, String messageId, String programHash,
                        List<OpenUiRepairIntent> intents, String model) {
        if (sessionId == null || messageId == null || programHash == null) {
            throw new IllegalArgumentException("保存意图缺少 key 三元组");
        }
        List<OpenUiRepairIntent> safeIntents = intents == null ? List.of() : intents;
        repairIntentDao.deleteByProgram(sessionId, messageId, programHash);
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < safeIntents.size(); i++) {
            OpenUiRepairIntent intent = safeIntents.get(i);
            if (intent == null || intent.searchText().isBlank()) {
                continue;
            }
            repairIntentDao.insert(OpenuiRepairIntentPO.builder()
                    .sessionId(sessionId)
                    .messageId(messageId)
                    .programHash(programHash)
                    .intentIndex(i)
                    .searchText(intent.searchText())
                    .replaceText(intent.replaceText())
                    .model(model)
                    .createdAt(now)
                    .build());
        }
    }

    @Override
    public List<OpenUiRepairIntent> listByProgram(String sessionId, String messageId, String programHash) {
        if (sessionId == null || messageId == null || programHash == null) {
            return List.of();
        }
        List<OpenuiRepairIntentPO> rows = repairIntentDao.listByProgram(sessionId, messageId, programHash);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<OpenUiRepairIntent> result = new ArrayList<>(rows.size());
        for (OpenuiRepairIntentPO row : rows) {
            if (row == null || row.getSearchText() == null) {
                continue;
            }
            result.add(new OpenUiRepairIntent(row.getSearchText(), row.getReplaceText()));
        }
        return result;
    }
}
