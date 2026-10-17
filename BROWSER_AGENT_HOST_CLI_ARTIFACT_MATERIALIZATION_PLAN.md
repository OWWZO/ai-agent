# Browser / Agent Browser / Host CLI 文件产物统一物化计划

Status: Implemented（Phase 0-6 已落地，Phase 5 的 OpenCLI 侧候选输出已落地并回填协议）

## 1. 目标

让以下三个工具产生的文件使用同一条产物交付链路：

- `host_cli`
- `browser`
- `agent_browser`

统一后的行为：

1. CLI 在 Agent workspace 中产生文件。
2. Java 侧发现并校验文件。
3. 文件被登记到文件服务，得到 `downloadUrl` 和 `previewUrl`。
4. 文件绑定到当前工具调用的 `ToolArtifactSource`。
5. `ToolArtifactRegistry` 保存工具调用与文件之间的关系。
6. `ToolExecutionPipeline` 将绑定写入 Execution Ledger 的 `ai_agent_artifact`。
7. 实时 `file` 事件和历史 artifact 接口都返回同一份文件元数据。
8. 前端沿用现有文件卡片和 `downloadUrl` 下载文件。

最终调用方不需要知道文件是在宿主机、Browser Relay、Agent Browser CDP 还是其他 OpenCLI 会话中产生的。

## 2. 非目标

本变更不做以下事情：

- 不把图片、视频或其他二进制内容编码进 LLM observation。
- 不在 `BrowserTool` 或 `AgentBrowserTool` 中新增浏览器专用下载接口。
- 不新增第二套 artifact 表或 transcript 文件表。
- 不把服务器绝对路径返回给 Agent 或前端。
- 不改变 Browser Relay、Direct CDP、Agent Browser 的 target 路由语义。
- 不把所有 workspace 文件自动暴露给用户。
- 不为 `host_cli` 放宽现有 CLI allowlist、代理或写操作策略。

## 3. 当前代码事实

### 3.1 三个工具的共同执行模型

三条路径最终都通过 `CliExecutionPort.exec(CliInvocation)` 启动 OpenCLI 子进程，并把 `cwd` 设置为 `AgentContext.getWorkspaceRoot()`：

- `HostCliTool`：本机宿主 CLI，见 `Reactor-agent-domain/.../runtime/tool/cli/HostCliTool.java`。
- `BrowserTool`：通过 `OPENCLI_RELAY_URL` 连接用户 Browser Relay，见 `Reactor-agent-domain/.../runtime/tool/browser/BrowserTool.java`。
- `AgentBrowserTool`：通过 `OPENCLI_CDP_ENDPOINT` 连接 Agent Browser，见 `Reactor-agent-domain/.../runtime/tool/browser/AgentBrowserTool.java`。

浏览器连接方式不同，但文件写入位置都是 CLI 子进程所在的 workspace，而不是浏览器 tab 的下载目录。

### 3.2 已有的局部实现

`HostCliTool` 已经在执行后：

- 对 workspace 做前后快照；
- 发现新文件；
- 调用 `FileArtifactPort.register`；
- 调用 `AgentContext.registerGeneratedArtifact`；
- 发送 `file` 事件；
- 在工具结果中返回 `artifacts`。

这段逻辑位于 `HostCliTool.registerNewFiles`，但目前是 Host CLI 私有实现。

### 3.3 已有的公共产物链路

以下模块已经是当前产物主链路，应直接复用：

- `FileArtifactPort`：文件登记、上传、查询和读取。
- `WorkspaceFileRegistration`：workspace 文件路径规范化、文件服务登记、产物绑定和 `file` 事件。
- `ToolArtifactSource`：当前工具调用来源快照。
- `ToolArtifactRegistry`：工具调用和文件产物绑定关系。
- `AgentContext.registerGeneratedArtifact`：写入运行期 registry。
- `ToolExecutionPipeline.recordToolArtifacts`：将 registry 绑定转换为 `ArtifactRecordCommand`。
- `AgentConversationHistoryController`：返回历史 artifact 的 `downloadUrl`、`previewUrl` 和元数据。
- UI 的 `artifactRefs`、`fileInfo`、`downloadUrl` 展示和下载逻辑。

