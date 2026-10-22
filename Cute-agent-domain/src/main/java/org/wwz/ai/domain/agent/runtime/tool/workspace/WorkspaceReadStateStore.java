package org.wwz.ai.domain.agent.runtime.tool.workspace;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.wwz.ai.domain.agent.runtime.agent.AgentContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 跨轮轻量 readState 持久化（主 Agent 的 session workspace 旁的 JSON，不进 MySQL、不存全文）。
 * 只服务主 Agent 的：别重复读 / 允许直接 edit。
 */
@Slf4j
@Component
public class WorkspaceReadStateStore {

    private static final String RELATIVE_STORE_PATH = ".reactor/read-state.json";

    public void hydrate(AgentContext agentContext) {
        if (agentContext == null || StringUtils.isBlank(agentContext.getWorkspaceRoot()) || isSubAgent(agentContext)) {
            return;
        }
        Path storeFile = storeFile(agentContext.getWorkspaceRoot());
        if (!Files.isRegularFile(storeFile)) {
            return;
        }
        try {
            String json = Files.readString(storeFile, StandardCharsets.UTF_8);
            if (StringUtils.isBlank(json)) {
                return;
            }
            JSONObject root = JSON.parseObject(json);
            if (root == null) {
                return;
            }
            Map<String, PersistedEntry> files = root.getObject("files", new TypeReference<Map<String, PersistedEntry>>() {
            });
            if (files == null || files.isEmpty()) {
                return;
            }
            int loaded = 0;
            // 只恢复 mtime、读取范围和 hash，不把文件正文写入跨轮状态，避免状态文件变成第二份内容存储。
            for (Map.Entry<String, PersistedEntry> entry : files.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                PersistedEntry pe = entry.getValue();
                agentContext.markWorkspaceFileRead(WorkspaceFileReadState.builder()
                        .absolutePath(entry.getKey())
                        .mtimeMs(pe.mtimeMs)
                        .startLine(pe.startLine <= 0 ? 1 : pe.startLine)
                        .lineCount(pe.lineCount <= 0 ? 2000 : pe.lineCount)
                        .contentHash(pe.contentHash)
                        .build());
                loaded++;
            }
            log.info("{} hydrated workspace read-state entries={}", agentContext.getRequestId(), loaded);
        } catch (Exception e) {
            log.warn("{} hydrate workspace read-state failed, path={}",
                    agentContext.getRequestId(), storeFile, e);
        }
    }

    /**
     * 压缩成功后清空内存 + 落盘状态（对齐 cc-haha：compact 后 clear readFileState）。
     * 子 Agent 不持有跨轮 JSON，只清内存。
     */
    public void clear(AgentContext agentContext) {
        if (agentContext == null) {
            return;
        }
        agentContext.clearWorkspaceReadState();
        if (isSubAgent(agentContext) || StringUtils.isBlank(agentContext.getWorkspaceRoot())) {
            return;
        }
        clearPersisted(agentContext.getWorkspaceRoot());
    }

    /** 删除会话工作区旁的 read-state.json（pre-run 压缩时尚无 AgentContext）。 */
    public void clearPersisted(String workspaceRoot) {
        if (StringUtils.isBlank(workspaceRoot)) {
            return;
        }
        Path storeFile = storeFile(workspaceRoot);
        try {
            if (Files.deleteIfExists(storeFile)) {
                log.info("cleared workspace read-state path={}", storeFile);
            }
        } catch (Exception e) {
            log.warn("clear workspace read-state failed, path={}", storeFile, e);
        }
    }

    public void persist(AgentContext agentContext) {
        if (agentContext == null || StringUtils.isBlank(agentContext.getWorkspaceRoot()) || isSubAgent(agentContext)) {
            return;
        }
        Map<String, WorkspaceFileReadState> states = agentContext.snapshotWorkspaceReadState();
        if (states == null || states.isEmpty()) {
            // 空快照必须删掉旧 JSON，否则下一轮 hydrate 会把已 clear 的状态读回来。
            clearPersisted(agentContext.getWorkspaceRoot());
            return;
        }
        Path storeFile = storeFile(agentContext.getWorkspaceRoot());
        try {
            Path parent = storeFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Map<String, PersistedEntry> files = new LinkedHashMap<>();
            // snapshot 是当前 AgentContext 的只读快照，序列化期间不依赖后续工具调用的瞬时变化。
            for (Map.Entry<String, WorkspaceFileReadState> entry : states.entrySet()) {
                WorkspaceFileReadState state = entry.getValue();
                if (state == null || StringUtils.isBlank(entry.getKey())) {
                    continue;
                }
                files.put(entry.getKey(), new PersistedEntry(
                        state.getMtimeMs(),
                        state.getStartLine(),
                        state.getLineCount(),
                        state.getContentHash()
                ));
            }
            JSONObject root = new JSONObject(true);
            root.put("version", 1);
            root.put("sessionId", agentContext.getSessionId());
            root.put("updatedAt", System.currentTimeMillis());
            root.put("files", files);
            // read-state 与工作区同根保存，跟随会话目录生命周期，不进入 Execution Ledger 或 MySQL。
            Files.writeString(storeFile, root.toJSONString(), StandardCharsets.UTF_8);
            log.info("{} persisted workspace read-state entries={} path={}",
                    agentContext.getRequestId(), files.size(), storeFile);
        } catch (Exception e) {
            log.warn("{} persist workspace read-state failed, path={}",
                    agentContext.getRequestId(), storeFile, e);
        }
    }

    public static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(StringUtils.defaultString(content).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private Path storeFile(String workspaceRoot) {
        return Path.of(workspaceRoot).toAbsolutePath().normalize().resolve(RELATIVE_STORE_PATH);
    }

    private boolean isSubAgent(AgentContext agentContext) {
        return StringUtils.isNotBlank(agentContext.getSubAgentId());
    }

    /**
     * 仅序列化元数据。
     */
    public static class PersistedEntry {
        public long mtimeMs;
        public int startLine;
        public int lineCount;
        public String contentHash;

        public PersistedEntry() {
        }

        public PersistedEntry(long mtimeMs, int startLine, int lineCount, String contentHash) {
            this.mtimeMs = mtimeMs;
            this.startLine = startLine;
            this.lineCount = lineCount;
            this.contentHash = contentHash;
        }
    }
}
