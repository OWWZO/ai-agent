# Prompt Cache Memory Log Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the exact model-visible memory prefix on the next request and remove request-specific values from Agent system prompts.

**Architecture:** New `prompt_memory` stream, turn, and message tables store only the messages a request appends to runtime `Memory`. A domain service controls a scope-specific lease, hydration, protocol validation, and delta publication. The execution ledger remains the replay and audit source.

**Tech Stack:** Java 17, Spring Boot 3.4, MyBatis XML, MySQL 8, FastJSON, JUnit 4.

---

### Task 1: Lossless Memory Projection

**Files:**
- Create: `Cute-agent-domain/src/main/java/org/wwz/ai/domain/agent/memory/PromptMemoryScope.java`
- Create: `Cute-agent-domain/src/main/java/org/wwz/ai/domain/agent/memory/PromptMemoryStreamKey.java`
- Create: `Cute-agent-domain/src/main/java/org/wwz/ai/domain/agent/memory/PromptMemoryMessage.java`
- Create: `Cute-agent-domain/src/main/java/org/wwz/ai/domain/agent/memory/PromptMemoryProjector.java`
- Modify: `Cute-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/dto/Memory.java`
- Test: `Cute-agent-app/src/test/java/org/wwz/ai/test/domain/PromptMemoryProjectorTest.java`

- [ ] Write a failing test for a `USER -> ASSISTANT(tool_calls) -> TOOL -> ASSISTANT` round trip and a second test that trims an unmatched assistant tool-call suffix.
- [ ] Run `mvn -pl Cute-agent-app test -Dtest=PromptMemoryProjectorTest -DskipTests=false`; expect compilation failure because the projector does not exist.
- [ ] Implement these exact contracts:

```java
public enum PromptMemoryScope { REACT, PLAN, EXECUTOR, SUMMARY }
public record PromptMemoryStreamKey(String sessionId, PromptMemoryScope scope,
        String promptContractId, String toolContractId) {}
public List<PromptMemoryMessage> project(List<Message> memory, int baseline);
public List<Message> hydrate(List<PromptMemoryMessage> rows);
public List<Message> validPrefix(List<Message> messages);
```

- [ ] Preserve `role`, `content`, `base64Image`, `toolCallId`, and raw tool-call arguments. Add `Memory.replaceMessages(List<Message>)` that copies its argument.
- [ ] Re-run the focused test; expect PASS.
- [ ] Commit with `git commit -m "feat: add prompt memory projection"`.

### Task 2: Prompt-Memory Persistence and Lease

**Files:**
- Modify: `Cute-agent-app/src/main/resources/db/schema.sql`
- Create: `Cute-agent-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/cute/IPromptMemoryStreamDao.java`
- Create: `Cute-agent-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/cute/IPromptMemoryTurnDao.java`
- Create: `Cute-agent-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/cute/IPromptMemoryMessageDao.java`
- Create: `Cute-agent-app/src/main/resources/mybatis/mapper/prompt_memory_stream_mapper.xml`
- Create: `Cute-agent-app/src/main/resources/mybatis/mapper/prompt_memory_turn_mapper.xml`
- Create: `Cute-agent-app/src/main/resources/mybatis/mapper/prompt_memory_message_mapper.xml`
- Create: `Cute-agent-infrastructure/src/main/java/org/wwz/ai/infrastructure/adapter/repository/PromptMemoryRepository.java`
- Test: `Cute-agent-app/src/test/java/org/wwz/ai/test/domain/PromptMemoryRepositoryTest.java`