### 3.4 当前缺口

- `BrowserTool` 忽略 CLI 产生的文件。
- `AgentBrowserTool` 忽略 CLI 产生的文件。
- 两个浏览器工具只处理 stdout、viewport 和 CDP URL 脱敏。
- `HostCliTool` 的文件登记代码没有复用 `WorkspaceFileRegistration`，存在重复逻辑。
- `CliResult.artifacts` 已存在，但三条工具路径没有统一消费它。
- `FileArtifactPort.register` 依赖文件服务和 Java 后端共享文件路径；非共享部署没有二进制上传 fallback。

## 4. 目标架构

### 4.1 统一的深模块

新增一个共享的深模块，建议命名为：

```text
CliArtifactMaterializer
```

建议位置：

```text
Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/cli/
```

该模块对三个工具暴露小接口，隐藏以下复杂性：

- workspace 路径归一化；
- 文件是否位于 workspace 内的校验；
- 新文件发现和 CLI 报告文件合并；
- 文件大小和数量限制；
- MIME 推断；
- `FileArtifactPort.register` / 二进制上传选择；
- `File` DTO 构造；
- `AgentContext.registerGeneratedArtifact`；
- `file` 事件发送；
- 对外安全元数据生成。

调用方只需要提供：

- 当前 `AgentContext`；
- 本次 CLI 的 workspace 根目录；
- 本次 CLI 报告或发现的候选文件；
- 工具事件名称。

调用方得到：

- 已成功物化的文件列表；
- 失败文件列表和失败原因；
- 可直接放进工具结果的安全 artifact 视图。

### 4.2 建议的数据对象

保留现有 `CliArtifact` 作为 CLI 候选文件的内部对象，不把绝对路径直接作为用户输出。

新增一个面向工具结果的对象，建议命名为 `MaterializedCliArtifact`：

```text
relativePath   workspace 内相对路径
fileName       展示文件名
fileSize       文件大小
mimeType       可选 MIME 类型
previewUrl     文件服务预览地址，可为空
downloadUrl    文件服务下载地址，必须在登记成功时存在
```

规则：

- 不包含 `absolutePath`。
- `relativePath` 必须经过 workspace 根目录校验和 `..` 拒绝。
- `downloadUrl` 来自 `FileResponse.ossUrl`。
- `previewUrl` 来自 `FileResponse.domainUrl`。
- `isInternalFile` 固定为 `false`，因为这些是用户请求产生的可见下载产物。

### 4.3 产物来源优先级

`CliArtifactMaterializer` 按以下优先级收集候选文件：

1. CLI 执行结果中明确报告的 `CliResult.artifacts`。
2. 当前命令声明的 output root 下的文件。
3. 兼容模式下，workspace 前后快照的新增文件。

前后快照只能作为兼容 fallback，不能成为长期唯一协议。原因是并发工具调用可能同时写入同一个 workspace，容易把一个调用的文件错误绑定到另一个调用。

### 4.4 产物绑定与 Ledger

登记成功后必须按下面顺序执行：

1. 使用当前 `AgentContext.getCurrentToolArtifactSource()`。
2. 调用 `agentContext.registerGeneratedArtifact(source, file)`。
3. 发送现有 `file` 事件。
4. 将安全的 `MaterializedCliArtifact` 放入当前工具的 `llmData` / `fields`。
5. 由现有 `ToolExecutionPipeline.recordToolArtifacts` 写入 Ledger。

不要在新模块中直接写 `ArtifactRecordCommand`。Ledger 记录已经有统一入口。

## 5. 文件存储模式

### 5.1 共享 workspace 模式

当前 Docker 部署中：

- Java 后端挂载 `reactor-data`；
- `reactor-tool` 也挂载 `reactor-data`；
- workspace 和文件服务路径可以共享。

此模式使用现有 `FileArtifactPort.register`：

```text
Java workspace file
  -> /v1/file_tool/register_file
  -> file service records local_path
  -> returns preview/download URLs
```

必须验证：

- Java 后端发送的 `localPath` 在 `reactor-tool` 中可见；
- 两边的挂载路径完全一致；
- 文件服务读取 URL 时文件仍然存在。

### 5.2 非共享 workspace 模式

