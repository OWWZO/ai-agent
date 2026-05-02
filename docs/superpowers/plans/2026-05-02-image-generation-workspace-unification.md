# 生图工作台与 image_generation_tool 收敛改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 抽离统一的生图执行内核，让生图工作台与 `image_generation_tool` 复用同一条执行与持久化主链路；工作台历史彻底改为从 `ai_agent_tool_output_image_generation + ai_agent_artifact` 读取，并删除遗留的 `ai_agent_image_generation_record`。

**Architecture:** 这次改造把“发请求并归一化结果”和“把结果落到通用账本”彻底拆开。新的执行内核只负责组装请求、调用 `/v1/tool/image_generation`、归一化结果；`WorkspaceImageGenerationServiceImpl` 与 `ImageGenerationTool` 只做各自上下文适配。工作台路径通过一个独立的事务型 persistence service 调用 `ToolOutputWriter` / `AgentExecutionRecorder` 的 strict 写入方法，保证批次主表与 artifact 明细同事务提交，同时避免把外部 HTTP 调用包进数据库事务。

**Tech Stack:** Java 17, Spring Boot 3.4.3, MyBatis / MyBatis-Plus, MySQL 8, OkHttp, Reactor Tool Python image generation endpoint

---

## 关键决策

- 工作台历史不再存 `deviceId` / `sessionId`，也不再按设备隔离。
- 工作台历史只读取 `request_source = 'workspace'` 的生图批次，避免把普通对话里的 `image_generation_tool` 结果混入工作台历史。
- `ai_agent_tool_output_image_generation` 作为“批次主表”，一批次一行。
- `ai_agent_artifact` 作为“图片明细表”，一张图一行。
- `file_size` 不合并进 `ai_agent_tool_output_image_generation`，继续留在 `ai_agent_artifact.file_size`，因为它是单文件粒度。
- `ImageGenerationTool` 不再手写 SSE 解析；它改走与工作台一致的同步 gateway 路径，因为当前 Java 侧本来也只消费最终结果，不消费中间 token。
- 工作台持久化必须保留事务边界，但事务只覆盖数据库写入，不覆盖外部生图 HTTP 调用。
- 仅补 `@Transactional` 不够，因为当前 `ToolOutputWriter.write(...)` 与 `AgentExecutionRecorder.recordArtifacts(...)` 是 `fail-open` 语义，内部会吞异常；因此必须补 `writeOrThrow(...)` / `recordArtifactsOrThrow(...)` 这类 strict 写入入口。

## 文件结构

### 新增文件

- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/imagegeneration/ImageGenerationExecuteCommand.java`
  - 统一的生图执行命令，屏蔽工作台命令与 tool 参数差异。
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/imagegeneration/ImageGenerationExecutionResult.java`
  - 统一的执行结果，承载批次字段、原始响应和归一化文件列表。
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/imagegeneration/IImageGenerationExecutionKernel.java`
  - 生图执行内核接口。
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/imagegeneration/impl/ImageGenerationExecutionKernelImpl.java`
  - 生图执行内核实现，复用 `IReactorImageGenerationGateway`。
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/imagegeneration/IImageGenerationBatchPersistenceService.java`
  - 工作台事务型批次持久化接口。
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/imagegeneration/impl/ImageGenerationBatchPersistenceServiceImpl.java`
  - 工作台事务型批次持久化实现，内部调用 strict writer / recorder。
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/imagegeneration/ImageGenerationWorkspaceConstants.java`
  - 工作台专用常量，例如 `request_source=workspace`、工作台 synthetic `toolCallId`。
- Create: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ImageGenerationExecutionKernelTest.java`
  - 内核单测，覆盖请求归一化与返回归一化。

### 修改文件

- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/imagegeneration/ImageGenerationGatewayRequest.java`
  - 增加 `model`，让 tool 与 workspace 统一走 gateway 时不丢失模型能力。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/ImageGenerationToolOutput.java`
  - 增加 `size / batchCount / sourceImageCount / maskImageCount / usedFallback`。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/ToolOutputPersistCommand.java`
  - 增加 `requestSource`。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/ToolOutputView.java`
  - 增加 `createdAt`，便于工作台历史页直接消费。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/ArtifactRecord.java`
  - 增加 `requestId`。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/ArtifactView.java`
  - 增加 `requestId`。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/AgentExecutionRecorder.java`
  - 增加 strict artifact 写入方法。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentExecutionRecorderImpl.java`
  - `recordArtifacts(...)` 允许 `runId` 为空并落 `requestId`；增加 `recordArtifactsOrThrow(...)`；`persistStructuredOutput(...)` 为 tool 路径补 `requestSource = agent`。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/WorkspaceImageGenerationServiceImpl.java`
  - 删除 `deviceId` 依赖，改为复用执行内核、事务型 persistence service 与 `ToolOutputReader`。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IWorkspaceImageGenerationService.java`
  - 去掉 `deviceId` 参数。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/tooloutput/ToolOutputWriter.java`
  - 增加 strict 写入方法。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/ImageGenerationTool.java`
  - 改用执行内核，不再直连 OkHttp SSE。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IToolOutputImageGenerationDao.java`
  - 增加工作台历史分页查询方法。
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IArtifactLedgerDao.java`
  - 增加按 `requestId + toolCallId` 查询 artifact 的方法。
- Modify: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/tooloutput/ToolOutputWriterImpl.java`
  - 写入 `request_source` 与新增批次字段；补 `writeOrThrow(...)`，`write(...)` 保持 fail-open 包装。
- Modify: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/tooloutput/ToolOutputReaderImpl.java`
  - 读取新增批次字段；当 `runId/toolInvocationId` 为空时，回退按 `requestId + toolCallId` 查 artifacts。
- Modify: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/AbstractToolOutputPO.java`
  - 增加 `requestSource`。
- Modify: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/ToolOutputImageGenerationPO.java`
  - 增加新增批次字段。
- Modify: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/gateway/ReactorImageGenerationGateway.java`
  - 透传 `model`。
- Modify: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentImageGenerationController.java`
  - 删除 `resolveDeviceId(...)` 强校验，调用新 service 签名。
- Modify: `ai-agent-station-study-app/src/main/resources/db/schema.sql`
  - 删除旧表；扩展 `ai_agent_tool_output_image_generation` 与 `ai_agent_artifact`。
- Modify: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/tool_output_image_generation_mapper.xml`
  - 新增字段与工作台历史分页 SQL。
- Modify: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/artifact_ledger_mapper.xml`
  - 落 `request_id`，并增加按 `request_id + tool_call_id` 查 artifact 的 SQL。
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/WorkspaceImageGenerationServiceTest.java`
  - 重写为共享落库链路测试，不再引用 `IAgentImageGenerationRecordDao`。
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentImageGenerationControllerTest.java`
  - 删除 `X-Device-Id` 缺失即失败的断言。
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ImageGenerationToolTest.java`
  - 校验 tool 走共享 kernel 后仍能产出 file 事件与完整 structured output。

### 删除文件

- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentImageGenerationRecord.java`
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentImageGenerationRecordDao.java`
- Delete: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_image_generation_record_mapper.xml`

---

## Task 1: 抽离统一生图执行内核

**Files:**
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/imagegeneration/ImageGenerationExecuteCommand.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/imagegeneration/ImageGenerationExecutionResult.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/imagegeneration/IImageGenerationExecutionKernel.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/imagegeneration/impl/ImageGenerationExecutionKernelImpl.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/imagegeneration/ImageGenerationGatewayRequest.java`
- Modify: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/gateway/ReactorImageGenerationGateway.java`
- Test: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ImageGenerationExecutionKernelTest.java`

- [ ] **Step 1: 先写内核失败测试**

```java
@Test
public void test_executeNormalizesGatewayRequestAndResponse() {
    IReactorImageGenerationGateway gateway = Mockito.mock(IReactorImageGenerationGateway.class);
    AtomicReference<ImageGenerationGatewayRequest> captured = new AtomicReference<>();
    Mockito.when(gateway.generate(Mockito.any(ImageGenerationGatewayRequest.class)))
            .thenAnswer(invocation -> {
                ImageGenerationGatewayRequest request = invocation.getArgument(0);
                captured.set(request);
                return ImageGenerationGatewayResponse.builder()
                        .requestId(request.getRequestId())
                        .mode("images")
                        .data("生成完成")
                        .usedFallback(true)
                        .rawResponse(Map.of("traceId", "raw-1"))
                        .fileInfo(List.of(
                                ImageGenerationGatewayFile.builder()
                                        .fileName("poster.png")
                                        .domainUrl("https://file.example.com/poster.png")
                                        .fileSize(512L)
                                        .mimeType("image/png")
                                        .build()
                        ))
                        .build();
            });

    ImageGenerationExecutionKernelImpl kernel = new ImageGenerationExecutionKernelImpl(gateway);
    ImageGenerationExecutionResult result = kernel.execute(ImageGenerationExecuteCommand.builder()
            .requestId("req-kernel-001")
            .prompt(" 生成海报 ")
            .mode("images")
            .size("")
            .n(2)
            .model("gpt-image-1")
            .build());

    Assert.assertEquals("req-kernel-001", captured.get().getRequestId());
    Assert.assertEquals("生成海报", captured.get().getPrompt());
    Assert.assertEquals("1024x1024", captured.get().getSize());
    Assert.assertEquals("gpt-image-1", captured.get().getModel());
    Assert.assertEquals(Integer.valueOf(2), result.getBatchCount());
    Assert.assertTrue(result.getUsedFallback());
    Assert.assertEquals("https://file.example.com/poster.png", result.getFiles().get(0).getPreviewUrl());
}
```

- [ ] **Step 2: 运行单测，确认当前代码尚不具备内核能力**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=ImageGenerationExecutionKernelTest -DskipTests=false`

