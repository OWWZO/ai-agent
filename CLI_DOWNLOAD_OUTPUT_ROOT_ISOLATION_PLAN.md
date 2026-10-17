# CLI 下载产物按调用隔离输出目录修复计划

Status: Implemented

## 1. 背景

`host_cli`、`browser`、`agent_browser` 已经接入统一的 CLI 文件产物物化链路，但当前 Browser / Agent Browser 的兼容发现逻辑仍然是：

1. 执行 CLI 前对 output root 做 snapshot；
2. 执行 CLI；
3. 扫描 output root 的新增文件；
4. 将新增文件登记并绑定到当前 `toolCallId`。

这在单次调用时可以工作，但同一个 workspace 中两个调用使用同一个 output root 时，两个调用都会看到对方写入的新文件，导致 artifact 交叉绑定。

本计划将产物捕获边界从“共享 workspace 中的新增文件”收敛为“当前 CLI 调用拥有的 output root”。Agent 在下载命令中显式声明 output 目录；Java 侧解析、校验、预留并只扫描该目录。

示例语义如下，文档和测试中使用占位 URL，不写入真实 signed URL：

```text
opencli xiaohongshu download "<signed-note-url>" \
  --output "./xiaohongshu-agent-post-images"
```

## 2. 设计决策

### 2.1 采用“Agent 指导 + Java 强制兜底”的混合方案

不能只依赖 Agent 是否记住了 skill 文档。最终边界必须由 Java 工具层保证：

- Agent / Code Mode skill 明确要求下载命令传入 `--output`；
- BrowserTool / AgentBrowserTool 发现已声明 output root 后，只允许扫描该目录；
- 未声明 output root 的已知下载命令，由 Java 侧自动注入一个本次调用唯一的 output root；
- 显式 output root 越界、含 `..` 或非法时，在启动 CLI 前失败，不执行不受控的 workspace 扫描；
- 不再允许 Browser / Agent Browser 使用整个 workspace 作为文件 diff fallback；
- `host_cli` 的旧兼容路径可以继续使用 workspace diff，但必须由独立 capture lock 保护，或要求命令提供 output root。

这样可以兼容旧 Agent 调用，同时保证新调用不会因为忘记写 `--output` 而回退到全 workspace 扫描。

### 2.2 显式 output root 的唯一性

Agent 应为每个下载任务使用不同的目录名，例如：

```text
./xiaohongshu-agent-post-images
./xiaohongshu-agent-post-images-2
./bilibili-agent-video-20261017
```

Java 侧仍然维护按绝对 output root 分组的 capture lease：

- 不同 output root 可以并发执行；
- 同一个 output root 的两个文件型调用只在该目录上串行捕获；
- 普通只读 Browser / Agent Browser 调用不获取文件 capture lease，继续并发；
- 不建立全局 Agent Browser 锁；
- 如果 output root 未声明，Java 侧自动创建本次调用唯一目录，因此不会产生同目录竞争。

### 2.3 扫描范围

最终允许登记的文件必须同时满足：

```text
file.realPath startsWith workspaceRoot.realPath
file.realPath startsWith captureRoot.realPath
```

`captureRoot` 是当前 CLI 调用的唯一文件捕获边界，不能只校验 workspaceRoot。

扫描规则：

- 只扫描当前调用的 capture root；
- 不扫描用户指定 output root 的兄弟目录；
- 不扫描整个 workspace；
- CLI 报告的 candidate 只是候选加速信息，不得扩大扫描边界；
- 即使 candidate JSON 解析失败，也可以安全扫描当前调用专属目录；
- 不把 workspace 外的绝对路径降级成 basename；
- 不将旧目录中原本存在的文件重复登记为本次新产物，除非命令明确报告且当前调用拥有该文件路径。

## 3. 术语和数据对象

建议新增一个只在 domain runtime 内使用的捕获计划对象，命名可按现有风格调整：

```text
CliArtifactCapturePlan
```

建议字段：

```text
originalArgs              Agent / Code Mode 原始 argv
rewrittenArgs             实际传给 OpenCLI 的 argv
isArtifactCommand         是否识别为下载/文件产生命令
requestedOutputRoot       Agent 声明的原始 output root，可为空
captureRoot               本次调用最终使用的 workspace 相对目录
captureRootAbsolute       仅内部使用的绝对目录
outputRootInjected        是否由 Java 侧注入
captureId                 本次调用唯一 ID
```

