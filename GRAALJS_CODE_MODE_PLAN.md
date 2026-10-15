# GraalJS Code Mode 实现计划书

## 1. 文档目的

本文档是 Reactor-agent 中新增 GraalJS Code Mode 的实现计划，供其他实现 Agent 直接执行。

本计划已经明确以下范围：

- Code Mode 只执行 JavaScript。
- JavaScript 使用 GraalJS 在当前 Java JVM 内执行。
- `CodeModeContext` 只暴露 `browser`、`host`、`workspace` 和 `input`。
- `agentBrowser` 不注入 Code Mode。
- 现有 `AgentBrowserTool` 继续作为独立普通工具保留。
- 本期不实现 Java、Python、Kotlin、Groovy 或其他语言执行器。
- 本期不要求移除现有 Browser、Agent Browser、Host CLI 工具。
- 本期的 workspace 以当前会话工作区为主要实现目标。

## 2. 最终目标

Agent 可以调用一个 `code_mode` 工具，让生成的 JavaScript 在当前 JVM 内完成多步工具编排：

```text
Agent
  -> code_mode
  -> GraalJS Context
  -> CodeModeContext
       -> browser
       -> host
       -> workspace
```

需要支持以下场景：

1. 调用 Browser Tool 获取结构化数据。
2. 在 JavaScript 内进行过滤、映射、去重、合并和计算。
3. 读取一个或多个 workspace 文件到内存。
4. 将两个 CSV 合并后写入新的 CSV。
5. 读取 CSV，在内存中修改后覆盖原文件。
6. 将 JSON、Markdown、HTML、图片或其他二进制内容写回 workspace。
7. 直接执行 workspace 中已有的 JavaScript 文件。
8. 生成文件并登记为当前 Agent 的 artifact。
9. 将 Code Mode 和内部 capability 调用写入 Execution Ledger。

## 3. 当前仓库事实

当前仓库已经具备以下可复用能力：

- `BrowserTool` 通过 `CliExecutionPort` 调用 OpenCLI，并使用 Browser Relay 连接用户浏览器。
- `HostCliTool` 通过 `CliExecutionPort` 执行白名单 CLI，使用 argv，不经过 shell。
- `AgentBrowserTool` 使用单独的 Agent Browser session，本期不接入 Code Mode。
- `WorkspaceService` 负责 workspace 根目录和路径限制。
- `WorkspaceReadTool`、`WorkspaceWriteTool` 已经提供文本文件读取和写入。
- `FileArtifactPort` 和 `WorkspaceFileRegistration` 已经可以登记工作区产物。
- `ReactorRuntimeDependencies` 是 domain 侧的 typed runtime dependency bundle。
- `AgentToolCollectionFactory` 负责按当前 Agent 能力装配工具。
- `ToolExecutionPipeline` 负责普通工具调用、取消、超时和 Execution Ledger 记录。

相关文件：

```text
Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/browser/BrowserTool.java
Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/browser/AgentBrowserTool.java
Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/cli/HostCliTool.java
Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/workspace/WorkspaceService.java
Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/workspace/WorkspaceReadTool.java
Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/workspace/WorkspaceWriteTool.java
Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/factory/AgentToolCollectionFactory.java
Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/ReactorRuntimeDependencies.java
Reactor-agent-app/src/main/java/org/wwz/ai/config/reactor/ReactorRuntimeAutoConfiguration.java
```

## 4. 总体架构

```text
CodeModeTool
  -> CodeModeExecutorPort
  -> GraalJsCodeModeExecutor
  -> GraalJS Context
  -> Proxy-based bindings
       -> BrowserCodeCapability
       -> HostCodeCapability
       -> WorkspaceCodeCapability
```

职责划分：

| 模块 | 职责 |
|---|---|
| `CodeModeTool` | 接收 Agent 参数、校验 source/workspaceFile、返回工具结果 |
| `CodeModeExecutorPort` | domain 到执行引擎的稳定接口 |
| `GraalJsCodeModeExecutor` | 创建 Context、绑定 capability、执行 JS、转换结果 |
| `BrowserCodeCapability` | 复用 BrowserTool 的身份、Relay、OpenCLI 和结果处理 |
| `HostCodeCapability` | 复用 HostCliTool 的白名单、argv、cwd 和产物处理 |
| `WorkspaceCodeCapability` | 读写受控文件、CSV 解析和 artifact 登记 |
| `CodeModeInvocationRecorder` | 记录 Code Mode 内部能力调用 |
| `ReactorRuntimeAutoConfiguration` | 装配 GraalJS 执行器和 runtime port |

