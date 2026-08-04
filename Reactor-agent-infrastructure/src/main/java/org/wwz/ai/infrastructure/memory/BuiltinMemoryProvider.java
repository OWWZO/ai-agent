package org.wwz.ai.infrastructure.memory;

import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryStore;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;
import org.wwz.ai.domain.agent.memory.ltm.MemoryProvider;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 内置策展 Provider：非 external，system 块来自 curated 快照。
 */
public class BuiltinMemoryProvider implements MemoryProvider {

    private final CuratedMemoryStore curatedMemoryStore;
    private final AtomicReference<LtmOwner> ownerRef = new AtomicReference<>();

    public BuiltinMemoryProvider(CuratedMemoryStore curatedMemoryStore) {
        this.curatedMemoryStore = curatedMemoryStore;
    }

    @Override
    public String name() {
        return "builtin";
    }

    @Override
    public boolean isExternal() {
        return false;
    }

    @Override
    public void initialize(String sessionId, LtmOwner owner, Map<String, Object> context) {
        if (owner != null) {
            ownerRef.set(owner);
        }
    }

    @Override
    public String systemPromptBlock() {
        LtmOwner owner = ownerRef.get();
        if (owner == null || curatedMemoryStore == null) {
            return "";
        }
        return curatedMemoryStore.formatSnapshot(owner);
    }
}
