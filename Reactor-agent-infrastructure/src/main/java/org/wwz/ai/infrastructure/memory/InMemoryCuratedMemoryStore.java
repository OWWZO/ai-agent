package org.wwz.ai.infrastructure.memory;

import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryEntry;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryScope;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryStore;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryWriteResult;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwnerType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 进程内策展记忆实现（单测与无 DAO 降级）。
 * 禁止 embedding；有界全量快照。生产主路径见 {@link MyBatisCuratedMemoryStore}。
 */
public class InMemoryCuratedMemoryStore implements CuratedMemoryStore {

    private static final String ENTRY_DELIMITER = "\n§\n";

    private final int curatedCharLimit;
    private final int userCharLimit;
    private final AtomicLong idSeq = new AtomicLong(1);
    private final Map<String, List<CuratedMemoryEntry>> store = new ConcurrentHashMap<>();

    public InMemoryCuratedMemoryStore() {
        this(2200, 1375);
    }

    public InMemoryCuratedMemoryStore(int curatedCharLimit, int userCharLimit) {
        this.curatedCharLimit = curatedCharLimit;
        this.userCharLimit = userCharLimit;
    }

    @Override
    public synchronized CuratedMemoryWriteResult add(LtmOwner owner,
                                                     CuratedMemoryScope scope,
                                                     String content,
                                                     String sourceSessionId,
                                                     String sourceRequestId,
                                                     String writeOrigin) {
        String text = normalize(content);
        if (text.isEmpty()) {
            return CuratedMemoryWriteResult.fail("content required", usedChars(owner, scope), charLimit(scope));
        }
        List<CuratedMemoryEntry> entries = activeList(owner, scope);
        if (entries.stream().anyMatch(e -> text.equals(e.getContent()))) {
            return CuratedMemoryWriteResult.noChange("duplicate entry", usedChars(owner, scope), charLimit(scope));
        }
        int limit = charLimit(scope);
        int next = measure(entries) + text.length() + (entries.isEmpty() ? 0 : ENTRY_DELIMITER.length());
        if (next > limit) {
            return CuratedMemoryWriteResult.fail(
                    "memory full (" + usedChars(owner, scope) + "/" + limit
                            + "); consolidate entries before adding",
                    usedChars(owner, scope), limit);
        }
        entries.add(CuratedMemoryEntry.builder()
                .id(idSeq.getAndIncrement())
                .ownerType(owner.getType())
                .ownerId(owner.getId())
                .scope(scope)
                .content(text)
                .status(CuratedMemoryEntry.STATUS_ACTIVE)
                .sourceSessionId(sourceSessionId)
                .sourceRequestId(sourceRequestId)
                .writeOrigin(writeOrigin)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build());
        return CuratedMemoryWriteResult.ok("added", usedChars(owner, scope), limit);
    }

    @Override
    public synchronized CuratedMemoryWriteResult replace(LtmOwner owner,
                                                         CuratedMemoryScope scope,
                                                         String oldText,
                                                         String content,
                                                         String sourceSessionId,
                                                         String sourceRequestId,
                                                         String writeOrigin) {
        String needle = normalize(oldText);
        String text = normalize(content);
        if (needle.isEmpty() || text.isEmpty()) {
            return CuratedMemoryWriteResult.fail("old_text and content required", usedChars(owner, scope), charLimit(scope));
        }
        List<CuratedMemoryEntry> entries = activeList(owner, scope);
        List<CuratedMemoryEntry> matches = entries.stream()
                .filter(e -> e.getContent().contains(needle))
                .collect(Collectors.toList());
        if (matches.isEmpty()) {
            return CuratedMemoryWriteResult.fail("no entry matched old_text", usedChars(owner, scope), charLimit(scope));
        }
        if (matches.size() > 1) {
            return CuratedMemoryWriteResult.fail("old_text matches multiple entries; be more specific",
                    usedChars(owner, scope), charLimit(scope));
        }
        CuratedMemoryEntry target = matches.get(0);
        List<CuratedMemoryEntry> trial = new ArrayList<>();
        for (CuratedMemoryEntry e : entries) {
            if (e.getId().equals(target.getId())) {
                trial.add(CuratedMemoryEntry.builder()
                        .id(e.getId())
                        .ownerType(e.getOwnerType())
                        .ownerId(e.getOwnerId())
                        .scope(e.getScope())
                        .content(text)
                        .status(CuratedMemoryEntry.STATUS_ACTIVE)
                        .sourceSessionId(sourceSessionId)
                        .sourceRequestId(sourceRequestId)
                        .writeOrigin(writeOrigin)
                        .createTime(e.getCreateTime())
                        .updateTime(LocalDateTime.now())
                        .build());
            } else {
                trial.add(e);
            }
        }
        int limit = charLimit(scope);
        if (measure(trial) > limit) {
            return CuratedMemoryWriteResult.fail("replace would exceed capacity", usedChars(owner, scope), limit);
        }
        store.put(key(owner, scope), trial);
        return CuratedMemoryWriteResult.ok("replaced", usedChars(owner, scope), limit);
    }

