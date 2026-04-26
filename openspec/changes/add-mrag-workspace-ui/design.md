## Context

当前仓库已经有一条可运行的 MRAG 能力链路，但主要面向运行时工具调用，而不是面向人操作的工作台：

- `reactor-tool` 已提供知识库与文件管理接口：`/v1/documents/upload`、`/v1/documents/create_knowledge_base`、`/v1/documents/list_knowledge_base`、`/v1/documents/add_files`、`/v1/documents/add_web_url`、`/v1/documents/list_kb_files`、`/v1/documents/delete_files`。
- `reactor-tool` 已提供 MRAG 查询接口：`/v1/tool/mragQuery`，并以 SSE 返回流式回答。
- `ui/` 已存在“独立 Workspace 页面直连工具服务”的实现先例，即图片生成工作台。
- Java 主链路当前只接入了 `multimodalagent_tool -> /v1/tool/mragQuery` 的运行时查询能力，没有提供 MRAG 文档管理代理接口。

与此同时，项目里还保留了一套旧的 `IRagService/RagService` 与后台知识库管理能力，但它基于另一套存储与接入模型，不等同于 `reactor-tool` 下的 MRAG。本次变更如果把两条链路混在一起，会放大理解成本，也会模糊“首期只是给 MRAG 做可视化工作台”的目标。

本次设计的约束也很明确：

- 用户已经确认首期只需要文件级可视化与独立检索调试。
- 首期不要求查看 chunk / OCR / caption 明细。
- 首期优先复用现有 `reactor-tool` 接口，不新增 Python / Java 后端接口。

## Goals / Non-Goals

**Goals:**

- 在 `ui/` 中新增一个独立的 MRAG Workspace 页面，支持知识库列表查看、创建与切换。
- 在所选知识库下展示文件列表、文件入库状态和文件级操作入口。
- 支持两种入库方式：本地文件上传、网页链接入库。
- 为已入库文件提供原始文件预览或下载入口，帮助用户确认知识库中实际收录的资料。
- 提供独立的检索调试区，允许用户针对所选知识库发起 MRAG 查询，并看到流式结果与最终回答。
- 尽量复用现有 `ui` 的 workspace 路由、服务层风格、SSE 工具与视觉体系。

**Non-Goals:**

- 不新增 chunk 明细浏览器，不展示向量 payload、OCR/caption 明细或召回打分细节。
- 不改造 `ai-agent-station-study-domain/` 的聊天主链路，不改变 `multimodalagent_tool` 的运行时行为。
- 不新增 Java 代理接口，也不要求 `reactor-tool` 在首期补新 API。
- 不处理权限体系重构、组织级隔离或知识库共享策略。
- 首期检索调试仅要求文本问题输入，不要求补图片查询上传能力。

## Decisions

### Decision 1: 首期前端直接接入 `reactor-tool`，而不是先走 Java 主链路代理

采用方案：

- `ui` 仿照图片生成工作台的接入方式，直接通过 `REACTOR_TOOL_BASE_URL` 调用 `reactor-tool`。
- 知识库与文件管理使用 `/v1/documents/*`。
- 检索调试使用 `/v1/tool/mragQuery`。

原因：

- 现有 Java 主链路没有 MRAG 文档管理代理接口，补代理会把一个前端需求扩散成跨栈改造。
- `ui/src/pages/WorkspaceImageGeneration` 已证明“Workspace 直连工具服务”在本项目内是可接受范式。
- 首期需求明确偏向工具台与调试台，而不是并入统一会话持久化链路。

备选方案：

- 方案 A：先在 Java 侧补 `/api/agent/mrag/*` 代理。优点是统一鉴权和域名；缺点是开发面更大，首期收益不够高。

### Decision 2: 新增独立页面 `WorkspaceMRag`，并保留现有 Workspace 子路由模式

采用方案：

- 新增路由 `/workspace/mrag`。
- 保留已有 `/workspace/image-generation`。
- 在 Workspace 页面中引入轻量切换入口，避免新页面成为“只能手输 URL 才能访问”的隐藏能力。

原因：

- MRAG 工作台与图片生成工作台都属于独立工具面板，适合共享 Workspace 心智。
- 独立路由可以降低单页复杂度，后续也容易继续加入更多 workspace 工具。
- 保留现有图片生成页面，避免新需求覆盖旧能力入口。

备选方案：

- 方案 A：直接把 MRAG 塞进首页右侧工作区。问题是首页与会话流强耦合，不适合知识库管理场景。
- 方案 B：把 MRAG 硬塞进图片生成页作为新 tab。问题是两者职责差异太大，可维护性差。

### Decision 3: 页面结构按“知识库 / 文件 / 检索调试”三块组织，而不是做成单列表或纯表单页

采用方案：

- 左侧或上方为知识库列表与创建入口。
- 中间主体展示当前知识库的文件列表、状态、文件入库入口。
- 右侧或下方为检索调试区，绑定当前选中的知识库。

原因：