该对象不能把绝对路径放入工具结果、LLM observation、file event 或 Ledger metadata。

建议新增一个资源 lease 对象：

```text
CliArtifactCaptureLease implements AutoCloseable
```

职责：

- 按归一化后的 capture root 获取目录级锁；
- 记录当前 owner 的 captureId，用于日志排查；
- `close()` 释放锁并清理无引用的锁表项；
- 不能锁住所有 Browser / Agent Browser 调用；
- 不能把锁持有逻辑泄漏到 `CliArtifactMaterializer` 的文件登记细节中。

## 4. 命令识别规则

### 4.1 只为文件产生命令注入 output

不能对所有 Browser argv 无条件追加 `--output`，否则会破坏 `browser state`、`browser click`、`browser list`、`browser eval` 等命令。

建议在 `OpenCliArgv` 中新增：

```text
isDownloadCommand(args)
resolveOutputOption(args)
rewriteOutputRoot(args, captureRoot)
```

第一版识别规则：

- 站点 adapter 形态：`<site> download ...`；
- 允许的 site 包括现有下载 adapter 覆盖的 `xiaohongshu`、`rednote`、`twitter`、`bilibili`、`1688`；
- 后续新增下载 adapter 时必须显式登记到命令分类表或统一 manifest；
- `browser ...` 顶层浏览器原语不属于文件下载命令；
- `host_cli` 不使用 Browser 下载命令识别器，保留自己的 allowlist 和 capture 策略；
- 无法确认是否产物命令时，不注入参数，也不做全 workspace 扫描。

分类器必须对 prefix args 透明。实际 process args 可能是：

```text
node main.js xiaohongshu download <url> --output <dir>
```

分类时应定位 OpenCLI 主命令之后的 site / command，而不是假设 argv[0] 永远是 site。

### 4.2 output 参数解析

沿用现有选项集合，并统一处理以下形式：

```text
--output <dir>
--output=<dir>
--output-dir <dir>
--output-dir=<dir>
--out <dir>
--dir <dir>
```

解析要求：

- 缺少值、空值、下一个参数仍是 flag 时返回结构化 usage error；
- Windows 盘符绝对路径、UNC 路径、Unix `/tmp/x` 均视为绝对路径；
- `../escape`、`foo/../../escape`、反斜杠变体均拒绝；
- `./foo`、`foo/bar` 归一化为 workspace 相对路径；
- 不接受空字符串作为“扫整个 workspace”的隐式语义。

## 5. capture root 生成和重写

### 5.1 Agent 已声明 output root

当 Agent 已经传入合法 output root 时：

1. 取得 workspace 相对路径；
2. 归一化为 POSIX 分隔符；
3. 生成绝对 capture root；
4. 为该 root 获取目录级 lease；
5. 保留原目录语义，向 OpenCLI 传入归一化后的相对路径；
6. CLI 完成后只扫描这个 root。

如果该目录已经有旧文件：

- snapshot 必须在 lease 内完成；
- 只登记本次执行后新增或明确报告的文件；
- 不得把旧文件全部重新登记；
- 相同 root 的调用不能同时进入“snapshot → exec → scan”区间。

### 5.2 Agent 未声明 output root

对于已知下载命令，Java 侧自动生成：

```text
.reactor-artifacts/<tool-name>/<capture-id>
```

要求：

- `capture-id` 使用安全目录片段，不能含 `/`、`\\`、`..`；
- 推荐由 toolCallId 的安全短 hash 加随机 attempt suffix 构成；
- 不把 sessionId、visitorId、Cookie、CDP URL 或 signed URL 放进目录名；
- 将生成后的相对路径注入 `--output`；
- `captureRoot` 为空或非法时不得执行 CLI；
- 结果中的 `relativePath` 可以包含该目录，但不能返回绝对路径。

### 5.3 重写现有 output 参数

建议不简单追加第二个 `--output`，因为不同 CLI 对重复 option 的处理不一致。

实现方式：