## 5. CodeModeContext 契约

最终上下文只包含：

```text
CodeModeContext
  ├── browser
  ├── host
  ├── workspace
  └── input
```

JavaScript 入口固定为：

```javascript
async function main({ browser, host, workspace, input }) {
  // Agent 编排逻辑
}
```

本期不支持以下能力：

```javascript
context.agentBrowser
require(...)
import ...
process
Java.type(...)
Packages
Polyglot.import(...)
```

不要把以下对象注入 GraalJS：

- `AgentContext`
- Spring `ApplicationContext`
- Spring Bean
- `CliExecutionPort`
- `ProcessBuilder`
- `java.nio.file.Path`
- `java.io.File`
- `HttpClient`
- 原始 CDP 对象
- 原始 `BaseTool`

## 6. Domain 契约

### 6.1 CodeModeExecutorPort

在 `Reactor-agent-domain` 新增：

```java
public interface CodeModeExecutorPort {

    CodeModeResult execute(
            CodeModeRequest request,
            AgentContext agentContext
    );
}
```

该接口不能依赖 GraalJS 类型。

### 6.2 CodeModeRequest

```java
public record CodeModeRequest(
        String source,
        String workspaceFile,
        Map<String, Object> inputs,
        long timeoutMs
) {
}
```

约束：

- `source` 和 `workspaceFile` 必须二选一。
- 两者同时存在时直接失败。
- 两者都为空时直接失败。
- `inputs` 必须是 JSON 对象。
- `timeoutMs` 由配置和工具参数共同限制。

### 6.3 CodeModeResult

至少包含：

```text
success
result
errorCode
errorMessage
logs
durationMs
producedFiles
```

错误码建议统一为：

```text
CODE_MODE_INVALID_ARGUMENT
CODE_MODE_INVALID_SOURCE
CODE_MODE_WORKSPACE_FILE_NOT_FOUND
CODE_MODE_WORKSPACE_FILE_DENIED
CODE_MODE_TIMEOUT
CODE_MODE_CANCELLED
CODE_MODE_OUTPUT_TOO_LARGE
CODE_MODE_TOOL_CALL_LIMIT
CODE_MODE_CAPABILITY_DENIED
CODE_MODE_CAPABILITY_UNAVAILABLE
CODE_MODE_FILE_CONFLICT
CODE_MODE_FILE_TOO_LARGE
CODE_MODE_MEMORY_LIMIT
CODE_MODE_RUNTIME_ERROR
```

## 7. CodeModeTool

新增：

```text
org.wwz.ai.domain.agent.runtime.tool.codemode.CodeModeTool
```

工具名称：

```text
code_mode
```

工具参数：

```json
{
  "source": "可选，JavaScript 源码",
  "workspaceFile": "可选，workspace 内的 JavaScript 文件",
  "inputs": {},
  "timeoutMs": 180000
}
```

`CodeModeTool` 负责：

1. 解析工具参数。
2. 校验 `source/workspaceFile` 互斥关系。
3. 校验源码或文件路径不能为空。
4. 校验 timeout 最大值。
5. 获取当前 `AgentContext`。
6. 获取 `CodeModeExecutorPort`。
7. 调用执行器。
8. 转换为 `ToolResultPayload`。
9. 让普通 `ToolExecutionPipeline` 记录顶层 `code_mode` 调用。

`CodeModeTool` 不应直接 import GraalJS 类。

## 8. GraalJS 执行器

### 8.1 实现位置

在 `Reactor-agent-infrastructure` 新增：

```text
src/main/java/org/wwz/ai/infrastructure/codemode/
  GraalJsCodeModeExecutor.java
  GraalJsContextFactory.java
  GraalJsBindingFactory.java
  GraalJsValueConverter.java
  CodeModeExecutionException.java
```

GraalJS 依赖不能加入 domain。

### 8.2 Maven 依赖

在 root dependency management 中统一 GraalVM 相关版本，在 infrastructure 模块引入 embedding 依赖。