如果 Java 后端和文件服务不在同一台机器或不共享目录，`register_file` 不够，因为它只登记路径，不复制文件内容。

需要补充 `FileArtifactPort` 的二进制上传能力，建议增加一个明确的方法：

```text
uploadLocalFile(serviceBaseUrl, localPath, fileName, metadata)
```

基础设施适配器调用已有的：

```text
POST /v1/file_tool/upload_file_data
```

实现要求：

- 使用流式 multipart，不把整个图片转换成 Base64 放入 JSON。
- 保留超时和最大文件大小限制。
- 返回与 `register` 相同的 `FileResponse`。
- 由部署能力决定使用 register 还是 upload，不能让工具层自己判断网络拓扑。

建议把模式选择封装在 `FileArtifactPort` 的基础设施适配器内部，工具层只调用统一的“物化本地文件”接口。

如果新增配置或修改 Docker / 文件服务路径，必须同步部署文档、Docker 配置和配置说明，并使用 `reactor-deployment-config-sync` skill 检查部署一致性。

## 6. 分阶段任务

### Phase 0：锁定现状行为

目标：先建立公共测试 seam，避免重构时改变三条工具的原有执行行为。

任务：

1. 为 `HostCliTool` 增加文件产物测试 fixture：
   - fake CLI 在临时 workspace 创建一个文件；
   - fake `FileArtifactPort.register` 返回 `FileResponse`；
   - 断言返回 `artifacts`；
   - 断言 `AgentContext` 有 visible binding；
   - 断言发送了 `file` 事件。
2. 为 `BrowserTool` 增加等价 fixture：
   - relay online；
   - fake CLI 创建一个文件；
   - 断言 relay 环境变量仍然正确；
   - 断言文件产物登记成功。
3. 为 `AgentBrowserTool` 增加等价 fixture：
   - fake `AgentBrowserSession` 提供 CDP URL；
   - 断言 CDP URL 仍然只存在于子进程环境；
   - 断言工具 payload 和 artifact registry 不泄漏 CDP URL。
4. 增加失败场景：
   - CLI exit code 非零但产生了部分文件；
   - 一个文件登记成功、另一个文件登记失败；
   - workspace 外路径被忽略。

验收：现有行为测试通过，新增测试在未抽取公共模块前先能明确表达目标行为。

### Phase 1：抽取共享文件物化模块

任务：

1. 新建 `CliArtifactMaterializer`。
2. 新建 `MaterializedCliArtifact` 结果对象。
3. 将 `HostCliTool.registerNewFiles` 中的以下逻辑迁移进去：
   - 文件枚举；
   - workspace 路径校验；
   - `FileRequest` 构造；
   - `FileArtifactPort.register` 调用；
   - `File` DTO 构造；
   - `AgentContext.registerGeneratedArtifact`；
   - `file` 事件构造；
   - `CliArtifact` / 外部 artifact 结果构造。
4. 将 `WorkspaceFileRegistration.registerLocalFile` 从 `void` 改为返回结构化登记结果，保留现有调用方兼容性。
5. 让公共模块使用 `WorkspaceFileRegistration`，不要复制路径清洗逻辑。
6. 增加统一错误分类：
   - `outside_workspace`；
   - `file_not_found`；
   - `file_too_large`；
   - `artifact_register_failed`；
   - `artifact_upload_failed`。
7. 确保单个文件失败不会阻止其他文件继续登记。

验收：

- `HostCliTool` 原有行为不变；
- 公共模块可用 fake `FileArtifactPort` 单独测试；
- 不返回绝对路径；
- 成功登记的文件自动进入 registry。

### Phase 2：迁移 HostCliTool

任务：

1. 删除 `HostCliTool` 内部重复的 `registerNewFiles` 实现。
2. 删除 HostCliTool 内部重复的 workspace snapshot 辅助方法。
3. 通过 `CliArtifactMaterializer` 生成 artifacts。
4. 保留 `host_cli` 的现有输出字段：
   - `ok`；
   - `tool`；
   - `exit_code`；
   - `stdout`；
   - `stderr`；
   - `duration_ms`；
   - `timed_out`；
   - `truncated`。
