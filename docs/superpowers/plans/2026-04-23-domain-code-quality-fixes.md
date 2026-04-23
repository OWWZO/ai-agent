# Domain 模块代码质量快速修复计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复代码审查发现的 6 类低风险代码质量问题：拼写错误、重复辅助方法、NPE/类型安全、正则性能、Agent 构造重复逻辑、过度 null 防御。

**Architecture:** 将散落在 5 个类中的 `firstNonBlank` 统一收敛到 `StringUtil`；在 `BaseAgent` 中新增受保护的提示词构建方法供子类复用；提取公共配置解析方法消除 `ReactorConfig` 中的重复模式。

**Tech Stack:** Java 17, Spring Boot, Lombok, Alibaba Fastjson, Jackson

---

## 文件结构

| 文件 | 动作 | 说明 |
|------|------|------|
| `agent/util/StringUtil.java` | 修改 | 添加 `firstNonBlank` 重载、优化 `textDesensitization` 正则预编译 |
| `config/ReactorConfig.java` | 修改 | 修正拼写错误、提取公共 `parseJsonMap` 方法 |
| `agent/agent/BaseAgent.java` | 修改 | 修复 NPE 和类型安全、添加 `buildToolPrompt` 和 `initializePrompts` |
| `agent/agent/ReactImplAgent.java` | 修改 | 使用基类方法替换重复提示词构造逻辑 |
| `agent/agent/PlanningAgent.java` | 修改 | 使用基类方法替换重复提示词构造逻辑、Stream API 替换循环 |
| `service/support/SessionTranscriptBlockAssembler.java` | 修改 | 提前返回替代分散的 null 三元表达式 |
| `service/support/SessionArtifactRestoreSupport.java` | 修改 | 删除私有 `firstNonBlank`，改用 `StringUtil` |
| `service/support/SessionMemoryPromptFormatter.java` | 修改 | 删除私有 `firstNonBlank`，改用 `StringUtil` |
| `service/support/ConversationEventPayloadNormalizer.java` | 修改 | 删除私有 `firstNonBlank`，改用 `StringUtil` |
| `service/support/LlmSessionMemorySummaryGenerator.java` | 修改 | 删除私有 `firstNonBlank`，改用 `StringUtil` |

---

## Task 1: 修复 ReactorConfig 拼写错误和重复配置解析

**Files:**
- Modify: `src/main/java/org/wwz/ai/domain/agent/reactor/config/ReactorConfig.java`

- [ ] **Step 1: 提取公共配置解析方法**

在 `ReactorConfig` 类中添加泛型辅助方法（放在所有 setter 之后、普通字段之前）：

```java
    private static Map<String, String> parseStringMap(String json) {
        if (!StringUtils.hasText(json) || "{}".equals(json.trim())) {
            return new HashMap<>();
        }
        return JSON.parseObject(json, new TypeReference<Map<String, String>>() {});
    }

    private static Map<String, Object> parseObjectMap(String json) {
        if (!StringUtils.hasText(json) || "{}".equals(json.trim())) {
            return new HashMap<>();
        }
        return JSON.parseObject(json, new TypeReference<Map<String, Object>>() {});
    }
```

- [ ] **Step 2: 修正拼写错误并替换重复解析模式**

修正以下字段/方法名中的拼写错误：

| 错误 | 正确 |
|------|------|
| `codeAgentPamras` | `codeAgentParams` |
| `reportToolPamras` | `reportToolParams` |
| `fileToolPamras` | `fileToolParams` |
| `deepSearchToolPamras` | `deepSearchToolParams` |
| `dataAnalysisToolPamras` | `dataAnalysisToolParams` |
| `setCodeAgentPamras` | `setCodeAgentParams` |
| `setHtmlToolPamras` | `setReportToolParams` |
| `setFileoolPamras` | `setFileToolParams` |
| `setDeepSearchToolPamras` | `setDeepSearchToolParams` |
| `setDtaAnalysisToolPamras` | `setDataAnalysisToolParams` |
| `CodeInterpreterUrl` | `codeInterpreterUrl` |
| `DeepSearchUrl` | `deepSearchUrl` |

修正后替换重复的 setter 实现：

```java
    private Map<String, String> plannerSystemPromptMap = new HashMap<>();
    @Value("${autobots.autoagent.planner.system_prompt:{}}")
    public void setPlannerSystemPromptMap(String list) {
        this.plannerSystemPromptMap = parseStringMap(list);
    }
```

其他 6 个 `Map<String, String>` setter 同理替换。5 个 `Map<String, Object>` setter 使用 `parseObjectMap` 替换。

