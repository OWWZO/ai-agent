## Context

当前生图工作台的 `images / edits` 模式由前端直接 `fetch` 到 `reactor-tool` 的 `/v1/tool/image_generation`，浏览器需要感知 Python 服务地址；而 Java 主链路没有接管这条能力，也没有对生成结果做业务持久化。与此同时，Python 侧图片生成接口已经具备两项可复用能力：

- 支持同步 JSON 响应，返回 `fileInfo` 数组；
- 在生成完成后将图片上传到现有文件服务，返回稳定的下载/预览地址。

Java 侧也并非完全没有相关能力：现有 `ImageGenerationTool` 已经通过 `autobots.autoagent.image_generation_url` 调用 Python 生图接口，`FileInformation` 也已是项目内统一的文件元数据抽象；前端请求工具还会统一携带 `X-Device-Id`，这为匿名工作台历史隔离提供了现成基础。

因此，这次设计的重点不是重新发明图片生成能力，而是把“工作台发起请求、结果持久化、历史回看”纳入 Java 主后端的标准分层。

## Goals / Non-Goals

**Goals:**

- 让生图工作台的文生图 / 图生图请求只调用 Java 后端，不再从浏览器直连 Python 工具服务。
- 在 Java 侧新增一条清晰的 DDD 链路：Trigger 接口、Domain 应用服务、Infrastructure Python 网关、MySQL 持久化。
- 将每张成功生成的结果图片持久化到一张新表中，并保留可用于历史展示的关键信息。
- 为前端提供历史查询接口，能够按“单次生成请求”聚合展示之前生成过的图片。
- 尽量复用现有 Python 上传产物能力、Java 文件元数据结构和前端图片预览/下载展示逻辑。

**Non-Goals:**

- 不重写 Python 侧实际生图逻辑，也不把图片真实生成职责迁回 Java。
- 不把 Agent 对话流里的 `image_generation_tool` 会话产物链路并入本次工作台需求。
- 不回填新表创建前已经生成的历史图片。
- 不把图生图输入的原始 base64、遮罩原图等大体积内容直接存入 MySQL。
- 不在本期新增“删除历史”“收藏历史”“按标签搜索历史”等扩展管理能力。

## Decisions

### Decision 1: 新增独立的“工作台生图”后端链路，不直接复用 `ImageGenerationTool`

采用方案：

- 在 `trigger` 层新增工作台生图 Controller，例如：
  - `POST /api/agent/image-generation/generate`
  - `GET /api/agent/image-generation/history`
- 在 `domain` 层新增专门的工作台应用服务，负责参数校验、请求编排、结果入库与历史聚合。
- 在 `infrastructure` 层新增 Python 网关，专门负责调用 `reactor-tool` 的 `/v1/tool/image_generation` 同步接口。

原因：

- 现有 `ImageGenerationTool` 强依赖 `AgentContext`、SSE、会话产物打印器和工具运行语义，适合 Agent 工具链，不适合普通 REST 工作台。
- 工作台需求需要“同步请求 + 持久化 + 历史列表”，强行复用工具类会把工具运行时耦合进页面接口。

备选方案：

- 方案 A：直接在 Controller 中调用现有 `ImageGenerationTool`。问题是依赖 `AgentContext`，职责不匹配，后续维护成本高。
- 方案 B：前端继续直连 Python，只新增 Java 历史接口。问题是架构问题没有被真正解决。

### Decision 2: 新增一张结果明细表，按“每张输出图片一行”持久化

采用方案：

- 新增单表，例如 `ai_agent_image_generation_record`。
- 以 `request_id` 作为一次生成请求的批次标识，以 `result_index` 区分同批次内的多张结果图。
- 每行存储以下关键信息：
  - 归属信息：`device_id`，以及预留的 `user_id`
  - 请求信息：`request_id`、`prompt`、`mode`、`size`、`batch_count`
  - 输入摘要：`source_image_count`、`mask_image_count`
  - 输出文件信息：`file_name`、`oss_url`、`domain_url`、`download_url`、`file_size`
  - 执行补充：`used_fallback`
  - 通用字段：`create_time`、`update_time`、`deleted`

原因：

- 用户明确要求“新建一张表”，而工作台当前最核心的历史诉求是“展示之前生成了哪些图片”，按图片明细落一张表最直接。
- 一次请求可能生成多张图片，按图片一行能避免把 `fileInfo` 再塞回 JSON 字段后续重复解析。
- 历史列表、后续统计、下载次数扩展都更容易围绕单图记录做演进。

明确约束：

- 不存储原始 `fileNames` / `maskFileNames` 的 base64 内容，避免表膨胀与无效冗余。
- 只保存输入数量摘要，不把大文件内容或临时 data URL 写入数据库。

备选方案：

- 方案 A：一张请求表 + 一张图片子表。更规范，但超出本次“先建一张表并形成闭环”的必要复杂度。
- 方案 B：一条请求一行，`fileInfo` 用 JSON 数组存。实现快，但历史查询、单图展示和后续统计都不够友好。

### Decision 3: Java 对外请求契约按“真实后端语义”收敛，不再暴露 Python 直连配置

采用方案：