5. 将 `artifacts` 替换为安全的相对路径、文件大小、MIME 和 URL，不再返回绝对路径。
6. 保留现有 allowlist、社交 CLI 只读策略和代理环境行为。

验收：

- `HostCliToolTest` 全部通过；
- 新文件会登记并返回 URL；
- CLI 失败时仍登记已经产生的有效文件；
- workspace 外文件不会登记；
- 前端 file 事件字段与现有 workspace 文件事件一致。

### Phase 3：接入 BrowserTool

任务：

1. 在 `BrowserTool.executeCli` 前建立本次 CLI 的 artifact capture 状态。
2. CLI 执行完成后，无论成功、失败还是超时，都调用公共物化模块。
3. 保留 Browser Relay 逻辑不变：
   - `OPENCLI_RELAY_URL`；
   - `OPENCLI_RELAY_VISITOR_ID`；
   - `OPENCLI_RELAY_SECRET`；
   - `OPENCLI_CACHE_DIR`。
4. 不把 relay 地址、用户身份、Cookie 或浏览器 target 写入 artifact 元数据。
5. 将物化结果合并到 BrowserTool 的 `fields`。
6. 继续支持现有 viewport tree、screenshot artifact 和敏感 stdout 脱敏。
7. 确保 `browser.command` 的 Code Mode 返回值可以看到 `artifacts[].downloadUrl`。

重点验收用例：

```text
browser.command({
  args: ["xiaohongshu", "download", signedUrl, "--output", "./xhs"]
})
```

结果必须同时满足：

- 小红书页面仍通过 Browser Relay 执行；
- 图片写入 Java 进程的 workspace；
- 文件服务返回下载地址；
- 当前工具调用绑定 artifact；
- Code Mode 返回 artifact 元数据；
- 前端可以通过 downloadUrl 下载图片。

### Phase 4：接入 AgentBrowserTool

任务：

1. 在 `AgentBrowserTool.executeForUser` 使用与 BrowserTool 相同的物化入口。
2. 保留 Agent Browser 专属行为：
   - `AgentBrowserSessionPort.resolveForUser`；
   - `OPENCLI_CDP_ENDPOINT`；
   - `KERNEL_API_KEY` 清除；
   - CDP URL 脱敏；
   - session reconstructed notice。
3. 不让 artifact 的 `downloadUrl` 被 CDP URL 脱敏逻辑误删或错误替换。
4. 对敏感命令和普通命令都保留 artifact registry 绑定。
5. 保留现有同一 owner 的并发执行能力。

并发要求：

- 只读 Agent Browser 命令仍然可以并发。
- 文件产物不能因为 workspace 前后快照而跨工具调用串线。
- 优先使用 CLI 明确报告的 artifact candidate。
- 如果只能使用 workspace diff，必须使用文件产物 capture lock，不能让两个文件写入调用同时做同一 workspace 的 diff。
- 不能为了文件登记把所有 Agent Browser 调用全局串行化。

验收：

- 原有 `shouldRunKernelCallsForSameOwnerConcurrently` 继续通过；
- 新增两个并发下载调用测试，文件不能错误绑定到对方 toolCallId；
- CDP URL 不出现在 tool result、artifact metadata、file event 或 ledger observation 中。

### Phase 5：统一 CLI artifact candidate 协议

目标：减少 workspace diff 对并发和旧文件的依赖。

任务：

1. 明确 `CliResult.artifacts` 的语义：它表示本次 CLI 调用报告的候选文件，不表示已经登记完成。
2. 为 OpenCLI 下载型命令增加统一 artifact candidate 输出约定。
3. `downloadMedia` 返回相对 output root 的文件路径、文件大小和类型。
4. Java 侧解析并校验 candidate：
   - 相对路径必须位于 workspace；
   - 路径不能包含 `..`；
   - 文件必须在 CLI 结束时真实存在；
   - 失败项不能进入 registry。
5. 仍保留 workspace diff 作为旧命令兼容 fallback。
6. 不把绝对路径放入 OpenCLI JSON 输出。

建议的 OpenCLI candidate 字段：

```json
{
  "relativePath": "xiaohongshu-downloads/note-1/note-1_1.jpg",
  "size": 183421,
  "mime": "image/jpeg"
}
```