Expected: FAIL，提示 `ImageGenerationExecuteCommand` / `ImageGenerationExecutionResult` / `ImageGenerationExecutionKernelImpl` 不存在。

- [ ] **Step 3: 实现命令模型、结果模型和执行内核**

```java
public interface IImageGenerationExecutionKernel {

    ImageGenerationExecutionResult execute(ImageGenerationExecuteCommand command);
}
```

```java
@Service
@RequiredArgsConstructor
public class ImageGenerationExecutionKernelImpl implements IImageGenerationExecutionKernel {

    private static final String DEFAULT_IMAGE_SIZE = "1024x1024";

    private final IReactorImageGenerationGateway imageGenerationGateway;

    @Override
    public ImageGenerationExecutionResult execute(ImageGenerationExecuteCommand command) {
        ImageGenerationGatewayRequest request = ImageGenerationGatewayRequest.builder()
                .requestId(command.getRequestId())
                .prompt(StringUtils.trim(command.getPrompt()))
                .mode(StringUtils.trimToNull(command.getMode()))
                .fileNames(defaultList(command.getFileNames()))
                .maskFileNames(defaultList(command.getMaskFileNames()))
                .fileName(StringUtils.trimToNull(command.getFileName()))
                .fileDescription(StringUtils.trimToNull(command.getFileDescription()))
                .size(StringUtils.defaultIfBlank(StringUtils.trim(command.getSize()), DEFAULT_IMAGE_SIZE))
                .n(command.getN() == null ? 1 : command.getN())
                .timeoutSeconds(command.getTimeoutSeconds() == null ? 300 : command.getTimeoutSeconds())
                .model(StringUtils.trimToNull(command.getModel()))
                .stream(Boolean.FALSE)
                .build();

        ImageGenerationGatewayResponse response = imageGenerationGateway.generate(request);
        List<WorkspaceImageFile> files = normalizeFiles(response.getFileInfo());

        return ImageGenerationExecutionResult.builder()
                .requestId(request.getRequestId())
                .prompt(request.getPrompt())
                .mode(StringUtils.defaultIfBlank(response.getMode(), request.getMode()))
                .summary(StringUtils.defaultIfBlank(response.getData(), "生成完成"))
                .size(request.getSize())
                .batchCount(files.size())
                .sourceImageCount(request.getFileNames().size())
                .maskImageCount((int) request.getMaskFileNames().stream().filter(StringUtils::isNotBlank).count())
                .usedFallback(Boolean.TRUE.equals(response.getUsedFallback()))
                .rawResponse(response.getRawResponse())
                .files(files)
                .build();
    }
}
```

- [ ] **Step 4: 重新运行内核测试**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=ImageGenerationExecutionKernelTest -DskipTests=false`

Expected: PASS

- [ ] **Step 5: 提交内核抽离**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/imagegeneration/ImageGenerationExecuteCommand.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/imagegeneration/ImageGenerationExecutionResult.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/imagegeneration/IImageGenerationExecutionKernel.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/imagegeneration/impl/ImageGenerationExecutionKernelImpl.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/imagegeneration/ImageGenerationGatewayRequest.java
git add ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/gateway/ReactorImageGenerationGateway.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ImageGenerationExecutionKernelTest.java
git commit -m "refactor: extract shared image generation kernel"
```

## Task 2: 让通用持久化主链路支持工作台批次与非 run artifact