要求：

- 兼容 Java 21。
- 固定版本。
- 不依赖 Node.js。
- 不在运行时下载依赖。
- 不混用不同版本的 polyglot、truffle 和 JS language 包。
- 不把 GraalJS 依赖传入 domain 的编译接口。

实现 Agent 需要先验证当前 Maven 依赖管理和 GraalVM artifact 名称，再修改 POM。

### 8.3 JavaScript 包装方式

本期不使用 ES Module，不支持 `export default`、`import` 和 `require`。

执行器将源码包装成固定入口：

```javascript
(async function () {
  "use strict";

  async function main({ browser, host, workspace, input }) {
    // Agent source
  }

  return await main(context);
})()
```

必须支持：

- 普通同步返回值。
- `async function main(...)`。
- Promise。
- `await browser.command(...)`。
- `await workspace.writeCsv(...)`。

如果脚本没有有效的 `main` 入口，返回 `CODE_MODE_INVALID_SOURCE`。

### 8.4 Context 权限

从最小权限开始创建 GraalJS Context：

```text
HostAccess.NONE
禁止 Java Class Lookup
禁止 Host Class Loading
禁止文件 IO
禁止环境变量访问
禁止创建进程
禁止创建线程
禁止 Native Access
禁止 Polyglot Access
```

不要使用：

```text
HostAccess.ALL
allowAllAccess(true)
```

推荐使用 `ProxyObject` 和 `ProxyExecutable` 暴露能力，而不是把普通 Java 对象直接放入 JS。

JS 参数只允许转换为：

```text
null
String
Boolean
Number
List
Map
```

返回值也必须转换为 JSON-safe 数据，拒绝以下结果：

- Java 对象。
- GraalJS `Value`。
- 函数。
- 循环引用对象。
- 未限制深度的嵌套对象。
- 超过输出上限的字符串或数组。

## 9. Browser Capability

### 9.1 JavaScript 接口

```javascript
const result = await browser.command({
  args: ["jd", "search", "手机"],
  timeoutMs: 120000
});
```

返回 JSON-safe 结构：

```json
{
  "ok": true,
  "data": {},
  "stdout": "...",
  "stderr": "",
  "exitCode": 0,
  "durationMs": 1000
}
```

### 9.2 实现要求

Browser Capability 复用现有 `BrowserTool` 的能力：

- 用户身份校验。
- Browser Relay 在线检查。
- OpenCLI 参数重写。
- Relay URL 注入。
- Visitor ID 注入。
- Secret 注入。
- Timeout。
- 最大输出限制。
- 敏感参数脱敏。
- Viewport 处理。
- Artifact 处理。

不要在 GraalJS 执行器内重新拼装 Browser CLI 环境。

建议新增：

```text
BrowserCodeCapability
```

它可以暂时委托已有 `BrowserTool`，也可以复用从 `BrowserTool` 抽取出的内部执行服务。

必须保证：

- 每次 Code Mode 执行使用独立的工具绑定。
- 不共享带可变 `AgentContext` 的工具实例。
- 当前用户身份不能被脚本修改。
- 脚本结束后释放 Browser 相关资源。
- `agentBrowser` 不出现在绑定对象中。

### 9.3 Agent Browser 明确排除

本期不做：

- `CodeModeContext.agentBrowser`。
- `AgentBrowserCodeCapability`。
- Agent Browser session 注入。
- Agent Browser CDP endpoint 暴露。

现有 `AgentBrowserTool` 可以继续由 `AgentToolCollectionFactory` 单独注册，不能因此删除现有能力。

## 10. Host Capability

### 10.1 JavaScript 接口

```javascript
const result = await host.exec({
  tool: "allowed-cli",
  args: ["arg1", "arg2"],
  timeoutMs: 120000
});
```

禁止：

```javascript
host.shell("...")
host.exec("任意 shell 字符串")
```

### 10.2 实现要求

复用 `HostCliTool` 的规则：

- CLI 必须存在于配置白名单。
- 只能使用 argv。
- 不经过 shell。
- cwd 固定为当前 session workspace。
- 复用超时。
- 复用最大输出限制。
- 复用社交 CLI 只读限制。
- 复用文件产物登记。
- 复用代理环境变量处理。