不要直接把 `downloadUrl` 交给 OpenCLI 适配器生成。下载 URL 应由 Reactor 文件服务登记后生成，避免两个系统各自维护 URL 规则。

### Phase 6：非共享文件系统上传能力

此阶段可以与前面阶段并行，但在非 Docker 部署前必须完成。

任务：

1. 扩展 `FileArtifactPort`，增加本地二进制文件上传方法。
2. 在 `ReactorToolFileArtifactAdapter` 中调用 `/v1/file_tool/upload_file_data`。
3. 使用流式 multipart 上传，不把文件读取为 Base64。
4. 增加：
   - 最大单文件大小；
   - 上传超时；
   - 文件名清洗；
   - MIME 推断；
   - 上传失败错误码。
5. 让 `CliArtifactMaterializer` 统一调用“本地文件物化”接口，由基础设施选择 register 或 upload。
6. 在 Docker 共享卷模式下保留 register 的零拷贝路径。
7. 更新部署文档和 `docker-compose.yml` 相关说明，并执行 deployment config sync 检查。

## 7. 并发和幂等规则

### 7.1 不允许使用全 workspace 无条件扫描作为长期协议

全 workspace snapshot 有三个风险：

- 并发调用可能互相看到文件；
- 旧文件可能被重复登记；
- workspace 中与当前工具无关的文件可能被暴露。

兼容 fallback 必须满足：

- 只扫描当前命令明确声明的 output root；或
- 由文件产物 capture lock 保护；或
- 只在没有并发运行的 Host CLI 路径使用。

### 7.2 重复下载

相同文件再次登记时：

- 文件服务可以按 `requestId + relativePath` 幂等返回相同 URL；
- `ToolArtifactRegistry` 按现有 binding 去重；
- 不重复发送相同 `file` 事件，除非当前 toolCallId 不同且需要新的 Ledger 关联。

### 7.3 部分成功

如果 5 张图片中 3 张成功、2 张失败：

- 3 张成功文件必须登记并可下载；
- 2 张失败文件不得创建 artifact；
- 工具结果保留成功数量和失败原因；
- 整个工具调用可以是 `ok=false`，但不能丢掉成功产物。

## 8. 安全要求

1. 所有候选文件必须 `normalize().startsWith(workspaceRoot)`。
2. 拒绝 `..`、绝对路径和 workspace 外符号链接逃逸。
3. 不返回服务器绝对路径。
4. artifact 元数据不能包含 Cookie、CDP URL、relay secret 或用户身份 token。
5. `downloadUrl` 和 `previewUrl` 只使用文件服务生成的地址。
6. 文件服务登记必须使用当前 session/request 作为 `requestId`，不能使用全局固定 ID。
7. 对单次工具调用限制文件数量和总字节数，防止任意 CLI 误把整个 workspace 作为产物。
8. 前端下载权限继续沿用现有 session ownership 和文件服务访问控制。

## 9. 测试矩阵

### 9.1 公共物化模块

- 登记一个图片文件，得到 `downloadUrl` 和 `previewUrl`。
- 登记一个嵌套相对路径文件。
- 拒绝 workspace 外绝对路径。
- 拒绝 `../escape.txt`。
- 文件不存在时返回结构化失败。
- 文件服务登记失败时不写 registry。
- 单文件失败不影响其他文件。
- 文件大小和数量限制。
- `FileArtifactPort.register` 模式。
- 二进制 upload 模式。

### 9.2 HostCliTool

- 新文件被登记。
- CLI 失败但产生的文件仍被登记。
- 绝对路径不出现在 payload。
- 原有 allowlist、代理和只读策略不变。
- 现有 `HostCliToolTest` 全部通过。

### 9.3 BrowserTool

- relay 环境变量不变。
- `xiaohongshu download` 产生的图片进入 artifact registry。
- Browser Relay 页面操作仍然成功。
- `file` 事件包含文件名、相对路径、preview URL、download URL。
- Code Mode 返回 `artifacts`。
- 敏感命令不泄漏 Cookie。
- 部分下载成功时保留成功文件。

### 9.4 AgentBrowserTool