**Files:**
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/ImageGenerationToolOutput.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/ToolOutputPersistCommand.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/ToolOutputView.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/ArtifactRecord.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/ArtifactView.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/AgentExecutionRecorder.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IToolOutputImageGenerationDao.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IArtifactLedgerDao.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentExecutionRecorderImpl.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/tooloutput/ToolOutputWriter.java`
- Modify: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/AbstractToolOutputPO.java`
- Modify: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/ToolOutputImageGenerationPO.java`
- Modify: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/tooloutput/ToolOutputWriterImpl.java`
- Modify: `ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/tooloutput/ToolOutputReaderImpl.java`
- Modify: `ai-agent-station-study-app/src/main/resources/db/schema.sql`
- Modify: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/tool_output_image_generation_mapper.xml`
- Modify: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/artifact_ledger_mapper.xml`

- [ ] **Step 1: 先写工作台历史失败测试，锁定“批次主表 + artifact 明细表”目标行为**

```java
@Test
public void test_queryHistoryReadsWorkspaceBatchesFromToolOutputAndArtifacts() {
    WorkspaceImageGenerationServiceImpl service = new WorkspaceImageGenerationServiceImpl();
    InMemoryToolOutputImageGenerationDao toolOutputDao = new InMemoryToolOutputImageGenerationDao();
    StubToolOutputReader reader = new StubToolOutputReader();

    toolOutputDao.reset(List.of(
            row("req-workspace-new", "workspace-image-generation", "workspace", "最新工作台批次", LocalDateTime.of(2026, 5, 2, 10, 0, 0)),
            row("req-agent-001", "tool-call-1", "agent", "对话批次", LocalDateTime.of(2026, 5, 2, 11, 0, 0)),
            row("req-workspace-old", "workspace-image-generation", "workspace", "旧工作台批次", LocalDateTime.of(2026, 5, 1, 10, 0, 0))
    ));
    reader.register("req-workspace-new", "workspace-image-generation", output("最新工作台批次", "images", "1024x1024", 2));
    reader.register("req-workspace-old", "workspace-image-generation", output("旧工作台批次", "edits", "1536x1024", 1));

    ReflectionTestUtils.setField(service, "imageGenerationOutputDao", toolOutputDao);
    ReflectionTestUtils.setField(service, "toolOutputReader", reader);

    WorkspaceImageGenerationHistoryPage page = service.queryHistory(1, 10);

    Assert.assertEquals(2, page.getTotal());
    Assert.assertEquals(List.of("req-workspace-new", "req-workspace-old"),
            page.getList().stream().map(WorkspaceImageGenerationHistoryBatch::getRequestId).toList());
}
```

- [ ] **Step 2: 运行工作台历史测试，确认当前实现仍绑定旧表与 deviceId**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=WorkspaceImageGenerationServiceTest -DskipTests=false`

Expected: FAIL，提示 `queryHistory(String deviceId, ...)` 签名不匹配，或 service 仍依赖 `IAgentImageGenerationRecordDao`。

- [ ] **Step 3: 扩展结构化输出、artifact 契约、strict 写入接口与 SQL**

```java
public class ImageGenerationToolOutput implements ToolStructuredOutput {
    private String prompt;
    private String mode;
    private String summary;
    private String size;
    private Integer batchCount;
    private Integer sourceImageCount;
    private Integer maskImageCount;
    private Boolean usedFallback;
    @Builder.Default
    private List<ToolFileRef> fileRefs = new ArrayList<>();
}
```

```java
public class ToolOutputPersistCommand {
    private Long toolInvocationId;
    private Long runId;
    private String requestId;
    private String sessionId;
    private String toolCallId;
    private String toolName;
    private String requestSource;
    private Integer status;
    private String errorMsg;
    private ToolStructuredOutput structuredOutput;
}
```

```sql
ALTER TABLE ai_agent_tool_output_image_generation
    ADD COLUMN request_source VARCHAR(32) NOT NULL DEFAULT 'agent' COMMENT '请求来源 workspace / agent' AFTER tool_call_id,
    ADD COLUMN size VARCHAR(32) NULL COMMENT '输出尺寸' AFTER summary,
    ADD COLUMN batch_count INT NOT NULL DEFAULT 1 COMMENT '本批次生成图片总数' AFTER size,
    ADD COLUMN source_image_count INT NOT NULL DEFAULT 0 COMMENT '参考图数量' AFTER batch_count,
    ADD COLUMN mask_image_count INT NOT NULL DEFAULT 0 COMMENT '蒙版图数量' AFTER source_image_count,
    ADD COLUMN used_fallback TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否走兼容降级接口' AFTER mask_image_count,
    ADD KEY idx_request_source_created (request_source, created_at DESC);