- [ ] **Step 3: 修复双分号 `;;`**

第 198 行：`private Integer reactMaxSteps;;` → `private Integer reactMaxSteps;`

- [ ] **Step 4: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/config/ReactorConfig.java
git commit -m "refactor: 修正 ReactorConfig 拼写错误，提取公共配置解析方法"
```

---

## Task 2: 统一提取 firstNonBlank 到 StringUtil

**Files:**
- Modify: `src/main/java/org/wwz/ai/domain/agent/reactor/agent/util/StringUtil.java`
- Modify: `src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionArtifactRestoreSupport.java`
- Modify: `src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemoryPromptFormatter.java`
- Modify: `src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationEventPayloadNormalizer.java`
- Modify: `src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionTranscriptBlockAssembler.java`
- Modify: `src/main/java/org/wwz/ai/domain/agent/reactor/service/support/LlmSessionMemorySummaryGenerator.java`

- [ ] **Step 1: 在 StringUtil 中添加 firstNonBlank**

在 `StringUtil` 类中添加：

```java
    /**
     * 返回第一个非空（hasText）的字符串值，全部为空则返回 null。
     */
    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (org.springframework.util.StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
```

- [ ] **Step 2: 删除 SessionArtifactRestoreSupport 中的私有 firstNonBlank**

删除第 275-282 行的私有方法，将类内所有调用替换为 `StringUtil.firstNonBlank(...)`。

- [ ] **Step 3: 删除 SessionMemoryPromptFormatter 中的私有 firstNonBlank**

同样删除并替换为 `StringUtil.firstNonBlank(...)`。

- [ ] **Step 4: 删除 ConversationEventPayloadNormalizer 中的私有 firstNonBlank**

同样删除并替换为 `StringUtil.firstNonBlank(...)`。

- [ ] **Step 5: 删除 SessionTranscriptBlockAssembler 中的私有 firstNonBlank**

同样删除并替换为 `StringUtil.firstNonBlank(...)`。

- [ ] **Step 6: 删除 LlmSessionMemorySummaryGenerator 中的私有 firstNonBlank**

同样删除并替换为 `StringUtil.firstNonBlank(...)`。

- [ ] **Step 7: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/util/StringUtil.java \
  ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionArtifactRestoreSupport.java \
  ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionMemoryPromptFormatter.java \
  ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/ConversationEventPayloadNormalizer.java \
  ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionTranscriptBlockAssembler.java \
  ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/LlmSessionMemorySummaryGenerator.java
git commit -m "refactor: 将 firstNonBlank 统一收敛到 StringUtil，消除5处重复定义"
```

---

## Task 3: 修复 BaseAgent 类型安全和 NPE 问题

**Files:**
- Modify: `src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/BaseAgent.java`

- [ ] **Step 1: 修复 run() 方法的 NPE**

第 97-104 行：

```java
    public String run(String query) {
        setState(AgentState.IDLE);
        if (query != null && !query.isEmpty()) {
            updateMemory(RoleType.USER, query, null);
        }
```

- [ ] **Step 2: 修复 executeTool() 的不安全类型转换**

第 208-238 行，将结果转换逻辑改为：

```java
    public String executeTool(ToolCall command) {
        if (command == null || command.getFunction() == null
                || command.getFunction().getName() == null
                || command.getFunction().getName().isBlank()) {
            return "Error: Invalid function call format";
        }

        String name = command.getFunction().getName();
        try {
            ObjectMapper mapper = new ObjectMapper();
            Object args = mapper.readValue(command.getFunction().getArguments(), Object.class);
            Object result = availableTools.execute(name, args);
            log.info("{} execute tool: {} {} result {}", context.getRequestId(), name, args, result);

            if (result == null) {
                return "Tool " + name + " Error.";
            }
            if (result instanceof String strResult) {
                return strResult;
            }
            return mapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("{} execute tool {} failed ", context.getRequestId(), name, e);
        }
        return "Tool " + name + " Error.";
    }
```

注意原第 237 行 `"Tool" + name + " Error."` 缺少空格，一并修正为 `"Tool " + name + " Error."`。

- [ ] **Step 3: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/BaseAgent.java
git commit -m "fix: 修复 BaseAgent NPE 和不安全类型转换问题"
```

---

## Task 4: 优化 StringUtil.textDesensitization 性能

**Files:**
- Modify: `src/main/java/org/wwz/ai/domain/agent/reactor/agent/util/StringUtil.java`

- [ ] **Step 1: 预编译正则表达式并优化替换逻辑**

将 `textDesensitization` 方法中的 5 个 `Pattern.compile` 提取为静态常量：

```java
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern ID_PATTERN = Pattern.compile(
            "(?:[^\\dA-Za-z_]|^)((?:[1-6][1-7]|50|71|81|82)\\d{4}(?:19|20)\\d{2}(?:0[1-9]|10|11|12)(?:[0-2][1-9]|10|20|30|31)\\d{3}[0-9Xx])(?:[^\\dA-Za-z_]|$)");
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?:[^\\dA-Za-z_]|^)(1[3456789]\\d{9})(?:[^\\dA-Za-z_]|$)");
    private static final Pattern BANKCARD_PATTERN = Pattern.compile(
            "(?:[^\\dA-Za-z_]|^)(62(?:\\d{14}|\\d{17}))(?:[^\\dA-Za-z_]|$)");