建议抽取内部共享逻辑：

```text
HostCliPolicy
HostCliExecutor
```

普通 `HostCliTool` 和 `HostCodeCapability` 共用这些逻辑，避免出现两套白名单实现。

## 11. Workspace Capability

### 11.1 JavaScript 接口

```javascript
workspace.readText({
  path: "input/config.json",
  maxChars: 12000
});
```

```javascript
workspace.readBytes({
  path: "input/image.png",
  maxBytes: 33554432
});
```

```javascript
workspace.readCsv({
  path: "input/products.csv",
  maxRows: 100000
});
```

```javascript
workspace.writeText({
  path: "outputs/result.json",
  content: "...",
  expectedVersion: "sha256:..."
});
```

```javascript
workspace.writeBytes({
  path: "outputs/result.bin",
  base64: "...",
  expectedVersion: "sha256:..."
});
```

```javascript
workspace.writeCsv({
  path: "outputs/result.csv",
  columns: ["name", "price"],
  rows: [
    { name: "A", price: 10 },
    { name: "B", price: 20 }
  ],
  expectedVersion: "sha256:..."
});
```

### 11.2 支持的文件类型

在权限允许且大小不超过限制的前提下，workspace 能力可以处理任意文件扩展名：

```text
.csv
.json
.md
.html
.js
.png
.jpg
.xlsx
.zip
.bin
```

文本文件使用 `readText/writeText`。

二进制文件使用 `readBytes/writeBytes`，在 JS 中以 Base64 表示。

CSV 使用 `readCsv/writeCsv`，由 Java 侧完成解析和序列化。

“可以写入任意扩展名”不代表“写入后自动执行”。写入的 `.js` 文件只有在外层通过 `workspaceFile` 明确执行时才会执行。

### 11.3 Workspace 复用

优先复用：

```text
WorkspaceService
WorkspacePathGuard
WorkspaceRuntimeOptions
WorkspaceFileRegistration
FileArtifactPort
```

实现 Agent 不要在 GraalJS 中直接调用 `java.nio.file.Files`。

### 11.4 路径安全

所有 workspace 操作必须：

- 限制在当前 session workspace。
- 拒绝 `../` 路径穿越。
- 拒绝其他用户 session 路径。
- 规范化并检查最终路径。
- 处理符号链接逃逸。
- 不接受任意宿主绝对路径。
- 不暴露宿主绝对路径给 JS。

默认脚本目录：

```text
scripts/
jobs/
```

默认输出目录：

```text
outputs/
artifacts/
```

如果需要允许 `skills/`，必须另行定义只读策略，不要默认开放。

### 11.5 CSV 实现

CSV 解析和写入必须由 Java 侧完成，不允许要求 Agent 手动使用 `split(",")`。

必须正确处理：

- 字段内逗号。
- 双引号转义。
- 字段内换行。
- UTF-8。
- UTF-8 BOM。
- 空值。
- 缺失列。
- 列顺序。
- 非标量字段。
- 最大行数。
- 最大列数。
- 最大文件大小。

`readCsv()` 建议返回：

```json
{
  "path": "input/products.csv",
  "columns": ["name", "price"],
  "rows": [
    { "name": "A", "price": "10" }
  ],
  "rowCount": 1,
  "version": "sha256:...",
  "encoding": "UTF-8",
  "delimiter": ","
}
```

### 11.6 CSV 合并示例

```javascript
async function main({ workspace }) {
  const first = await workspace.readCsv({
    path: "input/first.csv"
  });

  const second = await workspace.readCsv({
    path: "input/second.csv"
  });

  const columns = [
    ...new Set([
      ...first.columns,
      ...second.columns
    ])
  ];

  const rows = [
    ...first.rows,
    ...second.rows
  ].map(row => {
    const normalized = {};
    for (const column of columns) {
      normalized[column] = row[column] ?? "";
    }
    return normalized;
  });

  const output = await workspace.writeCsv({
    path: "outputs/merged.csv",
    columns,
    rows
  });

  return {
    rowCount: rows.length,
    output
  };
}
```

### 11.7 CSV 修改并覆盖