- `--output <old>` 替换为 `--output <captureRoot>`；
- `--output=<old>` 替换为 `--output=<captureRoot>`；
- 同理处理 `--output-dir`、`--out`、`--dir`；
- 保留其他 argv 的顺序和内容；
- 原始 args 只用于 Ledger / 调试摘要，实际执行 args 使用 `rewrittenArgs`；
- 不把 capture root 注入 `stdout`、tool result 的原始命令文本或 LLM observation。

## 6. BrowserTool 接入

修改范围：

```text
Reactor-agent-domain/.../runtime/tool/browser/BrowserTool.java
Reactor-agent-domain/.../runtime/tool/cli/OpenCliArgv.java
Reactor-agent-domain/.../runtime/tool/cli/CliArtifactCapturePlan.java
Reactor-agent-domain/.../runtime/tool/cli/CliArtifactCaptureCoordinator.java
```

执行顺序：

1. 校验 relay online、用户身份和 OpenCLI 配置；
2. 调用 capture planner 识别是否为下载命令；
3. 解析或生成 capture root；
4. 获取目录级 lease；
5. 在 lease 内建立 output root snapshot；
6. 使用重写后的 argv 执行 CLI；
7. 无论 CLI 成功、非零退出、超时，均在同一个 lease 内调用物化模块；
8. 物化模块只扫描 capture root；
9. 完成后释放 lease；
10. 将安全 artifact 结果返回到 fields / Code Mode result。

异常要求：

- `cli.exec` 抛 RuntimeException 时，如果 capture root 已创建，仍尝试扫描并登记已经落盘的部分文件；
- 物化失败不能掩盖原始 CLI 失败原因；
- 使用结构化 `artifact_capture_*` 错误码；
- lease 必须在 `finally` 中释放；
- 普通 `browser state/click/eval` 不创建 lease，不改变现有并发行为。

## 7. AgentBrowserTool 接入

修改范围：

```text
Reactor-agent-domain/.../runtime/tool/browser/AgentBrowserTool.java
```

要求与 BrowserTool 一致：

- 使用相同的 capture planner / coordinator / materializer；
- 保留 `resolveForUser`、`OPENCLI_CDP_ENDPOINT`、`KERNEL_API_KEY` 清除和 CDP URL 脱敏；
- capture root 不得进入 CDP URL 脱敏逻辑；
- 同一 owner 的普通只读调用仍可并发；
- 只有使用相同 capture root 的产物调用竞争同一目录时才等待该目录 lease；
- 两个不同 output root 的下载调用必须可以同时进入 CLI；
- 失败、超时和异常执行仍尝试登记 capture root 内已生成文件。

## 8. CliArtifactMaterializer 改造

现有 `CliArtifactMaterializeCommand` 需要增加内部 capture boundary：

```text
captureRoot
captureRootAbsolute 或由 workspaceRoot + captureRoot 统一解析
```

收集候选的新规则：

1. 优先读取 `CliResult.artifacts`；
2. 对 candidate 做“相对路径 + capture root 内”校验；
3. 扫描 capture root 内的新增普通文件，补齐 candidate 缺失项；
4. 合并并按真实归一化路径去重；
5. 绝不扫描 workspace root；
6. 绝不把 workspace 外路径转成 basename；
7. 符号链接必须通过 `toRealPath()` 校验，不能逃出 capture root；
8. 继续执行文件数量、单文件大小和总大小限制；
9. 单文件失败继续处理其他文件；
10. 成功项进入 registry，失败项只进入 `artifact_failures`。

建议增加错误分类：

```text
artifact_output_root_missing
artifact_output_root_invalid
artifact_output_root_outside_workspace
artifact_capture_root_conflict
artifact_candidate_outside_capture_root
artifact_capture_scan_failed
```

`outside_workspace` 仍然保留，用于 workspace 层越界；capture root 越界应有更具体的错误原因，便于区分 Agent 传错目录和 CLI 报告错误。

## 9. OpenCLI candidate 协议同步修复

即使 capture root 扫描成为可靠主路径，也要保留 candidate 协议，因为它能减少扫描、提供 MIME/size hint，并支持未来非本地执行器。

### 9.1 Java parser

修改：

```text
Reactor-agent-infrastructure/.../CliArtifactCandidateParser.java
```

必须支持：

