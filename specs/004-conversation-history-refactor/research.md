# Research: 对话历史持久化精简重构

## Decision 1: 事件表作为唯一历史回放权威源

- **Decision**: `PLAN_SOLVE` 与 `REACT` 的历史详情统一从 `ai_agent_message_event` 装配，`ai_agent_message` 不再承担 rich replay 存储。
- **Rationale**: 当前 `thought/plan_json/tasks_json/multi_agent_json/conclusion_json/render_snapshot_json` 与事件表存在明显语义重叠，继续并存会让每次新增展示节点都需要多处改表和回填。
- **Alternatives considered**:
  - 保留 `render_snapshot_json` 作为主读取源：读取快，但仍是重复载荷，无法解决字段持续膨胀问题。
  - 保留“双写但规定优先级”：迁移风险低，但长期复杂度不降。

## Decision 2: 消息表退化为单轮请求账本，而不是会话细节载体

- **Decision**: `ai_agent_message` 只保留 request 级字段、执行状态、附件和必要派生文本；删除 `thought/plan/tasks/render snapshot` 等会话细节字段。
- **Rationale**: 用户要求消息表只存单条请求相关信息；同时 `CHAT` 上下文窗口仍需要单轮最终回答文本，因此保留 `response` 作为派生上下文字段是最小兼容方案。
- **Alternatives considered**:
  - 完全清空消息结果字段：最纯粹，但会破坏 chat 上下文窗口和摘要逻辑。
  - 继续保留部分 rich JSON：实现更快，但无法真正去重。

## Decision 3: 大体量工作区内容通过稳定资源引用持久化

- **Decision**: 搜索总结、报告和工作区大文本内容不直接存入事件正文，而是通过稳定持久化资源引用（现有文件服务 URL / key）在事件 payload 中表达。
- **Rationale**: 这样既避免事件表体积持续膨胀，也满足“新增展示类型不改表结构”的目标。
- **Alternatives considered**:
  - 直接把完整总结正文塞进事件表：实现简单，但后续扩展和存储成本差。
  - 只存工作区本地路径：对历史回放不稳定，临时目录清理后即失效。

## Decision 4: 复用现有 `fileInfo` / 文件服务能力，不新造 artifact 服务

- **Decision**: artifact 引用格式优先复用当前 `file_tool`、`code_interpreter`、`report_tool` 产物里已经使用的稳定文件 URL 返回结构。
- **Rationale**: 项目里已经有可上传并返回稳定 `ossUrl/domainUrl/fileSize` 的链路，继续复用最稳，也符合“优先复用生态和现有能力”的约束。
- **Alternatives considered**:
  - 新增独立 artifact 表和解析服务：演进空间大，但超出本期范围。
  - 只在事件 payload 中存裸路径字符串：可维护性差，缺乏统一元数据。

## Decision 5: 前端状态拆分为“服务端摘要 + 详情缓存 + 草稿缓存”

- **Decision**: 前端以服务端会话列表为唯一持久化真相源；选中会话后单独加载详情缓存；未发送草稿和流式中消息保持本地态。
- **Rationale**: 当前 `remoteConversations` 与 `conversations` 两套元数据同时存在，导致合并规则、草稿保留和详情懒加载逻辑明显过重。
- **Alternatives considered**:
  - 继续沿用双状态合并：变更成本低，但无法根治复杂度。
  - 所有状态都只放本地：与“服务端历史为真相源”的方向冲突。

## Decision 6: 会话列表与详情接口分层收敛

- **Decision**: 列表接口只返回摘要，详情接口只返回 `turns + events` 以及渲染真正需要的字段；同时统一补齐 device/user scope 校验。
- **Rationale**: 现有列表/详情契约一边带了过多字段，一边又没有在 `list/detail` 中一致使用 `deviceId` 做过滤，既臃肿也不严谨。
- **Alternatives considered**:
  - 保留原 `messages[] + rich JSON` 详情结构：前端改动少，但会把旧模型继续带下去。
  - 每次切换会话都拉全量列表+详情：实现简单，但流量和状态复杂度都更高。

## Decision 7: 旧历史数据不迁移，直接清理

- **Decision**: 改造前历史数据不纳入兼容范围，切换前允许直接删除旧历史。
- **Rationale**: 用户已明确旧数据不重要；因此没必要为一次性遗留数据引入双读兼容、回填脚本和复杂的回滚设计。
- **Alternatives considered**:
  - 渐进双读回填：上线更稳，但会显著拉高设计和实现复杂度。
  - 一次性迁移旧数据：结果最干净，但对当前需求没有收益。
