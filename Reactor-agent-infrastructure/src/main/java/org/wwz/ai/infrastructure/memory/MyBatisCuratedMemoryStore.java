package org.wwz.ai.infrastructure.memory;

import lombok.extern.slf4j.Slf4j;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryEntry;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryScope;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryStore;
import org.wwz.ai.domain.agent.memory.ltm.CuratedMemoryWriteResult;
import org.wwz.ai.domain.agent.memory.ltm.LtmOwner;
import org.wwz.ai.infrastructure.dao.reactor.ILtmCuratedEntryDao;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MySQL 持久化策展记忆（用户级隔离，无向量）。
 * Bean 由 {@code LtmAutoConfiguration} 按 DAO 是否存在装配，避免 {@code @ConditionalOnBean} 扫描时序问题。
 */
@Slf4j
public class MyBatisCuratedMemoryStore implements CuratedMemoryStore {

    private static final String ENTRY_DELIMITER = "\n§\n";

    private final ILtmCuratedEntryDao curatedEntryDao;
    private final int curatedCharLimit;
    private final int userCharLimit;

    public MyBatisCuratedMemoryStore(ILtmCuratedEntryDao curatedEntryDao) {
        this(curatedEntryDao, 2200, 1375);
    }

    public MyBatisCuratedMemoryStore(ILtmCuratedEntryDao curatedEntryDao, int curatedCharLimit, int userCharLimit) {
        this.curatedEntryDao = curatedEntryDao;
        this.curatedCharLimit = curatedCharLimit;
        this.userCharLimit = userCharLimit;
    }

    @Override
    public CuratedMemoryWriteResult add(LtmOwner owner,
                                        CuratedMemoryScope scope,
                                        String content,
                                        String sourceSessionId,
                                        String sourceRequestId,
                                        String writeOrigin) {
        String text = normalize(content);
        if (text.isEmpty()) {
            return CuratedMemoryWriteResult.fail("content required", usedChars(owner, scope), charLimit(scope));
        }
        List<CuratedMemoryEntry> entries = listActive(owner, scope);
        if (entries.stream().anyMatch(e -> text.equals(e.getContent()))) {
            return CuratedMemoryWriteResult.noChange("duplicate entry", usedChars(owner, scope), charLimit(scope));
        }
        int limit = charLimit(scope);
        int next = measure(entries) + text.length() + (entries.isEmpty() ? 0 : ENTRY_DELIMITER.length());
        if (next > limit) {
            return CuratedMemoryWriteResult.fail(
                    "memory full (" + usedChars(owner, scope) + "/" + limit + "); consolidate entries before adding",
                    usedChars(owner, scope), limit);
        }
        CuratedMemoryEntry entry = CuratedMemoryEntry.builder()
                .ownerType(owner.getType())
                .ownerId(owner.getId())
                .scope(scope)
                .content(text)
                .status(CuratedMemoryEntry.STATUS_ACTIVE)
                .sourceSessionId(sourceSessionId)
                .sourceRequestId(sourceRequestId)
                .writeOrigin(writeOrigin)
                .build();
        try {
            int rows = curatedEntryDao.insert(entry);
            if (rows <= 0) {
                log.warn("curated insert returned 0 rows owner={} scope={}", owner, scope);
                return CuratedMemoryWriteResult.fail("insert returned 0 rows", usedChars(owner, scope), limit);
            }
            log.info("curated memory inserted id={} owner={}/{} scope={} chars={}",
                    entry.getId(), owner.getType(), owner.getId(), scope.getCode(), text.length());
        } catch (Exception e) {
            log.error("curated memory insert failed owner={}/{} scope={}: {}",
                    owner.getType(), owner.getId(), scope.getCode(), e.toString(), e);
            return CuratedMemoryWriteResult.fail("insert failed: " + e.getMessage(), usedChars(owner, scope), limit);
        }
        return CuratedMemoryWriteResult.ok("added", usedChars(owner, scope), limit);
    }

    @Override
    public CuratedMemoryWriteResult replace(LtmOwner owner,
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
        List<CuratedMemoryEntry> entries = listActive(owner, scope);
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
                trial.add(CuratedMemoryEntry.builder().content(text).build());
            } else {
                trial.add(e);
            }
        }
        int limit = charLimit(scope);
        if (measure(trial) > limit) {
            return CuratedMemoryWriteResult.fail("replace would exceed capacity", usedChars(owner, scope), limit);
        }
        curatedEntryDao.updateContent(target.getId(), text, sourceSessionId, sourceRequestId, writeOrigin);
        return CuratedMemoryWriteResult.ok("replaced", usedChars(owner, scope), limit);
    }

    @Override
    public CuratedMemoryWriteResult remove(LtmOwner owner,
                                           CuratedMemoryScope scope,
                                           String oldText,
                                           String sourceSessionId,
                                           String sourceRequestId,
                                           String writeOrigin) {
        String needle = normalize(oldText);
        if (needle.isEmpty()) {
            return CuratedMemoryWriteResult.fail("old_text required", usedChars(owner, scope), charLimit(scope));
        }
        List<CuratedMemoryEntry> entries = listActive(owner, scope);
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
        curatedEntryDao.softDeleteById(matches.get(0).getId());
        return CuratedMemoryWriteResult.ok("removed", usedChars(owner, scope), charLimit(scope));
    }

    @Override
    public List<CuratedMemoryEntry> listActive(LtmOwner owner, CuratedMemoryScope scope) {
        try {
            List<CuratedMemoryEntry> rows = curatedEntryDao.selectActive(
                    owner.getType().name(), owner.getId(), scope.getCode());
            return rows == null ? List.of() : List.copyOf(rows);
        } catch (Exception e) {
            log.warn("listActive curated memory failed owner={} scope={}: {}", owner, scope, e.toString());
            return List.of();
        }
    }

    @Override
    public String formatSnapshot(LtmOwner owner) {
        StringBuilder sb = new StringBuilder();
        appendBlock(sb, "MEMORY (curated notes)", owner, CuratedMemoryScope.CURATED);
        appendBlock(sb, "USER PROFILE", owner, CuratedMemoryScope.USER);
        return sb.toString().trim();
    }

    private void appendBlock(StringBuilder sb, String title, LtmOwner owner, CuratedMemoryScope scope) {
        List<CuratedMemoryEntry> entries = listActive(owner, scope);
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
    public int usedChars(LtmOwner owner, CuratedMemoryScope scope) {
        return measure(listActive(owner, scope));
    }

    @Override
    public int charLimit(CuratedMemoryScope scope) {
        return scope == CuratedMemoryScope.USER ? userCharLimit : curatedCharLimit;
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
            String c = entries.get(i).getContent();
            total += c == null ? 0 : c.length();
        }
        return total;
    }

    private static String normalize(String content) {
        return content == null ? "" : content.trim();
    }
}
