package org.wwz.ai.domain.agent.memory.ltm;

import java.util.List;

/**
 * 用户级有界策展记忆端口。禁止向量索引；仅全量快照注入。
 */
public interface CuratedMemoryStore {

    CuratedMemoryWriteResult add(LtmOwner owner,
                                 CuratedMemoryScope scope,
                                 String content,
                                 String sourceSessionId,
                                 String sourceRequestId,
                                 String writeOrigin);

    CuratedMemoryWriteResult replace(LtmOwner owner,
                                     CuratedMemoryScope scope,
                                     String oldText,
                                     String content,
                                     String sourceSessionId,
                                     String sourceRequestId,
                                     String writeOrigin);

    CuratedMemoryWriteResult remove(LtmOwner owner,
                                    CuratedMemoryScope scope,
                                    String oldText,
                                    String sourceSessionId,
                                    String sourceRequestId,
                                    String writeOrigin);

    List<CuratedMemoryEntry> listActive(LtmOwner owner, CuratedMemoryScope scope);

    /**
     * 会话开始冻结注入用快照文本。
     */
    String formatSnapshot(LtmOwner owner);

    int usedChars(LtmOwner owner, CuratedMemoryScope scope);

    int charLimit(CuratedMemoryScope scope);
}