ALTER TABLE ai_agent_artifact
    MODIFY COLUMN run_id BIGINT NULL COMMENT '所属 run ID',
    ADD COLUMN request_id VARCHAR(64) NOT NULL COMMENT '请求ID' AFTER run_id,
    DROP INDEX uk_run_tool_storage,
    ADD UNIQUE KEY uk_request_tool_storage (request_id, tool_call_id, storage_key),
    ADD KEY idx_artifact_request_tool (request_id, tool_call_id, deleted, create_time DESC);
```

```java
public interface ToolOutputWriter {

    void write(ToolOutputPersistCommand command);

    void writeOrThrow(ToolOutputPersistCommand command);
}
```

```java
public interface AgentExecutionRecorder {

    void recordArtifacts(List<ArtifactRecordCommand> records);

    void recordArtifactsOrThrow(List<ArtifactRecordCommand> records);
}
```

```java
// AgentExecutionRecorderImpl.recordArtifacts(...)
if (record == null || StringUtils.isBlank(record.getRequestId()) || StringUtils.isBlank(record.getFileName())) {
    continue;
}
entities.add(ArtifactRecord.builder()
        .runId(record.getRunId())
        .requestId(record.getRequestId())
        .toolInvocationId(record.getToolInvocationId())
        .toolCallId(record.getToolCallId())
        // ...
        .build());
```

- [ ] **Step 4: 重新运行工作台历史测试，确认公共持久化契约已具备工作台能力**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=WorkspaceImageGenerationServiceTest -DskipTests=false`

Expected: 仍可能 FAIL，但失败点应从“缺字段/旧 DAO”收敛为“workspace service 尚未接入新持久化链路”。

- [ ] **Step 5: 提交共享持久化契约改造**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/ImageGenerationToolOutput.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/ToolOutputPersistCommand.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/tooloutput/ToolOutputView.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/ArtifactRecord.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/ledger/ArtifactView.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/AgentExecutionRecorder.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IToolOutputImageGenerationDao.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IArtifactLedgerDao.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/AgentExecutionRecorderImpl.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/tooloutput/ToolOutputWriter.java
git add ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/AbstractToolOutputPO.java
git add ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/dao/po/ToolOutputImageGenerationPO.java
git add ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/tooloutput/ToolOutputWriterImpl.java
git add ai-agent-station-study-infrastructure/src/main/java/org/wwz/ai/infrastructure/tooloutput/ToolOutputReaderImpl.java
git add ai-agent-station-study-app/src/main/resources/db/schema.sql
git add ai-agent-station-study-app/src/main/resources/mybatis/mapper/tool_output_image_generation_mapper.xml
git add ai-agent-station-study-app/src/main/resources/mybatis/mapper/artifact_ledger_mapper.xml
git commit -m "refactor: extend shared image generation persistence"
```

## Task 3: 重写工作台服务，统一落到事务型批次持久化服务

**Files:**
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IWorkspaceImageGenerationService.java`
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/WorkspaceImageGenerationServiceImpl.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/imagegeneration/IImageGenerationBatchPersistenceService.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/imagegeneration/impl/ImageGenerationBatchPersistenceServiceImpl.java`
- Create: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/imagegeneration/ImageGenerationWorkspaceConstants.java`
- Modify: `ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentImageGenerationController.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/WorkspaceImageGenerationServiceTest.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentImageGenerationControllerTest.java`

- [ ] **Step 1: 先改失败测试，明确 service 不再接收 deviceId，且生成后写批次表 + artifact 表**

```java
@Test
public void test_generatePersistsWorkspaceBatchAndArtifacts() {
    WorkspaceImageGenerationServiceImpl service = new WorkspaceImageGenerationServiceImpl();
    RecordingImageGenerationBatchPersistenceService persistenceService = new RecordingImageGenerationBatchPersistenceService();

    ReflectionTestUtils.setField(service, "imageGenerationExecutionKernel", kernelReturningTwoFiles());
    ReflectionTestUtils.setField(service, "imageGenerationBatchPersistenceService", persistenceService);

    WorkspaceImageGenerationResult result = service.generate(WorkspaceImageGenerationCommand.builder()
            .requestId("req-workspace-001")
            .prompt("生成活动海报")
            .mode("images")
            .n(2)
            .build());

    Assert.assertEquals("req-workspace-001", result.getRequestId());
    Assert.assertEquals(1, persistenceService.savedBatches().size());
    Assert.assertEquals("workspace", persistenceService.savedBatches().get(0).getRequestSource());
    Assert.assertEquals(2, persistenceService.savedArtifacts().size());
}
```

