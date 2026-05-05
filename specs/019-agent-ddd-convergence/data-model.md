# Data Model: Agent 领域边界最终收敛

> 本特性不新增数据库表。这里的“数据模型”描述的是本次收敛过程中需要稳定下来的核心架构实体、层间契约与边界规则。

## 1. Agent Application Workflow

### 1.1 职责

- 代表一次从 `trigger` 进入 `case` 的应用编排流程
- 承接 dispatch、execute、armory、task 等主链路编排职责
- 负责把触发层输入转换为领域可消费的调用链

### 1.2 关键属性

| 字段 | 含义 | 说明 |
|------|------|------|
| `entryType` | 入口类型 | HTTP、Job、内部应用调用等 |
| `requestModel` | 请求模型 | 现有 Agent 请求、任务命令或装配命令 |
| `sessionStream` | 会话输出抽象 | 应用层使用的协议无关输出端口 |
| `executionStrategy` | 执行策略 | 由应用层选择并驱动的执行路径 |
| `completionState` | 完成状态 | 成功、失败、停止等 |

### 1.3 约束规则

- 只能由 `trigger` 或应用内部入口发起
- 不直接依赖 `SseEmitter`、`OkHttpClient`、`JdbcDataProvider`
- 不允许通过旧 `domain/agent/service` 根接口继续形成并行主线

## 2. Domain Subdomain

### 2.1 职责

- 表达 Agent 有界上下文内的稳定能力归属
- 用于替代历史 `reactor` catch-all 总包

### 2.2 子域集合

| 子域 | 关注点 |
|------|--------|
| `runtime` | Agent 生命周期、上下文、工具主循环、运行时行为 |
| `ledger` | 执行账本、回放、tool-output 聚合与历史重建 |
| `memory` | 会话记忆、上下文压缩、运行记忆重建 |
| `rag` | 检索增强、schema/table/SOP recall 语义 |
| `role` | 角色修复、角色治理与角色相关领域规则 |

### 2.3 约束规则

- 每类能力只能有一个主归属子域
- 子域可通过显式 port/repository seam 协作，不能借旧总包隐式耦合
- 子域内部允许保留领域服务、实体、值对象、聚合模型，但不承载协议和技术执行器

## 3. Domain Output Contract

### 3.1 职责

- 表达领域层对过程输出和最终结果的统一输出语义
- 允许触发层和应用层以不同协议适配同一领域输出

### 3.2 关键属性

| 字段 | 含义 |
|------|------|
| `payload` | 当前输出内容 |
| `completion` | 是否结束 |
| `failure` | 是否异常结束 |
| `semanticType` | 输出语义类型，例如 thought、task、result |

### 3.3 约束规则

- 不绑定 `SseEmitter`、HTTP response、WebSocket session 等具体协议对象
- 允许通过应用层流抽象或领域 printer 契约承接
- 历史回放与实时输出的语义类型应可共用

## 4. Technical Capability Port

### 4.1 职责

- 代表领域层声明、基础设施层实现的外部能力接口
- 隔离模型调用、远程工具、JDBC 查询执行和文件产物等技术细节

### 4.2 典型端口族

| 端口族 | 说明 |
|--------|------|
| `Model Invoke Port` | 模型调用、对话请求、流式响应处理 |
| `Data Query Port` | 数据查询执行、schema/catalog/dialect 驱动能力 |
| `Tool Runtime Port` | 工具/MCP 运行时调用与结果交付 |
| `File Artifact Port` | 文件上传、下载、引用生成 |
| `Multi-Agent Gateway Port` | 外部多代理或远端协作能力 |

### 4.3 约束规则

- `domain` 只声明端口，不创建技术客户端
- `infrastructure` 必须承接端口实现
- `app` 只负责端口实现的装配，不写业务判断

## 5. Compatibility Bridge

### 5.1 职责

- 表达迁移期间短期保留的过渡适配单元
- 用于在不破坏主链路可运行性的前提下逐步删除旧路径

### 5.2 必备元数据

| 字段 | 含义 |
|------|------|
| `bridgeOwner` | 当前依赖方 |
| `retentionReason` | 暂时保留原因 |
| `removalTrigger` | 删除前提 |
| `targetTask` | 预计在哪个任务或阶段删除 |

### 5.3 约束规则

- 必须显式标注为过渡桥接
- 不得作为最终主路径长期保留
- 一旦依赖方消失，桥接代码必须可删除

## 6. Boundary Guard Rule

### 6.1 职责

- 锁定最终目录结构和禁止依赖
- 用自动化方式阻止边界回流

### 6.2 关键检查项

| 检查项 | 目标 |
|--------|------|
| 旧目录检查 | `domain/agent/service` 与 `domain/agent/reactor` 不再作为主路径存在 |
| 协议泄漏检查 | `domain` 中不再出现 `SseEmitter` |
| 技术依赖检查 | `domain` 中不再直接出现 `new OkHttpClient`、`JdbcDataProvider` |
| 运行时查找检查 | `domain` 中不再出现 `SpringContextHolder`、`applicationContext.getBean(...)` |
| 主链路依赖检查 | `case/trigger/app` 不再依赖旧 `domain.agent.service` 根接口 |

### 6.3 状态转换

```text
未覆盖 -> 已定义 -> 已纳入测试 -> 阻止回流
```

说明：

- “已定义”表示边界规则已写入 spec/plan 与文档
- “已纳入测试”表示存在对应测试或目录扫描
- “阻止回流”表示该规则已成为交付验收门槛

## 7. Relationships

```text
Trigger Entry
  -> Agent Application Workflow
       -> Domain Subdomain
            -> Technical Capability Port
                 -> Infrastructure Adapter

Boundary Guard Rule
  -> validates Agent Application Workflow
  -> validates Domain Subdomain
  -> validates Compatibility Bridge
```

说明：

- `Trigger Entry` 只负责协议接入与路由
- `Agent Application Workflow` 是主链路编排中心
- `Domain Subdomain` 承接领域语义与业务规则
- `Technical Capability Port` 是领域与技术实现的稳定 seam
- `Boundary Guard Rule` 是整个收敛结果的持续守卫
