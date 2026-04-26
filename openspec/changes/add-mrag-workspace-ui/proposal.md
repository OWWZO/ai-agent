## Why

当前项目已经具备 MRAG 的后端检索与知识库入库能力，但缺少面向业务和开发同学的可视化工作台。用户无法直接查看已有知识库、确认文件入库状态、预览原始资料，或在独立界面中调试检索效果，这使 MRAG 能力的使用门槛和排障成本都偏高。

## What Changes

- 新增一个独立的 MRAG Workspace 前端页面，提供知识库列表、知识库文件列表和文件状态可视化能力。
- 在前端接入现有 `reactor-tool` 的 MRAG 文档管理接口，支持创建知识库、上传文件、添加网页链接、删除文件和刷新文件列表。
- 在前端接入现有 `reactor-tool` 的 `/v1/tool/mragQuery` SSE 检索接口，提供独立的检索调试区域，展示检索过程中的回答流和最终结果。
- 为已入库文件提供原始文件预览/下载入口，帮助用户确认知识库中实际收录的资料。
- 复用现有 `ui` 中 workspace 风格、服务层组织方式和配置方式，避免引入新的运行时接入模式。

## Capabilities

### New Capabilities
- `mrag-workspace-ui`: 提供 MRAG 知识库管理、文件入库状态查看、原始文件预览/下载，以及独立检索调试工作台能力。

### Modified Capabilities
- 无

## Impact

- 影响前端模块 `ui/`：新增 workspace 路由、页面、服务层、类型定义与界面交互。
- 复用 Python 工具服务 `reactor-tool/` 的既有接口：`/v1/documents/*`、`/v1/tool/mragQuery`，首期不要求新增接口。
- 首期不修改 `ai-agent-station-study-domain/` 的聊天主链路和 `multimodalagent_tool` 运行时行为。
- 依赖现有 `REACTOR_TOOL_BASE_URL` 配置与 `reactor-tool` 的 CORS/文件访问能力。