```javascript
async function main({ workspace }) {
  const source = await workspace.readCsv({
    path: "data/products.csv"
  });

  const rows = source.rows.map(row => ({
    ...row,
    price: Number(row.price || 0),
    status: "processed"
  }));

  return await workspace.writeCsv({
    path: "data/products.csv",
    columns: [
      ...source.columns,
      ...(source.columns.includes("status") ? [] : ["status"])
    ],
    rows,
    expectedVersion: source.version
  });
}
```

### 11.8 覆盖写和版本冲突

默认不允许无条件覆盖已经被其他执行修改的文件。

写入流程：

```text
readCsv()
  -> 返回 version/hash
  -> JS 在内存中修改 rows
  -> writeCsv(expectedVersion)
  -> 文件未变化：覆盖成功
  -> 文件已变化：返回 CODE_MODE_FILE_CONFLICT
```

如果业务明确需要强制覆盖，可以后续增加 `overwrite: true`，但不能把无条件覆盖作为默认行为。

### 11.9 原子写入

`writeText`、`writeBytes`、`writeCsv` 应采用：

```text
写入临时文件
  -> 关闭并刷新
  -> 校验大小和 hash
  -> 原子替换目标文件
  -> 登记 artifact
```

写入失败时必须保留旧文件。

## 12. Workspace JS 文件一键执行

`CodeModeTool` 支持：

```json
{
  "workspaceFile": "scripts/scrape.js",
  "inputs": {
    "keyword": "手机"
  },
  "timeoutMs": 180000
}
```

执行流程：

```text
校验 workspaceFile
  -> resolveAllowedPath()
  -> 检查文件存在和扩展名
  -> 读取 JS 源码
  -> 检查源码大小
  -> 计算 SHA-256
  -> 创建 GraalJS Context
  -> 执行 main(context)
  -> 返回结果和产物
```

要求：

- `workspaceFile` 只能是相对路径。
- 默认只允许 `scripts/` 和 `jobs/`。
- 文件必须是 `.js`。
- 文件必须实现 `main(context)`。
- 不支持隐式执行被读取的其他文件。
- 记录执行文件路径和源码 hash。
- 不把完整源码写入普通日志。
- 文件内容变化后 hash 必须变化。

本期“一键执行”定义为：

```text
调用 code_mode，并传入 workspaceFile
```

本期不新增独立前端按钮和 HTTP Endpoint。如果以后增加 UI 按钮，必须复用 `CodeModeExecutorPort`，不能重新实现执行链路。

## 13. 资源限制

建议新增配置：

```yaml
reactor:
  code-mode:
    enabled: false
    default-timeout-ms: 180000
    max-timeout-ms: 540000
    max-source-chars: 262144
    max-output-chars: 1048576
    max-tool-calls: 100
    max-total-memory-bytes: 134217728
    workspace:
      max-read-bytes: 33554432
      max-write-bytes: 33554432
      max-csv-rows: 100000
      max-csv-columns: 200
      allowed-script-prefixes:
        - scripts/
        - jobs/
      allowed-output-prefixes:
        - outputs/
        - artifacts/
```

必须支持：

- 单次脚本超时。
- 单次 Browser/Host 调用超时。
- Code Mode 最大能力调用次数。
- JS 输出大小限制。
- 源码大小限制。
- 单文件读取大小限制。
- 单文件写入大小限制。
- CSV 行数和列数限制。
- 当前执行总内存预算。
- Agent 取消传播。
- GraalJS Context 关闭。
- Browser/Host 资源清理。

超出限制时必须返回结构化错误，不能静默截断数据。

## 14. Runtime 装配

### 14.1 ReactorRuntimeDependencies

增加可选依赖：

```java
CodeModeExecutorPort codeModeExecutorPort;
```

提供：

```java
getOptionalCodeModeExecutorPort()
```

不要把以下类型放进 `ReactorRuntimeDependencies`：

- `org.graalvm.polyglot.Context`
- `org.graalvm.polyglot.Value`
- GraalJS Proxy 类型
- 任意具体引擎对象

### 14.2 ReactorRuntimeAutoConfiguration

修改：

```text
Reactor-agent-app/src/main/java/org/wwz/ai/config/reactor/ReactorRuntimeAutoConfiguration.java
```

负责装配：