- [ ] Write failing tests that: load committed rows in `turn_seq, seq_no` order; persist only one delta for each request; reject a second non-expired lease for the same stream.
- [ ] Run `mvn -pl Cute-agent-app test -Dtest=PromptMemoryRepositoryTest -DskipTests=false`; expect compilation failure.
- [ ] Add three tables: `ai_agent_prompt_memory_stream`, `ai_agent_prompt_memory_turn`, and `ai_agent_prompt_memory_message`. The stream table has unique stream identity, `latest_turn_seq`, `active_request_id`, `lease_expire_at`, and `version`; turn has unique `(stream_id, turn_seq)`; message has unique `(turn_id, seq_no)`.
- [ ] Implement compare-and-set acquire/release and transactional publish:

```java
@Transactional
public void publish(PromptMemoryLease lease, List<PromptMemoryMessage> delta) {
    requireLeaseOwner(lease);
    Long turnId = turnDao.insertBuilding(lease.toTurn());
    messageDao.batchInsert(turnId, delta);
    turnDao.markReady(turnId, delta.size());
    streamDao.advanceAndRelease(lease);
}
```

- [ ] Do not hold a database transaction while a model streams.
- [ ] Run `mvn -pl Cute-agent-app test -Dtest='PromptMemoryRepositoryTest,CuteMapperNamespaceBindingTest' -DskipTests=false`; expect PASS.
- [ ] Commit with `git commit -m "feat: persist prompt memory log"`.

### Task 3: Domain Lifecycle Service

**Files:**
- Create: `Cute-agent-domain/src/main/java/org/wwz/ai/domain/agent/memory/PromptMemoryRepository.java`
- Create: `Cute-agent-domain/src/main/java/org/wwz/ai/domain/agent/memory/PromptMemoryExecution.java`
- Create: `Cute-agent-domain/src/main/java/org/wwz/ai/domain/agent/memory/PromptMemoryService.java`
- Create: `Cute-agent-infrastructure/src/main/java/org/wwz/ai/infrastructure/cute/service/PromptMemoryServiceImpl.java`
- Modify: `Cute-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/CuteRuntimeDependencies.java`
- Modify: `Cute-agent-app/src/main/java/org/wwz/ai/config/cute/CuteRuntimeAutoConfiguration.java`
- Modify: `Cute-agent-app/src/test/java/org/wwz/ai/test/domain/support/CuteRuntimeTestSupport.java`
- Test: `Cute-agent-app/src/test/java/org/wwz/ai/test/domain/PromptMemoryServiceTest.java`

- [ ] Write a failing test that seeds `old`, opens a stream, appends `new`, completes it, and asserts that the second turn contains only `new` while hydration returns `old, new`.
- [ ] Run `mvn -pl Cute-agent-app test -Dtest=PromptMemoryServiceTest -DskipTests=false`; expect compilation failure.
- [ ] Implement the lifecycle:

```java
public interface PromptMemoryService {
    PromptMemoryExecution open(PromptMemoryStreamKey key, String requestId, Long runId);
    void complete(PromptMemoryExecution execution, Memory memory);
    void abort(PromptMemoryExecution execution);
}
```

- [ ] `open` acquires the lease and records the hydrated baseline; `complete` projects `Memory[baseline..end]`; `abort` releases an unpublished lease. Wire the service through `CuteRuntimeDependencies`.
- [ ] Re-run projector and lifecycle tests; expect PASS.
- [ ] Commit with `git commit -m "feat: add prompt memory lifecycle"`.

### Task 4: Static Prompt Contract

**Files:**
- Modify: `Cute-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/agent/BaseAgent.java`
- Modify: `Cute-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/agent/ReactImplAgent.java`
- Modify: `Cute-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/agent/PlanningAgent.java`
- Modify: `Cute-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/agent/ExecutorAgent.java`
- Modify: `Cute-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/agent/SummaryAgent.java`
- Modify: `Cute-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/ToolCollection.java`
- Test: `Cute-agent-app/src/test/java/org/wwz/ai/test/domain/PromptCacheContractTest.java`