- 单个 object：`{"artifacts":[...]}`；
- `data.artifacts`；
- JSON array 中的 artifact envelope；
- 业务 rows 中任意一行包含 `artifacts`；
- 前导日志后的一份 JSON payload；
- 相对路径校验；
- Unix、Windows、UNC 绝对路径拒绝；
- `..` path segment 拒绝，而不是把合法文件名误判为越界；
- 候选数量上限。

解析失败不能阻断 CLI 主结果，但 capture root 扫描仍必须继续。

### 9.2 OpenCLI adapter 返回形状

修改下载 adapter 时必须保持两种输出语义：

- JSON 模式返回完整 payload，包含 `files` 和 `artifacts`；
- table 模式仍渲染 `files` 行，不出现 artifact 空行；
- Bilibili 不应把 artifact envelope 当成业务 table row；
- Twitter 不得在映射业务 rows 时丢弃 `artifacts`；
- `renderTable` 或 adapter output wrapper 必须有明确的 payload unwrap 规则，并有测试。

相关文件：

```text
adapter-host/src/download/media-download.ts
adapter-host/src/output.ts
adapter-host/clis/xiaohongshu/download.js
adapter-host/clis/rednote/download.js
adapter-host/clis/twitter/download.js
adapter-host/clis/bilibili/download.js
adapter-host/clis/1688/download.js
```

## 10. Skill 和 Agent 指令

修改：

```text
runtime/skills/opencli-browser/SKILL.md
```

新增明确规则：

- 所有下载型 adapter 调用必须显式传 `--output`；
- output root 必须是当前任务专属目录；
- 并行下载必须使用不同目录；
- 不使用用户已有的共享下载目录作为并行任务 output；
- 使用 `<signed-note-url>` 等占位符写示例，不把真实 token 写进 skill；
- Code Mode 示例也必须传入 output；
- 下载完成后从返回的 artifact/file event 获取文件 URL，不读取服务器绝对路径。

示例：

```javascript
const output = "./xiaohongshu-agent-post-images";
return await browser.command({
  args: ["xiaohongshu", "download", "<signed-note-url>", "--output", output],
});
```

同时更新 BrowserTool / AgentBrowserTool 的参数描述，让模型在 schema 层也知道下载命令需要 output root。

## 11. 目录生命周期和清理

共享 workspace 的 register 模式要求文件登记后仍然存在，因此不能在登记后立即删除成功文件。

规则：

- 成功登记的文件保留到 workspace/file service 的既有 retention 机制处理；
- 空 capture root 或完全失败且未生成 artifact 的目录可以清理；
- 部分成功时保留所有已登记文件，失败文件是否清理由 workspace retention 统一处理；
- upload 模式在文件服务确认上传成功后可以清理本地副本，但本计划不改变现有 retention 语义；
- 目录名中的 captureId 不得含敏感信息；
- `workspace_list` 是否隐藏 `.reactor-artifacts` 属于单独产品决策，本修复不改变 workspace list 协议。

## 12. 测试计划

### 12.1 OpenCliArgv / capture planner

- 识别 `xiaohongshu download`、`rednote download`、`twitter download`、`bilibili download`、`1688 download`；
- 不识别 `browser state`、`browser click`、`browser eval`；
- 缺少 output 时注入唯一目录；
- `--output dir` 替换成功；
- `--output=dir` 替换成功；
- `--output-dir`、`--out`、`--dir` 替换成功；
- 同一 toolCallId 的两次 attempt 目录仍不冲突；
- `../escape`、Unix 绝对路径、Windows 盘符、UNC 路径被拒绝；
- 生成目录只包含安全字符；
- 原始 argv 不被就地修改。

### 12.2 Capture coordinator

- 两个不同 output root 可以并发获取 lease；
- 同一个 output root 只能有一个 capture 区间；
- 普通 Browser / Agent Browser 调用不获取 capture lease；
- lease 在 CLI 返回、异常、超时、物化异常时都释放；
- 失败后不会残留永久锁表项。

### 12.3 BrowserTool

- 未显式 output 时 process args 被注入唯一 output；
- 显式 output 时 process args 使用规范化目录；
- fake CLI 将文件写入 capture root 后能被登记；
- fake CLI 将文件写入 workspace 其他目录时不被登记；
- 两个并发调用使用同一个原始 output root 时，最终文件不会交叉绑定；
- 两个并发调用使用不同 output root 时，两次 CLI 可以同时运行；
- CLI 非零退出、超时、抛异常但产生部分文件时，成功文件仍登记；
- relay 环境变量不变；
- Code Mode 返回 `artifacts[].downloadUrl`。