- `CodeModeExecutorPort`。
- `CodeModeProperties`。
- GraalJS 执行器。
- capability 依赖。
- workspace 配置。

### 14.3 AgentToolCollectionFactory

修改：

```text
Reactor-agent-domain/src/main/java/org/wwz/ai/domain/agent/runtime/tool/factory/AgentToolCollectionFactory.java
```

增加：

```text
code_mode
```

注册条件：

- Code Mode 配置启用。
- `CodeModeExecutorPort` 存在。
- 当前 Agent 允许 Code Mode。

不要因为 Agent Browser 是否可用来决定 Code Mode 是否注册。

现有工具继续保留：

```text
browser
agent_browser
host_cli
workspace_*
code_mode
```

## 15. Execution Ledger

顶层调用由现有 `ToolExecutionPipeline` 记录：

```text
code_mode
```

内部 capability 调用建议记录：

```text
code_mode.browser
code_mode.host
code_mode.workspace.read
code_mode.workspace.write
```

每个内部调用至少记录：

- 父级 Code Mode 调用。
- capability 名称。
- 方法名称。
- 参数摘要。
- 开始时间。
- 结束时间。
- 成功或失败。
- 错误码。
- 超时状态。
- 输出摘要。
- artifact 引用。

要求：

- 复用现有 Execution Ledger。
- 不创建新的消息账本。
- 不重新引入已废弃的 `ai_agent_message*` 表。
- 不新增独立 transcript 表。
- 不把完整敏感参数写入日志。
- 不把源码全文写入普通日志。
- 可以记录源码 hash。
- 生成的文件必须和内部调用关联。

如果已有 `parent_tool_call_id` 能表达父子关系，直接复用。只有在确认现有 Ledger 无法表达时，才提出数据库字段变更，并同步 schema、迁移、PO、DAO、Mapper 和部署配置。

## 16. 安全边界

### 16.1 GraalJS 直接权限

脚本不能直接访问：

- 任意 Java Class。
- Spring ApplicationContext。
- ProcessBuilder。
- Java 文件系统 API。
- Java 网络客户端。
- 反射对象。
- 任意线程创建。
- 任意子进程。
- 环境变量。
- 原始 CDP 对象。

### 16.2 已授权能力仍需要单独限权

GraalJS 权限关闭不能替代 capability 策略：

- Browser 仍需用户身份和 Relay 状态检查。
- Host 仍需 CLI 白名单。
- Host 仍需 argv 执行，不允许 shell 字符串。
- Workspace 仍需 session 路径检查。
- CSV 和二进制文件仍需大小限制。
- 每次 capability 调用仍需 timeout 和审计。

### 16.3 同 JVM 限制

本期是受控内部 Agent 的同 JVM 执行模式，不是强安全沙箱。

同 JVM 模式不能绝对保证：

- 恶意 JS 永远不能消耗过多 CPU。
- 任何死循环都能立即停止。
- 任意第三方引擎漏洞不会影响主进程。
- 所有内存消耗都能被完全隔离。

如果以后需要抵御恶意租户代码，应增加独立 Runner JVM 或容器。本期不实现该隔离 Runner，但实现时不能把同 JVM 模式描述为强沙箱。

## 17. 任务拆分

### Task 0：冻结规格和验收标准

交付：

- `CodeModeContext` 不包含 `agentBrowser`。
- 只支持 GraalJS。
- 固定 `main(context)` 入口。
- `source/workspaceFile` 二选一。
- 明确 workspace 文件大小和路径规则。
- 明确同 JVM 的安全边界。

验收：

- 后续 Agent 不自行增加其他语言。
- 后续 Agent 不自行增加 Agent Browser 注入。

### Task 1：Domain 契约

修改 `Reactor-agent-domain`：

- `CodeModeTool`。
- `CodeModeExecutorPort`。
- `CodeModeRequest`。
- `CodeModeResult`。
- 错误码。

验收：

- domain 不 import GraalJS。
- 参数校验有单元测试。
- `source/workspaceFile` 互斥校验正确。

### Task 2：Code Mode 配置

修改 `Reactor-agent-types` 和 `Reactor-agent-app`：

- `CodeModeProperties`。
- `@ConfigurationProperties` 注册。
- `application-dev.yml`。
- `application-test.yml`。
- `application-prod.yml`。
- 配置文档和部署说明。

