# Contract: Domain Infrastructure Seam

## Goal

定义本次收敛后 `domain <- infrastructure` 的稳定 seam，明确哪些技术能力必须下沉，哪些边界守卫必须长期存在。

## 1. Port / Repository Seam

### Required Shape

- `domain` 声明 repository 或 port contract
- `infrastructure` 提供对应实现
- `app` 负责把实现装配回运行时

### Typical Capability Families

| 能力族 | `domain` 责任 | `infrastructure` 责任 |
|--------|---------------|-----------------------|
| 模型调用 | 定义调用语义、输入输出模型 | 构建客户端、处理超时/headers/URL |
| 数据查询 | 定义查询语义、约束与结果模型 | 承接 JDBC provider、catalog、dialect、连接池 |
| 工具运行时 | 定义工具调用语义与结果 contract | 承接 MCP/runtime/远端工具调用 |
| 文件产物 | 定义产物引用语义 | 承接上传、下载、稳定链接生成 |
| 执行账本 | 定义读写仓储 contract | 承接 DAO、Mapper XML、持久化实现 |

## 2. Forbidden Dependencies in Domain

以下内容不得继续停留在 `domain`：

- `SseEmitter`
- `new OkHttpClient`
- `JdbcDataProvider`
- `HttpUtils`
- 连接池、catalog、dialect 技术执行器
- `applicationContext.getBean(...)`
- `SpringContextHolder`

## 3. Compatibility Bridge Rules

若某个旧实现无法在同一任务中立即移除，bridge 必须满足：

1. 文件顶部写中文注释说明“过渡桥接原因、依赖方、删除时机”
2. 不得成为新代码默认依赖入口
3. 必须能在后续任务中被显式删除

## 4. Boundary Guard Contract

以下守卫必须长期存在并作为验收门槛：

1. 旧目录扫描：`domain/agent/service`、`domain/agent/reactor`
2. 协议扫描：`domain` 中 `SseEmitter` 命中为 0
3. 技术依赖扫描：`new OkHttpClient`、`JdbcDataProvider` 命中为 0
4. 运行时查找扫描：`SpringContextHolder`、`applicationContext.getBean(...)` 命中为 0
5. 主链路依赖扫描：`case/trigger/app` 不再引用旧根接口

## 5. Compatibility Notes

- 本期不新增数据库结构
- 现有 controller 路由、session memory、history replay、tool-output 恢复等终端用户行为保持不变
- 若需要最小化调整 `trigger`、`app` 或 `infrastructure` 以承接迁出职责，调整必须只服务于边界收敛本身

## 6. Current Stable Seam After 019

- runtime 远端调用：
  - `RemoteHttpPort` -> `OkHttpRemoteHttpAdapter`
  - `RemoteStreamPort` -> `OkHttpRemoteStreamAdapter`
  - `FileArtifactPort` -> `ReactorToolFileArtifactAdapter`
- dataquery：
  - `DataQueryExecutionPort` -> `DataQueryExecutionAdapter`
  - `DataQueryMetadataPort` -> `DataQueryMetadataAdapter`
  - `JdbcDataProvider`、`JdbcDataMetaProvider`、`JdbcUtils` 已下沉到 `infrastructure.dataquery/**`
- Spring 装配：
  - `app` 负责把上述实现装配回 `ReactorRuntimeDependencies`
  - `domain` 不再保留 `OkHttpUtil`、`HttpUtils`、`JdbcUtils` 等直接技术入口
- 当前 bridge 删除条件：
  - `reactor/service/IGptProcessService`、`IMultiAgentService`、`impl/*` 仅在 legacy query 主链路完全切到新领域语义接口后删除
  - `reactor/service/DataAgentService`、`Nl2SqlService` 仅在 dataagent 领域语义与 legacy DTO 完全解耦后删除
  - `domain/agent/service/**` 下仍保留的执行策略与装配节点，需要等 case/runtime 新模型完全替代后再清空目录