- CDP 环境变量仍然正确。
- CDP URL 不出现在任何可见结果。
- Agent Browser 下载文件进入 artifact registry。
- 重建 session notice 保留。
- 同 owner 只读调用仍然并发。
- 并发产物调用不会交叉绑定。

### 9.5 Ledger 和 API

- `ToolExecutionPipeline.recordToolArtifacts` 生成 output artifact。
- `ai_agent_artifact.download_url` 和 `preview_url` 正确写入。
- `/api/agent/conversation/sessions/{sessionId}/files` 返回文件。
- 历史 replay 返回相同 artifact 引用。
- 前端 `downloadUrl` 可以下载实际文件。

### 9.6 文件服务和部署

- 共享卷 register 模式可下载。
- 非共享模式 upload 后可下载。
- 文件服务找不到原文件时返回明确 missing 状态。
- Docker compose 中后端和文件服务的 workspace 路径一致。

## 10. 文件修改范围建议

核心实现：

- `Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/cli/HostCliTool.java`
- `Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/browser/BrowserTool.java`
- `Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/browser/AgentBrowserTool.java`
- `Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/workspace/WorkspaceFileRegistration.java`
- 新增 `Reactor-agent-domain/.../runtime/tool/cli/CliArtifactMaterializer.java`
- 新增 `Reactor-agent-domain/.../runtime/tool/cli/MaterializedCliArtifact.java`

文件服务适配：

- `Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/adapter/port/FileArtifactPort.java`
- `Reactor-agent-infrastructure/src/main/java/org/wwz/ai/infrastructure/adapter/port/ReactorToolFileArtifactAdapter.java`
- 需要时修改 `reactor-tool/reactor_tool/api/file_manage.py`

测试：

- `Reactor-agent-app/src/test/java/org/wwz/ai/test/domain/HostCliToolTest.java`
- `Reactor-agent-app/src/test/java/org/wwz/ai/test/domain/BrowserToolTest.java`
- `Reactor-agent-app/src/test/java/org/wwz/ai/test/domain/AgentBrowserToolTest.java`
- 新增公共 `CliArtifactMaterializerTest`
- 新增 Ledger / file endpoint 集成测试

部署同步：

- `docker-compose.yml`
- `Reactor-agent-app/src/main/resources/application-dev.yml`
- `Reactor-agent-app/src/main/resources/application-prod.yml`
- 相关文件服务和部署文档

## 11. 推荐提交顺序

### Commit 1：公共产物模块和测试

- 新增 `CliArtifactMaterializer`。
- 扩展 `WorkspaceFileRegistration`。
- 添加公共单元测试。
- 不改变三个工具的调用路径。

### Commit 2：HostCliTool 迁移

- 删除 HostCliTool 重复登记逻辑。
- 接入公共模块。
- 修复绝对路径暴露问题。
- 更新 HostCliTool 测试。

### Commit 3：BrowserTool 和 AgentBrowserTool 接入

- 两个工具共享同一物化模块。
- 保留各自 relay / CDP 环境和脱敏逻辑。
- 增加下载产物和并发测试。

### Commit 4：OpenCLI artifact candidate 协议

- 为下载型 adapter 输出相对 artifact 路径。
- Java 侧优先消费 CLI 报告的 candidate。
- 保留 workspace diff fallback。

### Commit 5：非共享文件系统上传

- 扩展 `FileArtifactPort`。
- 接入 multipart upload。
- 更新部署配置和部署文档。

## 12. 完成标准

只有全部条件满足才算完成：

1. `host_cli`、`browser`、`agent_browser` 三个工具都能返回统一 artifact 结果。
2. 三条路径都能将文件绑定到正确的 `toolCallId`。
3. Execution Ledger 中存在对应 output artifact。
4. 历史文件接口返回有效 `downloadUrl`。
5. 前端可以下载文件，不需要读取服务器本地路径。
6. Browser Relay 和 Agent Browser 的敏感连接信息没有进入 artifact 或 observation。
7. 并发只读 Agent Browser 调用不回退为全局串行。
8. 并发文件调用不会互相登记对方产物。
9. 共享 workspace 和非共享 workspace 两种部署模式都有明确测试。
10. 现有 HostCli、Browser Relay、Agent Browser、Ledger 和前端测试全部通过。