    @Override
    public synchronized CuratedMemoryWriteResult remove(LtmOwner owner,
                                                        CuratedMemoryScope scope,
                                                        String oldText,
                                                        String sourceSessionId,
                                                        String sourceRequestId,
                                                        String writeOrigin) {
        String needle = normalize(oldText);
        if (needle.isEmpty()) {
            return CuratedMemoryWriteResult.fail("old_text required", usedChars(owner, scope), charLimit(scope));
        }
        List<CuratedMemoryEntry> entries = activeList(owner, scope);
        List<CuratedMemoryEntry> matches = entries.stream()
                .filter(e -> e.getContent().contains(needle))
                .collect(Collectors.toList());
        if (matches.isEmpty()) {
            return CuratedMemoryWriteResult.fail("no entry matched old_text", usedChars(owner, scope), charLimit(scope));
        }
        if (matches.size() > 1) {
            return CuratedMemoryWriteResult.fail("old_text matches multiple entries; be more specific",
                    usedChars(owner, scope), charLimit(scope));
        }
        Long removeId = matches.get(0).getId();
        List<CuratedMemoryEntry> remaining = entries.stream()
                .filter(e -> !e.getId().equals(removeId))
                .collect(Collectors.toCollection(ArrayList::new));
        store.put(key(owner, scope), remaining);
        return CuratedMemoryWriteResult.ok("removed", usedChars(owner, scope), charLimit(scope));
    }

    @Override
    public synchronized List<CuratedMemoryEntry> listActive(LtmOwner owner, CuratedMemoryScope scope) {
        return List.copyOf(activeList(owner, scope));
    }

    @Override
    public synchronized String formatSnapshot(LtmOwner owner) {
        StringBuilder sb = new StringBuilder();
        appendBlock(sb, "MEMORY (curated notes)", owner, CuratedMemoryScope.CURATED);
        appendBlock(sb, "USER PROFILE", owner, CuratedMemoryScope.USER);
        return sb.toString().trim();
    }

    private void appendBlock(StringBuilder sb, String title, LtmOwner owner, CuratedMemoryScope scope) {
        List<CuratedMemoryEntry> entries = activeList(owner, scope);
        int used = measure(entries);
        int limit = charLimit(scope);
        int pct = limit == 0 ? 0 : Math.min(100, used * 100 / limit);
        sb.append("══════════════════════════════════════════════\n")
                .append(title)
                .append(" [")
                .append(pct)
                .append("% — ")
                .append(used)
                .append("/")
                .append(limit)
                .append(" chars]\n")
                .append("══════════════════════════════════════════════\n");
        if (entries.isEmpty()) {
            sb.append("(empty)\n\n");
            return;
        }
        sb.append(entries.stream().map(CuratedMemoryEntry::getContent).collect(Collectors.joining(ENTRY_DELIMITER)));
        sb.append("\n\n");
    }

    @Override
    public synchronized int usedChars(LtmOwner owner, CuratedMemoryScope scope) {
        return measure(activeList(owner, scope));
    }

    @Override
    public int charLimit(CuratedMemoryScope scope) {
        return scope == CuratedMemoryScope.USER ? userCharLimit : curatedCharLimit;
    }

    private List<CuratedMemoryEntry> activeList(LtmOwner owner, CuratedMemoryScope scope) {
        return store.computeIfAbsent(key(owner, scope), k -> new ArrayList<>());
    }

    private static String key(LtmOwner owner, CuratedMemoryScope scope) {
        return owner.getType().name() + "|" + owner.getId() + "|" + scope.getCode();
    }

    private static int measure(List<CuratedMemoryEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                total += ENTRY_DELIMITER.length();
            }
            total += entries.get(i).getContent().length();
        }
        return total;
    }

    private static String normalize(String content) {
        return content == null ? "" : content.trim();
    }

    /** 测试辅助：清空 */
    public synchronized void clear() {
        store.clear();
    }

    public static LtmOwnerType parseOwnerType(String raw) {
        if (raw == null) {
            return LtmOwnerType.VISITOR;
        }
        return LtmOwnerType.valueOf(raw.trim().toUpperCase());
    }
}
