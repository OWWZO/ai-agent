# Data Model: Agent Legacy Bridge 实质删除与子域再收敛

> 本特性不新增数据库表。这里的“数据模型”描述的是本轮 bridge 删除与子域再收敛中需要被稳定下来的架构实体、归属状态与守卫对象。

## 1. Legacy Bridge

### 1.1 职责

- 表示仍停留在旧 `reactor/service` 或旧 `service/**` 下的过渡接口、实现或委派壳
- 本轮的目标对象是“被删除”或“被明确界定为非 bridge”

### 1.2 关键属性

| 字段 | 含义 | 说明 |
|------|------|------|
| `bridgeName` | bridge 名称 | 如 `IGptProcessService`、`DataAgentService` |
| `currentDependents` | 当前依赖方 | case、app、test 或其他模块 |
| `bridgeCategory` | bridge 类型 | 入口桥、委派桥、实现桥、兼容 facade |
| `removalCondition` | 删除前提 | 哪个稳定 seam 或模型归属完成后可删除 |
| `removalStatus` | 当前状态 | 待删、迁移中、已删除 |

### 1.3 约束规则

- 若 `currentDependents` 只剩过渡委派或测试辅助，则应优先删除
- bridge 不得作为新代码的默认依赖入口
- bridge 若不能在本轮删除，必须被降级为明确延期项，而不能继续模糊存在

## 2. Stable Domain Seam

### 2.1 职责

- 表示 bridge 删除后真正承担主链路职责的稳定层间边界
- 用于替代旧 `reactor/service` 的生产级入口

### 2.2 关键属性

| 字段 | 含义 |
|------|------|
| `seamName` | seam 名称 |
| `ownerLayer` | 所属层级，如 case、domain、infrastructure |
| `capabilityScope` | 所负责的能力范围 |
| `requestContract` | 输入契约 |
| `responseContract` | 输出契约 |

### 2.3 约束规则

- seam 必须有唯一主归属
- seam 之间的协作应通过显式接口完成
- seam 不得再依赖已标记删除的 bridge

## 3. Subdomain Ownership Map

### 3.1 职责

- 描述剩余 legacy 模型、配置、步骤工厂与技术语义最终应归属到哪个稳定边界
- 用于指导“子域模型再收敛”

### 3.2 关键属性

| 字段 | 含义 |
|------|------|
| `artifactName` | 模型/配置/工厂/步骤名称 |
| `artifactType` | 请求模型、响应模型、配置契约、步骤工厂等 |
| `currentPackage` | 当前包归属 |
| `targetOwnership` | 目标归属，如 runtime、rag、app config、infrastructure |
| `ownershipState` | 迁移中、稳定归属、明确延期 |

### 3.3 约束规则

- 每个 artifact 只能有一个主归属
- 若保留历史包名，则必须被标记为稳定契约，而不是桥接壳
- 不允许新旧两份语义完全重复的 artifact 长期并存

## 4. Legacy Package Allowlist Rule

### 4.1 职责

- 表达哪些历史包名在本轮之后仍被允许暂存
- 约束这些包只能承载“明确界定的稳定契约或延期项”

### 4.2 关键属性

| 字段 | 含义 |
|------|------|
| `packageName` | 历史包名 |
| `allowedReason` | 允许存在原因 |
| `allowedContents` | 允许承载的内容类型 |
| `forbiddenExpansion` | 禁止扩张规则 |

### 4.3 约束规则

- allowlist 不是豁免令，只是短期边界说明
- allowlist 中的目录不得新增 bridge、委派壳或新的主逻辑
- 一旦归属迁移条件满足，应从 allowlist 中移除

## 5. Bridge Removal Guard

### 5.1 职责

- 自动验证 bridge 是否已经删除、旧目录是否被错误复用
- 锁定最终交付状态

### 5.2 关键检查项

| 检查项 | 目标 |
|--------|------|
| bridge 文件检查 | 必删 bridge 文件残留数为 0 |
| 生产依赖扫描 | case/trigger/app/infrastructure 不再依赖已删 bridge |
| legacy 包扫描 | 未解释的 `reactor/service`、`reactor/model`、`service/**` 残留为 0 |
| allowlist 检查 | 允许延期目录没有新增越界内容 |
| 主链路回归 | query/dataagent/image generation/history replay 等能力行为稳定 |

### 5.3 状态转换

```text
已识别 -> 已分类 -> 已收敛/已延期 -> 已纳入守卫
```

## 6. Relationships

```text
Legacy Bridge
  -> is replaced by Stable Domain Seam

Stable Domain Seam
  -> consumes Subdomain Ownership Map

Legacy Package Allowlist Rule
  -> constrains Subdomain Ownership Map entries still under old package names

Bridge Removal Guard
  -> validates Legacy Bridge
  -> validates Stable Domain Seam
  -> validates Legacy Package Allowlist Rule
```

说明：

- `Legacy Bridge` 是本轮直接删除对象
- `Stable Domain Seam` 是删除 bridge 后的真实承载者
- `Subdomain Ownership Map` 决定剩余 legacy 模型/配置的最终位置
- `Legacy Package Allowlist Rule` 只服务于有限延期，不服务于长期模糊状态
- `Bridge Removal Guard` 负责把这些关系变成可执行验收门槛