- 用户的核心动作天然分三步：选库、看文件、问问题。
- 三块结构有利于保持状态边界清晰，避免一个大表单同时承担管理与调试。
- 这也方便后续扩展 chunk 浏览器或更多工具，而不需要推翻布局。

备选方案：

- 方案 A：所有功能堆在单列长页面里。问题是操作链路长，状态切换混乱。

### Decision 4: 文件上传采用“两段式”流程，并优先把可预览 URL 写入知识库记录

采用方案：

1. 前端先调用 `/v1/documents/upload` 上传本地文件。
2. 从上传响应中取 URL 再调用 `/v1/documents/add_files` 提交入库任务。
3. 对本地文件服务返回的上传结果，优先使用 `preview_url` 作为 `add_files.file_url`；对 S3 场景使用原样返回的永久地址。

原因：

- 现有 `add_files` 只接受 `filename + file_url`，不接受浏览器 `File` 对象。
- `list_kb_files` 当前只稳定返回 `file_url`，不会返回独立 `preview_url`。如果本地文件入库时写入 `preview_url`，后续文件列表就仍然可以直接预览。
- `preview` URL 对 `download_utils.download_file` 仍可用，不影响解析链路。

备选方案：

- 方案 A：把 `permanent_url` 直接写入 `file_url`。问题是在本地文件服务场景下通常是 download URL，不利于后续预览。
- 方案 B：补后端字段专门存储 preview/download 双 URL。问题是超出首期范围。

### Decision 5: 文件状态通过轮询文件列表刷新，不引入新的任务推送协议

采用方案：

- 文件入库成功发起后，前端定时调用 `/v1/documents/list_kb_files` 刷新当前知识库文件列表。
- 当存在 `PENDING / RUNNING` 文件时开启轮询；全部进入终态后停止。

原因：

- 现有 API 已能表达 `file_status` 与 `task_status`。
- 首期没有必要为了状态更新单独引入 WebSocket/SSE 任务协议。
- 轮询方案简单、风险低，与当前工具服务能力匹配。

备选方案：

- 方案 A：新增专用任务状态流。问题是首期成本高，且会新增后端协议。

### Decision 6: 检索调试区只消费 MRAG 的回答流，不伪造 chunk 命中详情

采用方案：

- 前端把 `mragQuery` 的 SSE 内容当作“检索调试结果”展示，提供流式回答区和最终结果区。
- 如果返回内容中包含 Markdown 图片或可点击 URL，则按可视化结果渲染。
- 首期不展示底层 chunk 命中明细，因为当前后端没有稳定暴露这类数据。

原因：

- 现有 `/v1/tool/mragQuery` 只保证返回 OpenAI-compatible chunk 流，不提供结构化召回详情。
- 用户当前接受的首期目标是“能调试查询并看到结果”，不是“做召回分析器”。
- 不伪造不存在的数据，可以降低前后端语义不一致的风险。

备选方案：

- 方案 A：前端根据回答内容猜测命中来源。问题是准确性差，容易误导用户。
- 方案 B：本次顺手补召回详情接口。问题是超出首期范围。

## Risks / Trade-offs

- [直接接入 `reactor-tool`，接入面未统一进 Java 鉴权体系] → 首期明确定位为内部工具台；后续若要外放，再补代理或鉴权层。
- [本地文件预览依赖把 `preview_url` 作为入库源地址写入记录，存在接口约定隐含性] → 在前端服务层集中封装 URL 选择规则，并在设计/实现中补注释说明原因。
- [轮询文件状态会产生额外请求] → 仅在存在进行中文件时轮询，且限定当前选中知识库范围。
- [首期不提供 chunk 明细，部分用户会把“检索结果”理解为召回细节] → 页面文案明确区分“文件级资料视图”和“回答结果视图”。
- [新增 Workspace 路由后，如果入口设计不清晰，用户可能找不到页面] → 通过 Workspace 内部切换入口暴露新页面，而不是只依赖直接 URL。

## Migration Plan

1. 在 `ui` 中新增 MRAG workspace 路由、页面、类型与服务层。
2. 抽离或复用现有 Workspace 顶部导航/切换入口，让 MRAG 页面可从前端显式访问。
3. 接入知识库、文件、URL 入库与文件状态轮询。
4. 接入 `mragQuery` 的 SSE 调试区。
5. 完成文件预览/下载与空态/异常态处理后上线。

回滚策略：

- 移除 `/workspace/mrag` 及其入口；
- 保留既有 `reactor-tool` 接口与聊天主链路不变；
- 因为首期不涉及数据库迁移和 Java 代理改造，回滚主要是前端回退。

## Open Questions

- Workspace 顶部切换入口最终采用页内 tab、按钮组还是独立 workspace 索引页；本次默认优先采用轻量切换入口，不额外引入新的目录页。
- 是否需要在首期就支持“上传查询图片并传入 `image_urls`”；当前默认不做，后续可在同页扩展。
- 如果未来用户明确要求“看到 RAG 里实际存了什么 chunk”，需要新增后端明细接口还是接入现有存储层直读；本次暂不决策。