验收：

- 默认关闭或按项目约定配置。
- timeout、源码、输出、workspace 限制可以读取。
- 配置缺失时使用明确默认值。

实现配置变更时同步检查 Docker、部署文档和相关运行时环境。不要只修改 Java properties。

### Task 3：GraalJS 执行器

修改 `Reactor-agent-infrastructure`：

- 添加 GraalJS 依赖。
- 实现 Context 创建和关闭。
- 实现 JS 包装器。
- 实现 capability Proxy binding。
- 实现 Promise 处理。
- 实现 JS 值转换。
- 实现超时、取消和异常映射。

验收：

- 可以执行最小 `main(context)`。
- 可以返回 JSON-safe 对象。
- `Java.type`、`Packages`、文件 IO、进程创建不可用。
- 超时返回 `CODE_MODE_TIMEOUT`。
- Context 在成功和失败后都关闭。

### Task 4：Browser Capability

实现：

- `BrowserCodeCapability`。
- Browser 参数转换。
- Browser 结果转换。
- 复用现有 BrowserTool 策略。
- 资源清理。

验收：

- JS 可以执行 Browser CLI 命令。
- 使用当前用户身份。
- Browser 不可用时返回结构化错误。
- 不影响普通 Browser Tool。

### Task 5：Host Capability

实现：

- `HostCodeCapability`。
- Host CLI 参数转换。
- 白名单检查。
- argv 执行。
- 结果和 artifact 转换。

验收：

- 白名单 CLI 可以执行。
- 非白名单 CLI 被拒绝。
- shell 字符串不能执行。
- 不影响普通 Host CLI Tool。

### Task 6：通用 Workspace 文件能力

实现：

- `readText`。
- `readBytes`。
- `writeText`。
- `writeBytes`。
- 路径限制。
- 文件大小限制。
- 版本 hash。
- 原子写入。
- artifact 登记。

验收：

- 可以读写普通文本。
- 可以读写二进制文件。
- 不能访问其他 session。
- `../` 和宿主绝对路径被拒绝。
- 写入失败不会破坏旧文件。

### Task 7：CSV 能力

实现：

- `readCsv`。
- `writeCsv`。
- CSV 解析。
- CSV 转义。
- BOM 和 UTF-8 处理。
- 版本冲突检查。
- 行数、列数和内存限制。

验收：

- 两个 CSV 可以读入并合并。
- 合并结果可以写入新文件。
- CSV 可以读取、修改后覆盖原文件。
- 复杂字段不会被逗号、引号和换行破坏。
- 并发修改时返回 `CODE_MODE_FILE_CONFLICT`。

### Task 8：Workspace JS 文件执行

实现：

- `workspaceFile` 参数。
- 脚本目录白名单。
- JS 文件读取。
- 源码 hash。
- `main(context)` 校验。
- 直接执行和错误返回。

验收：

- 可以执行 `scripts/example.js`。
- 非 `.js` 文件被拒绝。
- workspace 外部文件被拒绝。
- 已有脚本不需要重新传入 source。

### Task 9：Runtime 装配和工具注册

修改：

- `ReactorRuntimeDependencies`。
- `ReactorRuntimeAutoConfiguration`。
- `AgentToolCollectionFactory`。

验收：

- Code Mode 启用时出现 `code_mode`。
- Code Mode 禁用时不暴露 `code_mode`。
- `agentBrowser` 不在 Code Mode Context。
- 现有 Browser、Agent Browser、Host CLI 行为不回归。

### Task 10：Ledger、取消和资源清理

实现：

- 顶层 `code_mode` 记录。
- 内部 Browser/Host/Workspace 调用记录。
- 父子调用关联。
- 源码 hash。
- artifact 关联。
- timeout、cancel、异常状态收口。

验收：

- 正常执行有完整记录。
- 脚本异常有失败记录。
- 超时有失败记录。
- 用户取消有取消记录。
- 不存在长期 `running` 的孤立调用。

### Task 11：测试和回归

必须增加：