```java
@Test
public void test_historyNoLongerRequiresDeviceIdHeader() {
    AgentImageGenerationController controller = new AgentImageGenerationController();
    ReflectionTestUtils.setField(controller, "workspaceImageGenerationService", new StubWorkspaceImageGenerationService());

    Response<PageRespVO<WorkspaceImageHistoryBatchRespVO>> response = controller.history(new MockHttpServletRequest(), 1, 10);

    Assert.assertEquals(ResponseCode.SUCCESS.getCode(), response.getCode());
}
```

- [ ] **Step 2: 运行工作台 service / controller 测试**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=WorkspaceImageGenerationServiceTest,AgentImageGenerationControllerTest -DskipTests=false`

Expected: FAIL，提示 service 签名仍为 `generate(String deviceId, ...)` / `queryHistory(String deviceId, ...)`，并且还没有事务型 persistence service。

- [ ] **Step 3: 实现事务型 persistence service，以及新的工作台 service 与 controller**

```java
public interface IWorkspaceImageGenerationService {

    WorkspaceImageGenerationResult generate(WorkspaceImageGenerationCommand command);

    WorkspaceImageGenerationHistoryPage queryHistory(int pageNo, int pageSize);
}
```

```java
@Service
@RequiredArgsConstructor
public class WorkspaceImageGenerationServiceImpl implements IWorkspaceImageGenerationService {

    private final IImageGenerationExecutionKernel imageGenerationExecutionKernel;
    private final IImageGenerationBatchPersistenceService imageGenerationBatchPersistenceService;
    private final ToolOutputReader toolOutputReader;
    private final IToolOutputImageGenerationDao imageGenerationOutputDao;

    @Override
    public WorkspaceImageGenerationResult generate(WorkspaceImageGenerationCommand command) {
        ImageGenerationExecutionResult executionResult = imageGenerationExecutionKernel.execute(toExecuteCommand(command));
        imageGenerationBatchPersistenceService.persistWorkspaceBatch(executionResult);
        return toWorkspaceResult(executionResult);
    }

    @Override
    public WorkspaceImageGenerationHistoryPage queryHistory(int pageNo, int pageSize) {
        int total = imageGenerationOutputDao.countByRequestSource(ImageGenerationWorkspaceConstants.REQUEST_SOURCE_WORKSPACE);
        List<Map<String, Object>> rows = imageGenerationOutputDao.queryPageByRequestSource(
                ImageGenerationWorkspaceConstants.REQUEST_SOURCE_WORKSPACE, offset(pageNo, pageSize), normalizePageSize(pageSize));
        List<WorkspaceImageGenerationHistoryBatch> batches = rows.stream()
                .map(this::toHistoryBatch)
                .toList();
        return WorkspaceImageGenerationHistoryPage.builder().total(total).list(batches).build();
    }
}
```

```java
@Service
@RequiredArgsConstructor
public class ImageGenerationBatchPersistenceServiceImpl implements IImageGenerationBatchPersistenceService {

    private final ToolOutputWriter toolOutputWriter;
    private final AgentExecutionRecorder executionRecorder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void persistWorkspaceBatch(ImageGenerationExecutionResult executionResult) {
        toolOutputWriter.writeOrThrow(ToolOutputPersistCommand.builder()
                .requestId(executionResult.getRequestId())
                .toolCallId(ImageGenerationWorkspaceConstants.WORKSPACE_TOOL_CALL_ID)
                .toolName(ToolOutputNames.IMAGE_GENERATION)
                .requestSource(ImageGenerationWorkspaceConstants.REQUEST_SOURCE_WORKSPACE)
                .status(ExecutionLedgerConstants.STATUS_SUCCESS)
                .structuredOutput(toStructuredOutput(executionResult))
                .build());

        executionRecorder.recordArtifactsOrThrow(toArtifactCommands(executionResult));
    }
}
```

```java
// AgentImageGenerationController
WorkspaceImageGenerationResult result = workspaceImageGenerationService.generate(
        WorkspaceImageGenerationCommand.builder()
                .requestId(reqVO.getRequestId())
                .prompt(reqVO.getPrompt())
                .mode(reqVO.getMode())
                .fileNames(reqVO.getFileNames())
                .maskFileNames(reqVO.getMaskFileNames())
                .fileName(reqVO.getFileName())
                .fileDescription(reqVO.getFileDescription())
                .size(reqVO.getSize())
                .n(reqVO.getN())
                .build()
);
```

- [ ] **Step 4: 重新运行工作台测试**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=WorkspaceImageGenerationServiceTest,AgentImageGenerationControllerTest -DskipTests=false`

