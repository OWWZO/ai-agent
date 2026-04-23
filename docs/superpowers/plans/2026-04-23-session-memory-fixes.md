# 会话记忆机制代码审查修复计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复代码审查发现的 5 个问题：abbreviate 重复实现、artifact 双重序列化、配置缺失 JavaDoc、流注册表无过期清理、guardrailStateMap 内存泄漏。

**Architecture:** 提取公共工具方法消除重复代码；简化序列化流程去除冗余；使用 Guava Cache 替换手动 Map 实现自动过期清理；在熔断器状态窗口过期时主动移除条目防止内存泄漏。

**Tech Stack:** Java 17, Spring Boot, Guava, Lombok

---

## 文件结构

| 文件 | 动作 | 说明 |
|------|------|------|
| `agent/util/StringUtil.java` | 修改 | 添加公共 `abbreviate()` 方法 |
| `service/support/LlmSessionMemorySummaryGenerator.java` | 修改 | 替换为 `StringUtil.abbreviate()` |
| `service/support/SessionMemorySummaryBuilder.java` | 修改 | 替换为 `StringUtil.abbreviate()` |
| `service/support/SessionTranscriptBlockAssembler.java` | 修改 | 替换为 `StringUtil.abbreviate()` |
| `service/support/SessionArtifactRestoreSupport.java` | 修改 | 将 `deduplicateArtifactRefs` 改为 public，简化 `toArtifactRefsJson` |
| `service/support/SessionMemoryCompactionService.java` | 修改 | 去掉双重序列化，直接调用去重方法 |
| `config/ReactorConfig.java` | 修改 | 为 session-memory 配置项添加 JavaDoc |
| `service/support/ActiveSessionStreamRegistry.java` | 修改 | 使用 Guava Cache 替换 ConcurrentHashMap，实现自动过期 |
| `service/impl/AgentSessionMemoryServiceImpl.java` | 修改 | 在 `resolveGuardrailState()` 中窗口过期时移除条目 |

---

## Task 1: 提取公共 abbreviate() 工具方法

**Files:**
- Modify: `src/main/java/org/wwz/ai/domain/agent/reactor/agent/util/StringUtil.java`

- [ ] **Step 1: 在 StringUtil 中添加 abbreviate 方法**

在 `StringUtil.java` 的 `removeSpecialChars` 方法之后添加：

```java
    /**
     * 截断文本到指定最大长度，支持空白字符归一化。
     *
     * @param text      原始文本，允许 null 或空串
     * @param maxLength 最大长度（字符数），必须 > 0
     * @param normalize 是否先将空白字符归一化为单个空格
     * @return 截断后的文本，若输入为 null/空则返回空串
     */
    public static String abbreviate(String text, int maxLength, boolean normalize) {
        if (!org.springframework.util.StringUtils.hasText(text)) {
            return "";
        }
        if (maxLength <= 0) {
            return "";
        }
        String result = text.trim();
        if (normalize) {
            result = result.replaceAll("\\s+", " ");
        }
        return result.length() > maxLength ? result.substring(0, maxLength) : result;
    }

    /**
     * 截断文本到指定最大长度（不归一化空白）。
     */
    public static String abbreviate(String text, int maxLength) {
        return abbreviate(text, maxLength, false);
    }
```