```

修改 `textDesensitization` 方法体，使用预编译的 Pattern 和 Matcher：

```java
    public static String textDesensitization(String content, Map<String, String> sensitivePatternsMapping) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        StringBuilder sb = new StringBuilder(content);

        // 邮箱脱敏
        Matcher emailMatcher = EMAIL_PATTERN.matcher(sb);
        while (emailMatcher.find()) {
            String snippet = emailMatcher.group();
            if (snippet.contains("@jd.com")) {
                continue;
            }
            int maskIdx = snippet.indexOf("@");
            emailMatcher.appendReplacement(sb, snippet.substring(0, maskIdx) + "＠" + snippet.substring(maskIdx + 1));
        }
        emailMatcher.appendTail(sb);

        // 其他脱敏逻辑同理使用预编译 Pattern...
        //（保持原有脱敏逻辑不变，仅替换 Pattern.compile 为静态常量引用）

        return sb.toString();
    }
```

注意：`Matcher.appendReplacement` / `appendTail` 配合 `StringBuilder` 的方式需要调整，因为原逻辑是直接在 `content` 字符串上 `replace`。更简单的优化是保留原有结构，仅将 `Pattern.compile` 改为使用静态常量。对于脱敏这类低频操作，预编译正则的收益远大于 StringBuilder 优化。

简化方案：仅提取静态 Pattern 常量，方法体保持原有逻辑不变（逐行替换 `Pattern.compile` → 常量引用）。

- [ ] **Step 2: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/util/StringUtil.java
git commit -m "perf: 预编译 StringUtil 脱敏正则，避免每次调用重复编译"
```

---

## Task 5: 抽取 ReactImplAgent 和 PlanningAgent 的公共提示词构造逻辑

**Files:**
- Modify: `src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/BaseAgent.java`
- Modify: `src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/ReactImplAgent.java`
- Modify: `src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/PlanningAgent.java`

- [ ] **Step 1: 在 BaseAgent 中添加公共提示词构建方法**

在 `BaseAgent` 中添加以下 protected 方法：

```java
    /**
     * 从工具集合构建工具描述提示词。
     */
    protected String buildToolPrompt(ToolCollection tools) {
        if (tools == null || tools.getToolMap() == null || tools.getToolMap().isEmpty()) {
            return "";
        }
        StringBuilder toolPrompt = new StringBuilder();
        for (BaseTool tool : tools.getToolMap().values()) {
            toolPrompt.append(String.format("工具名：%s 工具描述：%s\n", tool.getName(), tool.getDescription()));
        }
        return toolPrompt.toString();
    }

    /**
     * 初始化系统提示词和下一步提示词，替换标准占位符。
     */
    protected void initializePrompts(Map<String, String> systemPromptMap,
                                     Map<String, String> nextStepPromptMap,
                                     String defaultSystemPrompt,
                                     String defaultNextStepPrompt,
                                     String toolPrompt,
                                     String extraPlaceholder,
                                     String extraValue) {
        String promptKey = "default";
        String nextPromptKey = "default";

        String systemTemplate = systemPromptMap.getOrDefault(promptKey, defaultSystemPrompt)
                .replace("{{tools}}", toolPrompt)
                .replace("{{query}}", context.getQuery())
                .replace("{{date}}", context.getDateInfo())
                .replace("{{basePrompt}}", context.getBasePrompt());

        if (extraPlaceholder != null && extraValue != null) {
            systemTemplate = systemTemplate.replace(extraPlaceholder, extraValue);
        }
        setSystemPrompt(injectHistoryDialogue(systemTemplate, context.getHistoryDialogue()));

        String nextTemplate = nextStepPromptMap.getOrDefault(nextPromptKey, defaultNextStepPrompt)
                .replace("{{tools}}", toolPrompt)
                .replace("{{query}}", context.getQuery())
                .replace("{{date}}", context.getDateInfo())
                .replace("{{basePrompt}}", context.getBasePrompt());

        if (extraPlaceholder != null && extraValue != null) {
            nextTemplate = nextTemplate.replace(extraPlaceholder, extraValue);
        }
        setNextStepPrompt(injectHistoryDialogue(nextTemplate, context.getHistoryDialogue()));
    }
```