Expected: PASS

- [ ] **Step 5: 提交工作台链路重写**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/IWorkspaceImageGenerationService.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/WorkspaceImageGenerationServiceImpl.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/imagegeneration/IImageGenerationBatchPersistenceService.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/imagegeneration/impl/ImageGenerationBatchPersistenceServiceImpl.java
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/imagegeneration/ImageGenerationWorkspaceConstants.java
git add ai-agent-station-study-trigger/src/main/java/org/wwz/ai/trigger/http/agent/AgentImageGenerationController.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/WorkspaceImageGenerationServiceTest.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/AgentImageGenerationControllerTest.java
git commit -m "refactor: route workspace image generation through shared persistence"
```

## Task 4: 重写 image_generation_tool，复用执行内核并补齐 structured output

**Files:**
- Modify: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/ImageGenerationTool.java`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ImageGenerationToolTest.java`

- [ ] **Step 1: 先改 tool 回归测试，锁定“同步 kernel + 完整批次字段 + file 事件”行为**

```java
@Test
public void shouldUseSharedKernelAndReturnRichStructuredOutput() throws Exception {
    RecordingImageGenerationHandler handler = new RecordingImageGenerationHandler();
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/v1/tool/image_generation", handler);
    server.start();

    try {
        bindSpringContext(buildConfig("http://127.0.0.1:" + server.getAddress().getPort()));
        AgentContext context = buildAgentContext();
        ImageGenerationTool tool = new ImageGenerationTool();
        tool.setAgentContext(context);

        ToolResultPayload payload = (ToolResultPayload) tool.execute(JSONObject.parseObject("""
                {"prompt":"生成活动海报","n":2,"size":"1536x1024","model":"gpt-image-1"}
                """));

        ImageGenerationToolOutput output = (ImageGenerationToolOutput) payload.getStructuredOutput();
        Assert.assertFalse(payload.getFailed());
        Assert.assertEquals("1536x1024", output.getSize());
        Assert.assertEquals(Integer.valueOf(2), output.getBatchCount());
        Assert.assertTrue(output.getUsedFallback());
        Assert.assertEquals(Boolean.FALSE, handler.getLastRequest().getStream());
        Assert.assertEquals(List.of("file"), printer.messageTypes());
    } finally {
        server.stop(0);
        ReflectionTestUtils.setField(SpringContextHolder.class, "context", null);
    }
}
```

- [ ] **Step 2: 运行 tool 回归测试**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=ImageGenerationToolTest -DskipTests=false`

Expected: FAIL，提示当前 tool 仍在走旧的 `callImageGenerationStream(...)`，structured output 也没有 `size/batchCount/usedFallback`。

- [ ] **Step 3: 改写 ImageGenerationTool，改用同步 kernel**

```java
public Object execute(Object input) {
    try {
        Map<String, Object> params = castParams(input);
        ImageGenerationExecuteCommand command = ImageGenerationExecuteCommand.builder()
                .requestId(agentContext.getSessionId())
                .prompt(requirePrompt(params))
                .mode(resolveMode(params))
                .fileNames(resolveToolFileNames(params))
                .maskFileNames(toStringList(params.get("maskFileNames")))
                .fileName(resolveOutputFileName(params.get("fileName")))
                .fileDescription(resolveOutputDescription(params.get("fileDescription"), prompt))
                .model(StringUtils.trimToNull(valueAsString(params.get("model"))))
                .size(StringUtils.trimToNull(valueAsString(params.get("size"))))
                .n(resolveInteger(params.get("n"), 1))
                .timeoutSeconds(300)
                .build();

        ImageGenerationExecutionResult result = imageGenerationExecutionKernel.execute(command);
        ToolArtifactSource artifactSource = agentContext.requireCurrentToolArtifactSource(getName());
        appendGeneratedArtifacts(result, artifactSource);
        emitFileMessage(result);
        return buildSuccessPayload(result);
    } catch (Exception e) {
        return buildFailurePayload("image_generation_tool 执行失败：" + e.getMessage());
    }
}
```

