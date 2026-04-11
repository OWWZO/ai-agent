# Phase 0 Research: Fix 模式 AI 角色库

## 决策 1：角色库继续以 `ai_agent` 为唯一主数据，新增 Fix 角色投影查询

- **Decision**: 不新增角色表/角色标记表；在 repository 层新增“Fix 角色库”专用查询，基于 `ai_agent`、`ai_agent_flow_config` 以及必要的客户端可用性信息生成只读角色列表。
- **Why**:
  - 用户明确要求复用 `ai_agent` 表和现有 Fix 模式逻辑，避免维护平行角色体系。
  - 现有 `/query_available_agents` 只按 `ai_agent.status=1` 过滤，粒度过粗，无法保证 chat 角色库里“能选就能聊”。
  - 专用查询可以一次性给出前端需要的角色基础信息和稳定排序依据，避免 Controller 层做零散过滤。
- **Eligibility Rule**:
  - `ai_agent.status = 1`
  - `ai_agent.strategy = 'fixedAgentExecuteStrategy'`
  - 至少存在 1 条 `ai_agent_flow_config`
  - FlowConfig 关联客户端至少能被解析为启用客户端；真正发送前仍再做一次运行时校验，防止配置漂移
- **Alternatives Rejected**:
  - **独立角色表/角色库表**：与 `ai_agent` 形成双写和双维护，不符合“复用现有结构”的目标。
  - **继续复用 `queryAvailableAgents()` 再在 trigger/ui 侧过滤**：业务规则分散，且难以统一默认排序和可用性校验。
  - **列表完全不过滤，只在发送时报错**：用户会频繁遇到“能选但不能聊”的伪角色，违背 SC-003。

## 决策 2：默认角色通过应用配置显式声明，查询结果只负责回退排序

- **Decision**: 新增可选配置 `spring.ai.agent.chat.default-role-id`；角色库服务优先尝试该角色作为默认角色，若缺失或失效，则回退到当前可用角色库中的首个角色（按 `create_time ASC, id ASC` 排序）。
- **Why**:
  - 当前 `FixedAgentExecuteStrategy` 写死 `"1"` 是历史遗留，数据库快照显示默认 chat 角色来源已经演进，继续硬编码不可维护。
  - 用配置显式声明“现有 chat 角色”，无需给 `ai_agent` 再加默认标记字段，也能适应不同环境。
  - 回退排序保留了“就算配置暂未同步，chat 仍能工作”的安全网。
- **Alternatives Rejected**:
  - **继续在代码里写死 agentId**：高耦合、难运维、也无法解释不同环境的默认角色差异。
  - **在 `ai_agent` 新增 `is_default_role` 字段**：引入新的持久化语义，超出本期最小必要改动。
  - **完全依赖最早创建角色/最小 ID 自动推断**：规则不够显式，后续数据调整后容易失真。

## 决策 3：角色绑定落在 `ai_agent_conversation`，并保留名称快照

- **Decision**: 在 `ai_agent_conversation` 新增 `ai_agent_id` 与 `ai_agent_name_snapshot`。新建 chat 会话时必须绑定角色；非 chat 会话保持为空；历史 chat 会话允许为空并按默认角色回退。
- **Why**:
  - 需求明确要求“会话级别持久化角色绑定”，且同一会话内禁止切换角色。
  - 只存 `ai_agent_id` 时，一旦角色被改名/停用/删除，历史会话展示会变得不稳定；名称快照可以保证历史可读。
  - 直接挂在会话表上最简单，不需要额外的映射表和多表事务。
- **Alternatives Rejected**:
  - **消息级角色绑定**：重复存储、没有必要，也和“一个会话只绑定一个角色”的约束相冲突。
  - **单独的 conversation-role mapping 表**：结构更复杂，但不带来本期真实收益。
  - **只存 ID 不存快照**：历史展示对在线数据过于敏感，不利于“角色下线但历史仍可读”。

## 决策 4：会话创建和首发消息都可承接角色信息，`send-stream` 负责最终兜底

- **Decision**: `ConversationCreateReqVO` 和 `MessageSendReqVO` 都接受可选 `aiAgentId`。`AgentStreamPersistServiceImpl.sendAndPersist(...)` 按以下顺序解析角色：
  1. 已存在会话的绑定角色
  2. 本次请求里的 `aiAgentId`（仅在会话不存在，或旧会话需要懒补齐时使用）
  3. 默认角色
  当请求里的 `aiAgentId` 与已有会话绑定角色不一致时，返回“需新建会话后再切换角色”的业务错误。
- **Why**:
  - 当前前端存在“用户先发第一条消息，再由后端自动创建会话”的链路，不能假设所有 chat 都会先显式调用 create。
  - 双入口承接角色信息，可以让欢迎页、历史会话恢复、首发聊天兜底共用一套规则，不会把“先 create 再 send”变成隐藏前置条件。
- **Alternatives Rejected**:
  - **只允许 create 时传角色**：会导致当前首发发送链路断裂。
  - **会话已存在时静默覆盖为新角色**：会污染已有上下文，违背“不允许会话内切换角色”。

## 决策 5：角色选择组件复用在欢迎页和 chat 会话输入区，切换角色自动进入新会话

- **Decision**: 前端抽一个可复用的 `ChatRoleSelector` 组件，只在 chat 模式显示。空白草稿会话内改选角色时直接更新当前草稿；已有消息的 chat 会话中改选角色时，自动新建并切换到一个绑定新角色的草稿会话，原会话保持不变。
- **Why**:
  - 这既满足“右侧有角色库按钮”的产品诉求，也遵守“不在原会话里换角色”的约束。
  - 组件复用能覆盖欢迎页输入框和会话页输入区，避免角色按钮只出现在一个入口导致体验割裂。
- **Alternatives Rejected**:
  - **只在欢迎页展示角色库按钮**：用户进入历史会话后无法确认当前角色，也找不到平滑切换入口。
  - **会话开始后彻底禁用角色按钮**：虽然简单，但体验偏硬，切换角色还得用户自己先回退再新建。

## 决策 6：角色失效时历史可读、继续对话受限，错误通过 SSE 终态包统一返回

- **Decision**: 会话列表/详情继续返回角色名称快照，同时附带 `available=false`；`send-stream` 在进入 Fix 执行前先校验角色是否仍可用。若不可用，则返回兼容 `GptProcessResult` 的终态错误包，并停止真正的 Fix 执行。
- **Why**:
  - 角色失效是可预期的运营场景，不能让错误直接落到 `ApplicationContext.getBean()` 或上游 LLM 调用阶段才爆炸。
  - SSE 终态包复用现有流式通道，前端无需额外切换到另一套错误协议。
- **Alternatives Rejected**:
  - **只抛异常并关闭连接**：用户侧反馈不清晰，且不利于前端做稳定提示。
  - **历史会话直接隐藏失效角色**：会破坏已存在的对话历史可读性。