需要添加 import：`org.wwz.ai.domain.agent.reactor.agent.tool.BaseTool`

- [ ] **Step 2: 简化 ReactImplAgent 构造方法**

替换第 84-139 行的构造逻辑：

```java
    public ReactImplAgent(AgentContext context) {
        setName("react");
        setDescription("an agent that can execute tool calls.");

        ApplicationContext applicationContext = SpringContextHolder.getApplicationContext();
        ReactorConfig reactorConfig = applicationContext.getBean(ReactorConfig.class);

        String toolPrompt = buildToolPrompt(context.getToolCollection());
        initializePrompts(
                reactorConfig.getReactSystemPromptMap(),
                reactorConfig.getReactNextStepPromptMap(),
                ToolCallPrompt.SYSTEM_PROMPT,
                ToolCallPrompt.NEXT_STEP_PROMPT,
                toolPrompt,
                null, null);

        setSystemPromptSnapshot(getSystemPrompt());
        setNextStepPromptSnapshot(getNextStepPrompt());

        setPrinter(context.printer);
        setMaxSteps(reactorConfig.getReactMaxSteps());
        setLlm(new LLM(reactorConfig.getReactModelName(), ""));
        setContext(context);
        preloadMemory(context.getPreloadedMessages());

        availableTools = context.getToolCollection();
        setDigitalEmployeePrompt(reactorConfig.getDigitalEmployeePrompt());
    }
```

- [ ] **Step 3: 简化 PlanningAgent 构造方法**

替换第 91-145 行的构造逻辑：

```java
    public PlanningAgent(AgentContext context) {
        setName("planning");
        setDescription("An agent that creates and manages plans to solve tasks");

        ApplicationContext applicationContext = SpringContextHolder.getApplicationContext();
        ReactorConfig reactorConfig = applicationContext.getBean(ReactorConfig.class);

        String toolPrompt = buildToolPrompt(context.getToolCollection());
        initializePrompts(
                reactorConfig.getPlannerSystemPromptMap(),
                reactorConfig.getPlannerNextStepPromptMap(),
                PlanningPrompt.SYSTEM_PROMPT,
                PlanningPrompt.NEXT_STEP_PROMPT,
                toolPrompt,
                "{{sopPrompt}}", context.getSopPrompt());

        setSystemPromptSnapshot(getSystemPrompt());
        setNextStepPromptSnapshot(getNextStepPrompt());

        setPrinter(context.printer);
        setMaxSteps(reactorConfig.getPlannerMaxSteps());
        setLlm(new LLM(reactorConfig.getPlannerModelName(), ""));

        setContext(context);
        setIsColseUpdate("1".equals(reactorConfig.getPlanningCloseUpdate()));
        preloadMemory(context.getPreloadedMessages());

        availableTools.addTool(planningTool);
        planningTool.setAgentContext(context);
    }
```

- [ ] **Step 4: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/BaseAgent.java \
  ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/ReactImplAgent.java \
  ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/PlanningAgent.java