### 12.4 AgentBrowserTool

- 复用 BrowserTool 的 output/capture 逻辑；
- CDP URL 不进入 capture 计划、artifact metadata、file event、tool result、Ledger observation；
- 同 owner 普通只读调用仍并发；
- 同 output root 的下载调用只锁该目录；
- 不同 output root 的下载调用并发；
- session reconstructed notice 保留。

### 12.5 Materializer / security

- capture root 内嵌套文件成功登记；
- capture root 外但 workspace 内文件被忽略；
- workspace 外绝对路径被拒绝；
- capture root 外符号链接被拒绝；
- candidate 报告绝对路径、Windows 路径、UNC 路径时不登记；
- candidate 报告 `../escape` 时不登记；
- output 目录在 workspace 外时不执行或返回结构化错误；
- 单文件失败不影响其他文件；
- 数量、单文件大小、总大小限制仍生效；
- sessionId 为空时使用 requestId 作为文件服务 requestId。

### 12.6 OpenCLI adapter / output

- candidate parser 可以解析 object payload；
- candidate parser 可以解析 array payload；
- Bilibili artifact envelope 不产生空 table row；
- Twitter 不丢失 artifacts；
- XHS / Rednote / 1688 JSON 模式保留 artifacts；
- table 模式仍显示原有下载结果 rows；
- `--output` 显式目录下生成的候选路径与 Java capture root 一致。

### 12.7 Ledger / API / frontend

- 成功文件绑定正确 `toolCallId`；
- `ToolExecutionPipeline.recordToolArtifacts` 写入 output artifact；
- 历史文件接口返回 `downloadUrl` / `previewUrl`；
- file event 可被前端解析为可下载文件；
- 前端不显示绝对路径；
- 非共享 upload 模式仍可下载真实文件。

## 13. 推荐提交顺序

### Commit 1：Capture planner 和目录 lease

- 新增 capture plan / coordinator；
- 扩展 OpenCliArgv；
- 添加路径、唯一性和 lease 单元测试；
- 不改变现有 CLI 产物登记结果。

### Commit 2：Browser / Agent Browser output root 接入

- 两条工具路径统一使用 capture plan；
- 注入或替换 output 参数；
- 只扫描 capture root；
- 加入异常、超时、同目录并发测试。

### Commit 3：Materializer boundary hardening

- 增加 capture root 校验；
- 删除 basename fallback；
- 修复绝对路径、符号链接、requestId fallback；
- 更新公共物化测试。

### Commit 4：OpenCLI candidate / output shape 修复

- parser 支持 object / array；
- Twitter / Bilibili 保留 artifacts；
- table 输出保持兼容；
- 加 adapter 与 renderer 测试。

### Commit 5：Skill、文档和端到端验收

- 更新 `opencli-browser` skill；
- 更新 README / 计划文档中的调用示例；
- 补 Ledger、history、file endpoint、frontend 和 upload 验收；
- 只有全部验收通过后才把主计划状态改为 `Implemented`。

## 14. 完成标准

只有以下条件全部满足，才算本修复完成：

1. 已知下载命令总是使用显式或 Java 注入的 output root；
2. Browser / Agent Browser 不再扫描整个 workspace；
3. 同目录并发调用不会交叉绑定；
4. 不同目录的下载调用仍可并发；
5. CLI 失败、超时、异常产生的部分文件仍可登记；
6. output root 越界、绝对路径、符号链接逃逸均被拒绝；
7. candidate 解析失败不会导致安全扫描失效；
8. OpenCLI JSON 和 table 两种输出都保持正确；
9. `host_cli` 兼容 fallback 要么有 lock，要么只允许无并发使用；
10. 正确的 artifact 绑定、Ledger 记录、历史 URL 和前端下载全部有测试；
11. 非共享 multipart 上传有真实 endpoint 测试，不只测试模式分派；
12. 原有 Browser Relay、Agent Browser session、敏感信息脱敏和只读并发测试全部通过。