- [ ] **Step 2: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/util/StringUtil.java
git commit -m "refactor: 提取公共 abbreviate() 工具方法到 StringUtil"
```

---

## Task 2: 替换三处的 abbreviate() 为 StringUtil.abbreviate()

**Files:**
- Modify: `src/main/java/org/wwz/ai/domain/agent/reactor/service/support/LlmSessionMemorySummaryGenerator.java`
- Modify: `src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemorySummaryBuilder.java`
- Modify: `src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionTranscriptBlockAssembler.java`

- [ ] **Step 1: 修改 LlmSessionMemorySummaryGenerator**

删除原有的 `abbreviate` 方法（第166-172行），然后在 import 区域添加：

```java
import org.wwz.ai.domain.agent.reactor.agent.util.StringUtil;
```

将所有 `abbreviate(` 调用替换为 `StringUtil.abbreviate(`，并将 normalize 参数设为 true（因为原实现做了 `replaceAll("\\s+", " ")`）。

修改后的调用点：
- 第114行：`abbreviate(JSON.toJSONString(...), MAX_ARTIFACT_REF_LENGTH)` → `StringUtil.abbreviate(JSON.toJSONString(...), MAX_ARTIFACT_REF_LENGTH, true)`
- 第127行：`abbreviate(turn.getUserMessage(), MAX_TURN_TEXT_LENGTH)` → `StringUtil.abbreviate(turn.getUserMessage(), MAX_TURN_TEXT_LENGTH, true)`
- 第137行：`abbreviate(block.getText(), MAX_BLOCK_TEXT_LENGTH)` → `StringUtil.abbreviate(block.getText(), MAX_BLOCK_TEXT_LENGTH, true)`
- 第142行：`abbreviate(block.getToolArgumentsJson(), MAX_BLOCK_TEXT_LENGTH)` → `StringUtil.abbreviate(block.getToolArgumentsJson(), MAX_BLOCK_TEXT_LENGTH, true)`
- 第146行：`abbreviate(JSON.toJSONString(block.getArtifactRefs()), MAX_BLOCK_TEXT_LENGTH)` → `StringUtil.abbreviate(JSON.toJSONString(block.getArtifactRefs()), MAX_BLOCK_TEXT_LENGTH, true)`
- 第152行：`abbreviate(firstNonBlank(...), MAX_TURN_TEXT_LENGTH)` → `StringUtil.abbreviate(firstNonBlank(...), MAX_TURN_TEXT_LENGTH, true)`

- [ ] **Step 2: 修改 SessionMemorySummaryBuilder**

删除原有的 `abbreviate` 方法（第189-195行），在 import 区域添加：

```java
import org.wwz.ai.domain.agent.reactor.agent.util.StringUtil;
```

修改调用点（normalize=true，因为原实现调用了 normalize()）：
- 第53行：`abbreviate(turn.getUserMessage(), 80)` → `StringUtil.abbreviate(turn.getUserMessage(), 80, true)`
- 第55行：`abbreviate(turn.getAssistantMessage(), 120)` → `StringUtil.abbreviate(turn.getAssistantMessage(), 120, true)`
- 第110行：`abbreviate(userMessage, 120)` → `StringUtil.abbreviate(userMessage, 120, true)`
- 第112行：`abbreviate(userMessage, 120)` → `StringUtil.abbreviate(userMessage, 120, true)`
- 第116行：`abbreviate(assistantMessage, 160)` → `StringUtil.abbreviate(assistantMessage, 160, true)`

- [ ] **Step 3: 修改 SessionTranscriptBlockAssembler**

删除原有的 `abbreviate` 方法（第426-431行），在 import 区域添加：

```java
import org.wwz.ai.domain.agent.reactor.agent.util.StringUtil;
```

修改调用点（normalize=false，因为原实现没有归一化空白）：
- 第261行：`abbreviate(argumentsText, 160)` → `StringUtil.abbreviate(argumentsText, 160)`

- [ ] **Step 4: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/LlmSessionMemorySummaryGenerator.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemorySummaryBuilder.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionTranscriptBlockAssembler.java
git commit -m "refactor: 替换三处 abbreviate() 为 StringUtil.abbreviate()"
```

---

## Task 3: 修复 buildCompactedArtifactRefs 双重序列化

**Files:**
- Modify: `src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionArtifactRestoreSupport.java`
- Modify: `src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemoryCompactionService.java`

- [ ] **Step 1: 将 deduplicateArtifactRefs 改为 public**

在 `SessionArtifactRestoreSupport.java` 中，将第251行的 `private List<JSONObject> deduplicateArtifactRefs` 改为 `public List<JSONObject> deduplicateArtifactRefs`。

- [ ] **Step 2: 简化 toArtifactRefsJson 不去重**

修改 `SessionArtifactRestoreSupport.java` 第212-214行的 `toArtifactRefsJson`：

```java
    public String toArtifactRefsJson(List<JSONObject> artifactRefs) {
        return JSON.toJSONString(artifactRefs);
    }
```

注意：这里移除 `deduplicateArtifactRefs` 调用，因为调用方应该自行决定是否需要去重。序列化只做序列化的事。

- [ ] **Step 3: 修改 SessionMemoryCompactionService 去掉双重序列化**

修改 `SessionMemoryCompactionService.java` 第189-206行的 `buildCompactedArtifactRefs` 方法：

```java
    private List<JSONObject> buildCompactedArtifactRefs(AgentSessionMemory snapshot,
                                                        List<AgentMessage> completedMessages,
                                                        Map<Long, List<AgentMessageEvent>> eventMap,
                                                        Set<Long> compactedMessageIds) {
        List<JSONObject> artifactRefs = new ArrayList<>(artifactRestoreSupport.parseArtifactRefs(
                snapshot == null ? null : snapshot.getArtifactRefsJson()));
        if (CollectionUtils.isEmpty(completedMessages) || CollectionUtils.isEmpty(compactedMessageIds)) {
            return artifactRestoreSupport.deduplicateArtifactRefs(artifactRefs);
        }
        List<AgentMessage> compactedMessages = completedMessages.stream()
                .filter(message -> message != null && compactedMessageIds.contains(message.getId()))
                .sorted(Comparator.comparing(AgentMessage::getSortOrder))
                .collect(Collectors.toList());
        artifactRefs.addAll(artifactRestoreSupport.collectArtifactRefs(compactedMessages, eventMap));
        return artifactRestoreSupport.deduplicateArtifactRefs(artifactRefs);
    }
```

变化说明：
- 第197-198行：`return new ArrayList<>(artifactRestoreSupport.parseArtifactRefs(artifactRestoreSupport.toArtifactRefsJson(artifactRefs)));` → `return artifactRestoreSupport.deduplicateArtifactRefs(artifactRefs);`
- 第204-205行：`return new ArrayList<>(artifactRestoreSupport.parseArtifactRefs(artifactRestoreSupport.toArtifactRefsJson(artifactRefs)));` → `return artifactRestoreSupport.deduplicateArtifactRefs(artifactRefs);`

- [ ] **Step 4: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionArtifactRestoreSupport.java ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemoryCompactionService.java
git commit -m "refactor: 修复 buildCompactedArtifactRefs 双重序列化问题"
```

---

## Task 4: 为 ReactorConfig 的 session-memory 配置添加 JavaDoc

**Files:**
- Modify: `src/main/java/org/wwz/ai/domain/agent/reactor/config/ReactorConfig.java`

- [ ] **Step 1: 为 session-memory 配置项添加 JavaDoc**

将第302-327行的配置字段替换为带注释的版本：

```java
    /**
     * 会话记忆功能总开关。设为 false 时完全禁用上下文压缩和记忆恢复，所有请求直接 BYPASS。
     */
    @Value("${autobots.autoagent.session-memory.enabled:true}")
    private Boolean sessionMemoryEnabled;

    /**
     * 触发压缩的 Token 阈值。当会话工作记忆估算 Token 数超过此值时启动压缩流程。
     * 单位：Token（估算值，非精确值）。
     * 与 hard-limit-tokens 的关系：threshold < hard-limit，形成两级梯度。
     */
    @Value("${autobots.autoagent.session-memory.compaction-threshold-tokens:12000}")
    private Integer sessionMemoryCompactionThresholdTokens;

    /**
     * 最近窗口保留的轮次数量上限。压缩时至少保留最近 N 轮不被压缩，确保上下文连贯性。
     * 实际保留轮次取 min(recent-window-turns, 当前总轮次)。
     */
    @Value("${autobots.autoagent.session-memory.recent-window-turns:10}")
    private Integer sessionMemoryRecentWindowTurns;

    /**
     * Token 硬上限。当估算 Token 超过此值且压缩失败/熔断时，请求将被 REJECTED。
     * 这是保护 LLM 上下文不溢出的最后一道防线。
     * 必须 > compaction-threshold-tokens，否则阈值逻辑失效。
     */
    @Value("${autobots.autoagent.session-memory.hard-limit-tokens:20000}")
    private Integer sessionMemoryHardLimitTokens;

    /**
     * 最近窗口的最大 Token 数。即使轮次未达 recent-window-turns，若 Token 已超此值也停止保留。
     * 与 recent-window-turns 形成"轮次+Token"双维度控制，防止单轮超大内容撑爆窗口。
     */
    @Value("${autobots.autoagent.session-memory.recent-window-max-tokens:4000}")
    private Integer sessionMemoryRecentWindowMaxTokens;

    /**
     * 最近窗口的最小消息数。无论 Token 是否超限，至少保留这么多条消息不被压缩。
     * 保证即使最近轮次很短，也有足够上下文让 Agent 理解当前状态。
     */
    @Value("${autobots.autoagent.session-memory.recent-window-min-messages:4}")
    private Integer sessionMemoryRecentWindowMinMessages;

    /**
     * 连续压缩失败多少次后打开熔断器。
     * 达到此值后，在 circuit-open-seconds 时间内不再尝试压缩，直接返回降级或拒绝。
     */
    @Value("${autobots.autoagent.session-memory.max-consecutive-failures:3}")
    private Integer sessionMemoryMaxConsecutiveFailures;

    /**
     * 熔断器打开后保持的时间窗口。在此时间内压缩请求被短路。
     * 单位：秒。
     */
    @Value("${autobots.autoagent.session-memory.circuit-open-seconds:600}")
    private Integer sessionMemoryCircuitOpenSeconds;

    /**
     * 压缩后摘要文本的最大长度限制。
     * 单位：字符数（非 Token）。
     */
    @Value("${autobots.autoagent.session-memory.summary-max-length:4000}")
    private Integer sessionMemorySummaryMaxLength;
```

- [ ] **Step 2: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/ReactorConfig.java
git commit -m "docs: 为 ReactorConfig session-memory 配置项添加 JavaDoc"
```

---

## Task 5: ActiveSessionStreamRegistry 添加过期清理

**Files:**
- Modify: `src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ActiveSessionStreamRegistry.java`

- [ ] **Step 1: 使用 Guava Cache 替换 ConcurrentHashMap**

将 `ActiveSessionStreamRegistry.java` 完整替换为以下实现：

```java
package org.wwz.ai.domain.agent.reactor.service.support;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import okhttp3.Call;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 活跃会话流注册表。
 * 使用 Guava Cache 实现自动过期清理，防止异常中断导致内存泄漏。
 */
@Component
public class ActiveSessionStreamRegistry {

    /**
     * 默认过期时间：30 分钟。若请求超过此时间未完成，自动清理。
     */
    private static final long DEFAULT_EXPIRE_MINUTES = 30;

    private final Cache<String, ActiveSessionStream> activeStreams = CacheBuilder.newBuilder()
            .expireAfterWrite(DEFAULT_EXPIRE_MINUTES, TimeUnit.MINUTES)
            .build();

    public void register(String sessionId,
                         String requestId,
                         Long messageId,
                         Call call,
                         SseEmitter emitter) {
        activeStreams.put(requestId, ActiveSessionStream.builder()
                .sessionId(sessionId)
                .requestId(requestId)
                .messageId(messageId)
                .call(call)
                .emitter(emitter)
                .stopRequested(new AtomicBoolean(false))
                .build());
    }

    public void unregister(String requestId) {
        if (requestId == null) {
            return;
        }
        activeStreams.invalidate(requestId);
    }

    public boolean requestStop(String requestId) {
        ActiveSessionStream activeStream = activeStreams.getIfPresent(requestId);
        if (activeStream == null) {
            return false;
        }
        activeStream.getStopRequested().set(true);
        if (activeStream.getCall() != null) {
            activeStream.getCall().cancel();
        }
        return true;
    }

    public boolean isStopRequested(String requestId) {
        ActiveSessionStream activeStream = activeStreams.getIfPresent(requestId);
        return activeStream != null && activeStream.getStopRequested().get();
    }

    /**
     * 获取当前活跃流数量（主要用于监控和调试）。
     */
    public long getActiveCount() {
        activeStreams.cleanUp();
        return activeStreams.size();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveSessionStream {
        private String sessionId;
        private String requestId;
        private Long messageId;
        private Call call;
        private SseEmitter emitter;
        private AtomicBoolean stopRequested;
    }
}
```

关键变化：
- `ConcurrentHashMap` → `Cache<String, ActiveSessionStream>`
- `put` → `Cache.put`
- `remove` → `Cache.invalidate`
- `get` → `Cache.getIfPresent`
- 新增 `getActiveCount()` 用于监控

- [ ] **Step 2: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ActiveSessionStreamRegistry.java
git commit -m "fix: ActiveSessionStreamRegistry 使用 Guava Cache 实现自动过期清理"
```

---

## Task 6: 修复 guardrailStateMap 内存泄漏

**Files:**
- Modify: `src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentSessionMemoryServiceImpl.java`

- [ ] **Step 1: 修改 resolveGuardrailState 在窗口过期时移除条目**

修改第251-263行的 `resolveGuardrailState` 方法：

```java
    private CompactionGuardrailState resolveGuardrailState(String sessionId) {
        if (sessionId == null) {
            return new CompactionGuardrailState();
        }
        CompactionGuardrailState state = guardrailStateMap.computeIfAbsent(
                sessionId,
                key -> new CompactionGuardrailState());
        if (!isCircuitWindowActive(state)) {
            guardrailStateMap.remove(sessionId);
            return new CompactionGuardrailState();
        }
        return state;
    }
```

变化说明：
- 原逻辑：窗口过期时重置失败计数（`setConsecutiveFailures(0); setLastFailureAt(null)`），但保留条目
- 新逻辑：窗口过期时直接 `guardrailStateMap.remove(sessionId)`，返回一个全新的 `CompactionGuardrailState`
- 这样长期不活跃的会话 guardrail 状态会被自动清理

- [ ] **Step 2: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentSessionMemoryServiceImpl.java
git commit -m "fix: guardrailStateMap 在熔断窗口过期时移除条目防止内存泄漏"
```

---

## Self-Review Checklist

**Spec coverage:**
- [x] abbreviate 重复提取 → Task 1 + Task 2
- [x] artifact 双重序列化 → Task 3
- [x] ReactorConfig JavaDoc → Task 4
- [x] ActiveSessionStreamRegistry 过期清理 → Task 5
- [x] guardrailStateMap 内存泄漏 → Task 6

**Placeholder scan:**
- [x] 无 "TBD"/"TODO"
- [x] 无 "appropriate error handling" 等模糊描述
- [x] 每个代码步骤包含完整代码

**Type consistency:**
- [x] `StringUtil.abbreviate(String, int, boolean)` 签名在所有调用处一致
- [x] `deduplicateArtifactRefs` 返回类型 `List<JSONObject>` 在修改前后一致
- [x] `ActiveSessionStreamRegistry` 的 public API（register/unregister/requestStop/isStopRequested）签名不变