- 前端发给 Java 的图片生成请求只保留真正有业务意义的字段：`prompt`、`mode`、`size`、`n`、`fileNames`、`maskFileNames`、`fileName`、`fileDescription`。
- Java 使用服务端配置的 Python 基地址调用 `reactor-tool`，前端不再参与选择 Python 目标地址。
- 前端工作台移除或停用图片模式下的 `toolBaseUrl` 直连语义，避免继续让浏览器感知 Python 拓扑。

原因：

- 当前 Python 图片生成实现已经改为读取服务端环境变量，前端传的 `baseUrl/apiKey/model` 在图片模式下并不真正决定生成行为。
- 如果仅把“前端 -> Python”换成“前端 -> Java -> Python”，但继续保留这些失真配置，职责漂移仍然存在。

备选方案：

- 方案 A：Java 仅做透传，把前端现有所有字段原样转给 Python。问题是保留了无效配置，后续定位问题仍然混乱。

### Decision 4: 历史接口按“请求批次”聚合返回，而不是直接把单表行平铺给前端

采用方案：

- 数据库存储仍然是一图一行，但历史接口返回时按 `request_id` 聚合成批次视图：
  - `requestId`
  - `prompt`
  - `mode`
  - `size`
  - `createdAt`
  - `images[]`
- 分页以“请求批次”为单位，而不是以单图行为单位。
- 实现上使用“两段式查询”：
  1. 先按 `device_id` 查询当前页的 `request_id` 列表；
  2. 再批量加载这些 `request_id` 下的所有图片记录并组装返回。

原因：

- 对用户而言，“一次生成请求 + 其结果图列表”才是自然的历史单元。
- 如果直接返回单图平铺列表，前端需要自己重建批次，且同一 prompt 会重复出现多次。

范围约束：

- 本期历史隔离以 `X-Device-Id` 为准，`user_id` 只作为后续认证场景扩展预留，不在本期引入匿名历史迁移逻辑。

备选方案：

- 方案 A：直接返回单图平铺分页。实现简单，但前端体验差，也不符合工作台历史展示习惯。

### Decision 5: 继续复用 Python 产物上传结果，Java 只做记录与转发，不二次上传图片

采用方案：

- Java 调用 Python 生图接口后，直接消费其返回的 `fileInfo` 元数据。
- Java 将 `fileInfo` 映射为工作台响应与历史记录，不再重新下载图片、重新上传文件服务。

原因：

- Python 侧已经完成“生成图片 -> 落地临时文件 -> 上传文件服务 -> 返回稳定 URL”的闭环。
- Java 二次上传只会增加 I/O、链路耗时和失败面，并不会提升本期业务价值。

备选方案：

- 方案 A：Java 收到 URL 后再回源下载并重新上传。问题是重复工作且容易引入一致性问题。

### Decision 6: 生成接口保持同步请求，先不引入工作台 SSE 进度流

采用方案：

- 工作台 `generate` 接口采用同步 HTTP 返回最终结果。
- Java 网关调用 Python 时使用 `stream=false`。

原因：

- 当前前端工作台本身就是“发送后等待最终图片结果”的交互，尚未形成稳定的增量进度协议需求。
- 本次的核心价值是架构收口和历史沉淀，引入 SSE 会显著增加前后端复杂度。

备选方案：

- 方案 A：同时设计 Java SSE 透传。问题是会把简单闭环拖成双协议改造，超出当前必要范围。

## Risks / Trade-offs

- [单表按图片存储会重复保存同批次的 prompt/mode 等字段] → 接受适度冗余，换取单表方案的简单性和后续查询便利。
- [新增 Java 代理层后，链路多一跳，错误定位更复杂] → 统一透传 `requestId`、记录 Python 错误消息，并在 Java 网关日志中打印关键请求上下文。
- [按请求批次分页需要两段式查询，DAO 实现复杂度高于平铺分页] → 在 DAO/Mapper 层显式拆成“查 requestId 页 + 查批次明细”两步，并补齐聚合组装测试。
- [旧前端本地配置仍可能残留 `toolBaseUrl` 等字段] → 前端加载旧本地配置时忽略废弃字段，避免影响新接口。
- [历史以设备维度隔离，用户换设备后看不到旧记录] → 在本期文档中明确该行为；若未来要做登录态聚合，可利用预留 `user_id` 字段扩展。

## Migration Plan

1. 新增数据库表、PO、DAO、Mapper XML 与必要索引。
2. 新增 Python 图片生成网关 DTO 与调用实现，打通 Java -> Python 同步请求。
3. 新增工作台图片生成应用服务，完成结果入库和历史聚合装配。
4. 新增前端 API service，并把 `WorkspaceImageGeneration` 的 `images / edits` 模式切换到 Java 接口。
5. 在工作台页面新增历史列表区域，消费新的历史查询接口。
6. 验证生成成功、图生图、分页历史、空历史、Python 异常透传等场景后上线。
7. 若需要回滚，可先恢复前端直连旧 Python 接口；新表保留但不会影响旧链路。

## Open Questions

- 历史列表是否需要“清空历史”或“删除单条”能力；本期默认不做。
- 工作台中当前遗留的 `chat` 调试分支是否还需要保留为独立能力；若保留，应避免混用本次历史接口。
- 历史列表是否需要展示输入参考图缩略信息；本期默认只展示输出结果图和请求摘要，避免额外存储与上传复杂度。