- GraalJS 基础执行测试。
- Context 权限测试。
- Browser facade 测试。
- Host 白名单测试。
- Workspace 路径安全测试。
- 任意字节读写测试。
- CSV 解析和写入测试。
- CSV 合并测试。
- CSV 原文件覆盖测试。
- 文件版本冲突测试。
- Workspace JS 文件执行测试。
- timeout/cancel 测试。
- Ledger 关联测试。
- `agentBrowser` 不注入测试。
- 现有 Browser/AgentBrowser/Host CLI 回归测试。

## 18. 推荐 Agent 分工

为避免多个 Agent 同时修改同一个文件，建议按以下边界分工：

| Agent | 负责内容 | 主要文件范围 |
|---|---|---|
| Agent A | Domain 契约和 CodeModeTool | `Reactor-agent-domain/.../runtime/codemode/**` |
| Agent B | GraalJS 执行器 | `Reactor-agent-infrastructure/.../codemode/GraalJs*` |
| Agent C | Browser、Host、Workspace capability | `Reactor-agent-infrastructure/.../codemode/*Capability*` |
| Agent D | Runtime 装配和工具注册 | `ReactorRuntimeDependencies`、`ReactorRuntimeAutoConfiguration`、`AgentToolCollectionFactory` |
| Agent E | CSV、Ledger 和集成测试 | 测试目录、CSV 实现、Ledger 适配 |

依赖顺序：

```text
Task 0
  -> Task 1 + Task 2
  -> Task 3
  -> Task 4 + Task 5 + Task 6 + Task 7
  -> Task 8 + Task 9
  -> Task 10
  -> Task 11
```

## 19. 测试命令

基础编译：

```bash
mvn -pl Reactor-agent-domain -am compile -DskipTests
```

Domain 测试：

```bash
mvn -pl Reactor-agent-domain -am test -DskipTests=false
```

App 测试：

```bash
mvn -pl Reactor-agent-app test -DskipTests=false
```

边界测试：

```bash
mvn -pl Reactor-agent-app test -Dtest='*BoundaryTest' -DskipTests=false
```

完整构建：

```bash
mvn clean package -DskipTests
```

## 20. 完成标准

以下条件全部满足后，本期才算完成：

1. Agent 可以通过 `code_mode` 执行 JavaScript。
2. JavaScript 可以调用 `browser`。
3. JavaScript 可以调用 `host`。
4. JavaScript 可以读写 workspace 文本文件。
5. JavaScript 可以读写二进制文件。
6. JavaScript 可以读取 CSV 并得到结构化 rows。
7. JavaScript 可以合并两个 CSV 并输出新 CSV。
8. JavaScript 可以修改 CSV 并安全覆盖原文件。
9. 已有 JS 文件可以通过 `workspaceFile` 直接执行。
10. `CodeModeContext` 中没有 `agentBrowser`。
11. 本期没有实现其他语言执行器。
12. JS 无法直接访问 Java Class、文件系统、进程和网络客户端。
13. Host 只接受白名单工具和 argv。
14. Workspace 不能越过当前 session 根目录。
15. CSV 结果和其他文件可以登记为 artifact。
16. Code Mode 和内部 capability 调用写入 Execution Ledger。
17. timeout、cancel、异常都能释放资源。
18. 现有 Browser、Agent Browser、Host CLI 工具没有回归。

## 21. 本期明确不做

- 不实现 Java 动态编译器。
- 不实现 Python/GraalPy。
- 不实现其他语言 registry。
- 不把 `agentBrowser` 注入 Code Mode。
- 不把 Node.js/npm 模块注入 GraalJS。
- 不开放 `require` 或 `import`。
- 不开放任意文件系统。
- 不开放任意 shell。
- 不实现独立 Runner JVM 或容器隔离。
- 不新增独立的消息账本。
- 不删除现有 `AgentBrowserTool`。
- 不新增重复的 Browser 或 Host 权限逻辑。

## 22. 最终实现原则

```text
Agent 负责：
  选择工具
  编写循环和条件
  处理结构化数据
  组织任务流程

Java Runtime 负责：
  创建 GraalJS Context
  注入 browser / host / workspace
  校验路径和参数
  控制超时和调用次数
  复用现有工具权限
  写入文件和 artifact
  记录 Execution Ledger
  释放资源
```

实现时优先保持接口窄、能力可审计、文件操作可回滚，并避免把底层 Java 对象和 Spring 运行时暴露给 JavaScript。