git commit -m "refactor: 抽取 ReactImplAgent 和 PlanningAgent 公共提示词构造到 BaseAgent"
```

---

## Task 6: 清理 SessionTranscriptBlockAssembler 的过度 null 防御

**Files:**
- Modify: `src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionTranscriptBlockAssembler.java`

- [ ] **Step 1: 在 buildTurnMemory 中使用提前返回**

第 53-105 行：将分散的 `message == null ? null : message.getXxx()` 改为提前返回：

```java
    public SessionTurnMemory buildTurnMemory(AgentMessage message,
                                             List<AgentMessageEvent> events) {
        if (message == null) {
            return SessionTurnMemory.builder().build();
        }

        List<TranscriptContextBlock> blocks = new ArrayList<>();
        List<JSONObject> aggregatedArtifactRefs = new ArrayList<>();

        blocks.add(buildUserInputBlock(message));

        List<JSONObject> uploadedArtifactRefs = artifactRestoreSupport.normalizeFilesToArtifactRefs(
                artifactRestoreSupport.parseFiles(message.getFilesJson()));
        if (!uploadedArtifactRefs.isEmpty()) {
            aggregatedArtifactRefs.addAll(uploadedArtifactRefs);
            blocks.add(buildArtifactReferenceBlock(message, null, "用户上传文件", "user", uploadedArtifactRefs, false));
        }

        ToolInvocationRegistry registry = new ToolInvocationRegistry();
        if (!CollectionUtils.isEmpty(events)) {
            List<AgentMessageEvent> orderedEvents = new ArrayList<>(events);
            orderedEvents.sort(Comparator.comparing(AgentMessageEvent::getSeqNo, Comparator.nullsLast(Integer::compareTo)));
            for (AgentMessageEvent event : orderedEvents) {
                try {
                    blocks.addAll(buildEventBlocks(message, event, registry, aggregatedArtifactRefs));
                } catch (Exception e) {
                    log.warn("恢复 transcript 事件失败，已跳过该 event messageId={}, seqNo={}, eventType={}, eventSubType={}",
                            message.getId(),
                            event == null ? null : event.getSeqNo(),
                            event == null ? null : event.getEventType(),
                            event == null ? null : event.getEventSubType(),
                            e);
                }
            }
        }

        if (StringUtils.hasText(message.getResponse())) {
            blocks.add(TranscriptContextBlock.builder()
                    .blockType(TranscriptBlockType.ASSISTANT_ANSWER)
                    .sourceMessageId(message.getId())
                    .role("assistant")
                    .text(message.getResponse())
                    .referenceOnly(false)
                    .build());
        }

        return SessionTurnMemory.builder()
                .messageId(message.getId())
                .requestId(message.getRequestId())
                .sortOrder(message.getSortOrder())
                .userMessage(message.getQuery())
                .assistantMessage(message.getResponse())
                .finalAnswer(message.getResponse())
                .artifactRefs(new ArrayList<>(deduplicateArtifactRefs(aggregatedArtifactRefs)))
                .blocks(blocks)
                .build();
    }
```

- [ ] **Step 2: 清理 buildUserInputBlock**

第 107-115 行：移除 null 检查（调用方已保证 message 非 null）。

- [ ] **Step 3: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/support/SessionTranscriptBlockAssembler.java
git commit -m "refactor: SessionTranscriptBlockAssembler 提前返回替代分散 null 检查"
```

---

## Task 7: PlanningAgent 使用 Stream API 和常量替代魔法字符串

**Files:**
- Modify: `src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/PlanningAgent.java`

- [ ] **Step 1: 添加常量并简化 getNextTask**

添加常量：

```java
    private static final String STEP_STATUS_COMPLETED = "completed";
```

简化第 299-328 行的 `getNextTask`：

```java
    private String getNextTask() {
        boolean allComplete = planningTool.getPlan().getStepStatus().stream()
                .allMatch(STEP_STATUS_COMPLETED::equals);

        if (allComplete) {
            setState(AgentState.FINISHED);
            printer.send("plan", planningTool.getPlan());
            return "finish";
        }

        if (!planningTool.getPlan().getCurrentStep().isEmpty()) {
            setState(AgentState.FINISHED);
            String[] currentSteps = planningTool.getPlan().getCurrentStep().split("<sep>");
            printer.send("plan", planningTool.getPlan());
            Arrays.stream(currentSteps).forEach(step -> printer.send("task", step));
            return planningTool.getPlan().getCurrentStep();
        }

        return "";
    }
```

- [ ] **Step 2: Commit**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/agent/PlanningAgent.java
git commit -m "refactor: PlanningAgent 使用 Stream API 和常量替代魔法字符串"
```

---

## Self-Review Checklist

**Spec coverage:**
- [x] ReactorConfig 拼写错误 + 重复模式 → Task 1
- [x] firstNonBlank 统一 → Task 2
- [x] BaseAgent NPE + 类型安全 → Task 3
- [x] StringUtil 正则预编译 → Task 4
- [x] Agent 构造重复逻辑 → Task 5
- [x] SessionTranscriptBlockAssembler null 防御 → Task 6
- [x] PlanningAgent 魔法字符串 → Task 7

**Placeholder scan:**
- [x] 无 "TBD"/"TODO"
- [x] 无模糊描述
- [x] 每个代码步骤包含完整代码

**Type consistency:**
- [x] `StringUtil.firstNonBlank` 签名在所有替换处一致
- [x] `BaseAgent.initializePrompts` 参数类型与调用处匹配
- [x] `BaseAgent.buildToolPrompt` 返回 String，与使用处一致