```java
private ToolResultPayload buildSuccessPayload(ImageGenerationExecutionResult result) {
    String summary = StringUtils.defaultIfBlank(result.getSummary(), "image_generation_tool 执行完成");
    return ToolResultPayload.structured(summary, summary, ImageGenerationToolOutput.builder()
            .prompt(result.getPrompt())
            .mode(result.getMode())
            .summary(summary)
            .size(result.getSize())
            .batchCount(result.getBatchCount())
            .sourceImageCount(result.getSourceImageCount())
            .maskImageCount(result.getMaskImageCount())
            .usedFallback(result.getUsedFallback())
            .fileRefs(result.getFiles().stream().map(this::toToolFileRef).toList())
            .build());
}
```

- [ ] **Step 4: 重新运行 tool 测试**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=ImageGenerationToolTest -DskipTests=false`

Expected: PASS

- [ ] **Step 5: 提交 tool 适配层收敛**

```bash
git add ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/ImageGenerationTool.java
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/ImageGenerationToolTest.java
git commit -m "refactor: route image generation tool through shared kernel"
```

## Task 5: 删除遗留 record 链路并完成回归验证

**Files:**
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentImageGenerationRecord.java`
- Delete: `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentImageGenerationRecordDao.java`
- Delete: `ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_image_generation_record_mapper.xml`
- Modify: `ai-agent-station-study-app/src/main/resources/db/schema.sql`
- Modify: `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/WorkspaceImageGenerationServiceTest.java`

- [ ] **Step 1: 搜索遗留 record 引用并把删除动作写成显式验收项**

Run: `rg -n "AgentImageGenerationRecord|IAgentImageGenerationRecordDao|ai_agent_image_generation_record" ai-agent-station-study-domain ai-agent-station-study-app ai-agent-station-study-trigger`

Expected: 仅命中计划中的待删文件与尚未重构完成的位置。

- [ ] **Step 2: 删除旧实体、DAO、Mapper 与表定义**

```sql
DROP TABLE IF EXISTS ai_agent_image_generation_record;
```

```text
Delete:
- ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentImageGenerationRecord.java
- ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentImageGenerationRecordDao.java
- ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_image_generation_record_mapper.xml
```

- [ ] **Step 3: 运行定向回归套件**

Run: `mvn test -pl ai-agent-station-study-app -Dtest=ImageGenerationExecutionKernelTest,WorkspaceImageGenerationServiceTest,AgentImageGenerationControllerTest,ImageGenerationToolTest -DskipTests=false`

Expected: PASS

- [ ] **Step 4: 做一次编译级验证，确认删除旧 DAO 后主工程仍可装配**

Run: `mvn -pl ai-agent-station-study-app -am -DskipTests compile`

Expected: BUILD SUCCESS

- [ ] **Step 5: 提交清理与最终验证**

```bash
git add ai-agent-station-study-app/src/main/resources/db/schema.sql
git add ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/WorkspaceImageGenerationServiceTest.java
git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/entity/AgentImageGenerationRecord.java
git rm ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/mapper/IAgentImageGenerationRecordDao.java
git rm ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_image_generation_record_mapper.xml
git commit -m "chore: remove legacy image generation record flow"
```

---

## 风险与收口

- `request_source` 是工作台历史正确性的硬要求；如果去掉它，历史页会混入普通对话里的 `image_generation_tool` 结果。
- `ai_agent_artifact.run_id` 改为可空后，唯一键必须切到 `request_id + tool_call_id + storage_key`，否则 workspace 写 artifact 会出现重复写入失效。
- `ImageGenerationTool` 改走同步 gateway 后，不再消费 Python SSE 中间片段；当前 Java 侧只展示最终 file 卡片，所以这不会引入产品回退。
- 工作台不能直接调用 `ToolOutputWriter.write(...)` 与 `AgentExecutionRecorder.recordArtifacts(...)` 这两个 fail-open 包装；必须走 strict 写入入口，否则即使补了 `@Transactional` 也无法回滚。
- 前端 `ui/src/services/imageGeneration.ts` 与 `ui/src/pages/WorkspaceImageGeneration/*` 不需要改代码；本次要保证接口返回 shape 与当前一致。

## 覆盖检查

- 执行内核抽离：Task 1
- 工作台复用通用 tool output / artifact 写入：Task 2 + Task 3
- `ai_agent_tool_output_image_generation` 合并批次字段：Task 2
- `file_size` 保持在 `ai_agent_artifact`：Task 2 + Task 3
- `image_generation_tool` 复用相同执行内核：Task 4
- 删除 `ai_agent_image_generation_record`：Task 5