- [ ] Write failing tests asserting that different query/date/file contexts create equal system prompts, and that a tool result does not append a synthetic next-step user message.
- [ ] Run `mvn -pl Cute-agent-app test -Dtest=PromptCacheContractTest -DskipTests=false`; expect FAIL.
- [ ] Remove default history injection and remove query/date/files/request base/SOP replacements from every system prompt. Keep a stable continuation rule in the system prompt. Append dynamic context only as a persisted user message before the actual query.

```java
protected void appendRuntimeContext(String content) {
    if (StringUtils.isNotBlank(content)) {
        getMemory().addMessage(Message.userMessage("<runtime_context>\n" + content
                + "\n</runtime_context>", null));
    }
}
```

- [ ] Remove every `lastMessage != USER` branch that appends `nextStepPrompt`. Sort local and MCP tools by name for both rendering and `toolContractId` calculation.
- [ ] Run `mvn -pl Cute-agent-app test -Dtest='PromptCacheContractTest,PlanningAgentTest,SummaryAgentArtifactSelectionTest' -DskipTests=false`; expect PASS.
- [ ] Commit with `git commit -m "refactor: stabilize agent prompt prefix"`.

### Task 5: Hydrate and Publish the Four Scopes

**Files:**
- Modify: `Cute-agent-case/src/main/java/org/wwz/ai/application/agent/execute/react/ReactAgentExecuteStrategy.java`
- Modify: `Cute-agent-case/src/main/java/org/wwz/ai/application/agent/execute/planexecute/PlanSolveAgentExecuteStrategy.java`
- Modify: `Cute-agent-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/react/step/RunReactNode.java`
- Modify: `Cute-agent-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/react/step/SummaryResultNode.java`
- Modify: `Cute-agent-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/planexecute/step/Step2PlanExecuteNode.java`
- Test: `Cute-agent-app/src/test/java/org/wwz/ai/test/domain/PromptMemoryIntegrationTest.java`

- [ ] Write failing integration tests for two React requests that restore the first request's prefix before the second query, and for Plan/Executor/Summary streams that remain separate.
- [ ] Run `mvn -pl Cute-agent-app test -Dtest=PromptMemoryIntegrationTest -DskipTests=false`; expect FAIL.
- [ ] Remove text-history preparation from both application strategies. Open/hydrate/complete `REACT` around the React executor and `SUMMARY` around summary generation. Open/hydrate/complete `PLAN`, `EXECUTOR`, and `SUMMARY` around PlanSolve components. Abort unfinished leases in every exception or stop branch. Persist child executor messages only after deterministic merge into parent memory.
- [ ] Run `mvn -pl Cute-agent-app test -Dtest='PromptMemoryIntegrationTest,ReactExecutionLedgerIntegrationTest,PlanSolveExecutionLedgerIntegrationTest,PlanSolveNestedConcurrencyHardeningTest' -DskipTests=false`; expect PASS.
- [ ] Commit with `git commit -m "feat: restore prompt memory across scopes"`.

### Task 6: Boundary and Full Verification

**Files:**
- Modify: `Cute-agent-app/src/test/java/org/wwz/ai/test/domain/CutePersistenceBoundaryTest.java`
- Modify: `Cute-agent-app/src/test/java/org/wwz/ai/test/domain/CuteMapperNamespaceBindingTest.java`

- [ ] Add assertions that React and PlanSolve no longer call `buildHistoryDialogue`, prompt-memory mapper namespaces point to infrastructure DAOs, and UI replay remains ledger-backed.
- [ ] Run `mvn -pl Cute-agent-app test -Dtest='CutePersistenceBoundaryTest,CuteMapperNamespaceBindingTest,*PromptMemory*' -DskipTests=false`; expect PASS.
- [ ] Run `mvn -pl Cute-agent-app test -DskipTests=false`; expect PASS with existing external-service exclusions.
- [ ] Run `mvn clean compile`; expect `BUILD SUCCESS`.
- [ ] Commit with `git commit -m "test: verify prompt memory boundaries"`.
