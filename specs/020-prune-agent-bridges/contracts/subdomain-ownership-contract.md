# Contract: Subdomain Ownership

## Goal

定义 bridge 删除之后，剩余 legacy 模型、配置、步骤工厂与技术语义应如何归属到稳定子域或稳定契约。

## 1. Ownership Categories

### Stable Subdomain Ownership

适用于应归入 `runtime / ledger / memory / rag / role` 的领域语义：

- Agent 请求/响应与事件语义
- 执行步骤、运行时 handler、armory/execute 节点
- RAG / role / replay / memory 相关模型

### Stable Technical Contract

适用于跨层共享但本质上属于技术契约或配置契约的对象：

- dataquery 配置对象
- image generation gateway / command / result 模型
- 供 app/infrastructure/trigger 共同消费的稳定请求模型

### Explicitly Deferred Legacy Package

仅适用于本轮无法安全物理迁移，但已明确禁止扩张的历史包：

- 必须记录允许原因
- 必须记录禁止扩张范围
- 必须记录后续处理方向

## 2. Required Shape

- 每个 legacy artifact 必须有唯一主归属
- 归属完成后，新代码必须依赖目标归属，而不是继续依赖旧包副本
- 若保留历史包名，必须被视为“稳定契约”而非“过渡桥接”

## 3. Forbidden Shape

- 同一语义在新旧两个包中长期并存
- 用“暂时先放着”替代归属说明
- 把 dataquery、image generation、request/response 模型继续统一视作 `reactor` 杂项
- 把执行步骤、工厂或 runtime 支撑类继续无限期留在 `service/**`

## 4. Acceptance Signals

满足以下信号时，ownership contract 视为成立：

1. 每个残留 legacy artifact 都可被映射到稳定归属或明确延期项
2. case/trigger/app/infrastructure 的生产依赖指向新的稳定归属
3. 旧目录没有出现新的主逻辑或重复副本
4. 文档与边界测试使用相同的分类口径

## 5. Current Candidate Areas

- `reactor/model/req|response|multi|dto|imagegeneration/**`
- `reactor/config/data/**`
- `reactor/service/imagegeneration/**`
- `service/execute/**`
- `service/armory/**`
- `service/runtime/**`

这些区域在本轮必须被重新分类为“稳定归属”或“明确延期”，不能继续作为未定义状态存在。

## 6. Final Allowlist For 020

以下历史包在 `020-prune-agent-bridges` 完成后被明确界定为“允许延期但禁止扩张”的稳定历史契约：

- `reactor/config/data/**`
  - 归属性质：dataagent 技术配置契约
  - 当前消费者：`app`、`infrastructure`、`rag`
  - 禁止扩张：不得新增与配置无关的运行时编排、bridge 或 controller 协议语义
- `reactor/model/req|response|multi|dto/**`
  - 归属性质：query / replay / dataagent 共享请求响应契约
  - 当前消费者：`case`、`trigger`、`runtime`、`ledger`
  - 禁止扩张：新增稳定模型优先进入 `runtime` / `ledger`
- `reactor/model/imagegeneration/**`
  - 归属性质：工作台生图请求/响应/网关契约
  - 当前消费者：`trigger`、`infrastructure`、`runtime`
  - 禁止扩张：不得把与工作台生图无关的通用模型继续放入此目录
- `reactor/service/**`
  - 归属性质：dataagent 元数据/向量服务与工作台生图技术契约
  - 当前消费者：`app`、`rag`、`trigger`、`infrastructure`
  - 禁止扩张：不得重新引入 query/dataagent bridge、旧 handler 或新的应用入口编排
- `service/execute/**`
  - 归属性质：domain 内部仍在使用的执行策略节点与工厂
  - 当前消费者：`case`、`domain`
  - 禁止扩张：`trigger` / `app` / `infrastructure` 不得新增依赖
- `service/armory/**`
  - 归属性质：domain 内部仍在使用的装配策略节点与工厂
  - 当前消费者：`case`、`domain`
  - 禁止扩张：不得演化为新的主链路 bridge 入口
- `service/runtime/**`
  - 归属性质：AiClient runtime registry 稳定历史契约
  - 当前消费者：`case`、`domain`
  - 禁止扩张：仅允许 runtime registry 语义，不得继续堆放其它运行时杂项
