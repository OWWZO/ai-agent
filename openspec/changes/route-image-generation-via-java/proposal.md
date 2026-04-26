## Why

当前 `WorkspaceImageGeneration` 页面会从浏览器直接调用 `reactor-tool` 的 `/v1/tool/image_generation` 接口，这让前端耦合了 Python 工具服务地址与协议细节，也绕开了 Java 主后端的统一鉴权、设备隔离、审计与持久化边界。与此同时，图片生成结果虽然已经会被 Python 侧上传为稳定文件，但 Java/MySQL 侧没有沉淀结构化记录，导致前端无法通过业务接口查询“之前生成过哪些图片”。

这个问题现在值得单独收口，因为仓库里已经存在 Java 侧图片工具调用配置、统一请求头里的 `X-Device-Id`、以及会话/产物持久化的既有设计模式。把生图工作台纳入 Java 主链路，可以同时解决架构分层不清和历史不可追溯两个问题。

## What Changes

- 将前端生图工作台的文生图 / 图生图请求从直连 `reactor-tool` 改为调用 Java 后端业务接口。
- 在 Java 后端新增生图工作台接口，负责接收前端请求、调用 Python 生图接口、归一化响应并返回前端可直接消费的数据结构。
- 在 MySQL 中新增图片生成记录表，按“每张生成结果图片一条记录”保存请求信息、设备/用户归属、生成参数与图片文件信息。
- 在 Java 后端新增图片生成历史查询接口，让前端能够分页读取当前用户或当前设备下的历史生成图片列表。
- 在前端生图工作台中接入 Java 历史接口，展示之前生成过的图片，并复用现有图片预览/下载能力。
- 收敛前端配置语义：生图工作台不再依赖 Python 工具服务地址作为浏览器直连目标，避免前端继续感知 `reactor-tool` 拓扑。

## Capabilities

### New Capabilities
- `workspace-image-generation`: 生图工作台通过 Java 后端统一发起生成请求、持久化生成结果，并支持历史图片列表查询与展示。

### Modified Capabilities

## Impact

- 前端：`ui/src/pages/WorkspaceImageGeneration/`、`ui/src/services/`、相关类型与历史展示组件。
- Java Trigger / Domain / Infrastructure：新增生图工作台 Controller、应用服务、Python 网关、DAO/PO/Mapper 与历史查询装配。
- 应用配置与数据库：`ai-agent-station-study-app/src/main/resources/application-*.yml`、`db/schema.sql`、必要的测试数据与持久化验证。
- Python 工具服务：继续复用现有 `/v1/tool/image_generation` 协议，必要时仅做返回字段对齐，不改其作为图片实际生成执行端的职责。
