package org.wwz.ai.infrastructure.adapter.repository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.wwz.ai.infrastructure.dao.IAiAgentBrowserSessionDao;
import org.wwz.ai.infrastructure.dao.po.AiAgentBrowserSession;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Agent 浏览器会话映射的写路径。
 *
 * <p>事务边界严格限制在数据库调用之内：浏览器解析过程中的 Kernel HTTP 调用与进程内互斥
 * 留在 {@code AgentBrowserSessionAdapter}，避免数据库连接被跨在远程 I/O 上。</p>
 *
 * <p>独立成 Spring bean 而不是 Adapter 的私有方法，是因为自调用不会经过事务代理，
 * 写在同类内部的事务注解不会生效。</p>
 */
@Service
public class AgentBrowserSessionPersistenceService {

    private final IAiAgentBrowserSessionDao sessionDao;

    public AgentBrowserSessionPersistenceService(IAiAgentBrowserSessionDao sessionDao) {
        this.sessionDao = sessionDao;
    }

    /**
     * 直接写入映射（新建或重建浏览器后的落库路径）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void upsertMapping(String ownerKey,
                              String agentSessionId,
                              String agentBrowserName,
                              LocalDateTime now) {
        upsertMappingLocked(ownerKey, agentSessionId, agentBrowserName, now);
    }

    /**
     * 复用已有映射时只刷新 last_used_at；映射内容变化或行已消失时回退为 upsert。
     */
    @Transactional(rollbackFor = Exception.class)
    public void touchOrPersistMapping(String ownerKey,
                                      String agentSessionId,
                                      String agentBrowserName,
                                      LocalDateTime now) {
        AiAgentBrowserSession existing = sessionDao.queryByOwnerKey(ownerKey);
        boolean unchanged = existing != null
                && Objects.equals(existing.getAgentSessionId(), agentSessionId)
                && Objects.equals(existing.getAgentBrowserName(), agentBrowserName);
        if (unchanged && sessionDao.updateLastUsedAt(ownerKey, now) > 0) {
            return;
        }
        upsertMappingLocked(ownerKey, agentSessionId, agentBrowserName, now);
    }

    /**
     * 删除映射。远程浏览器的清理由调用方在事务外完成。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteMapping(String ownerKey) {
        sessionDao.deleteByOwnerKey(ownerKey);
    }

    /**
     * 同一事务内的 upsert：先校验浏览器名未被其他 owner 占用，再 update-or-insert。
     * insert 冲突说明并发写入已抢先落库，重新读回后转为 update，保证最终只有一行。
     */
    private void upsertMappingLocked(String ownerKey,
                                     String agentSessionId,
                                     String agentBrowserName,
                                     LocalDateTime now) {
        AiAgentBrowserSession mappedByName = sessionDao.queryByAgentBrowserName(agentBrowserName);
        if (mappedByName != null && !ownerKey.equals(mappedByName.getOwnerKey())) {
            throw new IllegalStateException("Agent browser name is mapped to another userId");
        }

        AiAgentBrowserSession row = new AiAgentBrowserSession();
        row.setOwnerKey(ownerKey);
        row.setAgentSessionId(agentSessionId);
        row.setAgentBrowserName(agentBrowserName);
        row.setLastUsedAt(now);
        row.setCreateTime(now);
        row.setUpdateTime(now);

        int updated = sessionDao.updateSession(row);
        if (updated == 0) {
            try {
                sessionDao.insert(row);
            } catch (RuntimeException insertFailure) {
                // 并发写入抢先插入唯一 owner_key 行时，重新读回并转为 update，保证幂等。
                AiAgentBrowserSession concurrentRow = sessionDao.queryByOwnerKey(ownerKey);
                if (concurrentRow == null) {
                    throw insertFailure;
                }
                sessionDao.updateSession(row);
            }
        }
    }
}
