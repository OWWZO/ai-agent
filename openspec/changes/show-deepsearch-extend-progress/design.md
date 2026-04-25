## Context

`deep_search` 现有链路已经具备完整的阶段事件：

- Python `reactor-tool/reactor_tool/tool/deepsearch.py` 在 `query_decompose()` 完成后先流式输出 `messageType=extend`，其中 `searchResult.query` 已包含拆解后的子查询，`docs` 仍为空。
- Java `DeepSearchTool` 会透传 `extend/search/report`，并为 `extend` 标记 `searchFinish=false`，为 `search` 标记 `searchFinish=true`。
- 前端 `agentConversation.ts` 与历史回放归一化逻辑已经能识别 `extend/search/report`。

当前体验问题主要出在前端任务聚合层：`ui/src/utils/chat.ts` 在构建任务列表时显式把 `deep_search + extend` 排除在侧边任务列表之外，导致用户只有在 `search` 阶段落地文档后才第一次看到深度搜索组件。也就是说，协议与持久化侧基本具备能力，缺的是一个一致的展示约定。

该变更影响 React 与 PlanSolve 两种模式，因为两者都走共享的会话消息归一化、任务列表构建和 `Dialogue/ActionView` 渲染链路。

## Goals / Non-Goals

**Goals:**

- 让前端在收到 `deep_search.extend` 后立即展示“正在搜索”状态，而不是等待真实检索完成。
- 在搜索中组件里展示查询分解后的子查询内容，即使当前 `docs` 仍为空。
- 统一 `extend -> search -> report` 的渲染切换规则，确保实时流式与历史回放表现一致。
- 将改动收敛在现有前端消息处理与展示链路内，尽量不触碰 Python/Java 协议。

**Non-Goals:**

- 不修改 `deepsearch.py` 的查询分解、搜索引擎调用或总结算法。
- 不新增 SSE 字段、数据库表结构或新的消息类型。
- 不重新设计深度搜索的视觉样式体系，只复用现有任务卡片/详情面板能力。
- 不扩展到其他工具类型的阶段化渲染，本次仅覆盖 `deep_search`。

## Decisions

### Decision 1: 以现有 `extend` 事件作为唯一“搜索开始”信号

采用方案：

- 前端收到 `messageType=extend` 时，直接把该 deep_search 任务纳入任务列表与详情面板。
- 不在前端自行猜测“已经开始搜索”，也不通过本地定时器制造占位状态。

原因：

- Python 与 Java 已经输出稳定的阶段信号，复用协议比新增前端推断逻辑更可靠。
- 这样可以保证实时流式、事件持久化、历史回放使用同一套阶段语义。

备选方案：

- 方案 A：继续等待 `search` 阶段后再显示。问题是用户在查询分解到真实检索之间仍无反馈，无法解决核心体验问题。
- 方案 B：前端在侦测到调用 `deep_search` 后立即生成本地 loading 占位。问题是占位内容无法准确展示子查询，也容易与真实流式事件脱节。

### Decision 2: 复用现有按 query 拆分的渲染结构，不新增专门的“分解结果组件”

采用方案：

- 继续使用 `processDeepSearchTask()` 已有的按 `searchResult.query` 拆分子项的逻辑。
- `extend` 阶段使用空 `docs` + 查询文本渲染“正在搜索”的条目，`search` 阶段在同一结构上补齐搜索结果，`report` 阶段切回总结视图。

原因：

- 现有前端已经具备按 query 渲染 deep_search 条目的基础能力，只是 `extend` 被过滤掉了。
- 复用同一组件链可以降低样式与状态分叉，避免新组件带来重复维护成本。

备选方案：

- 方案 A：新增专门的“查询分解卡片”组件。问题是会引入额外分支和重复渲染逻辑，收益有限。
- 方案 B：把所有子查询拼成一条纯文本。问题是丢失当前按 query 组织结果的结构，不利于后续从 `extend` 平滑过渡到 `search`。

### Decision 3: 任务列表允许 `extend` 可见，但仍保持单条 deep_search 工具流的稳定更新

采用方案：

- 移除任务列表构建中对 `deep_search.extend` 的排除逻辑。
- 仍然基于现有 task/tool 定位规则更新同一条 deep_search 工具记录，避免 `extend`、`search`、`report` 产生重复任务容器。

原因：

- 用户需要更早看到任务，但不需要因为阶段推进而看到重复条目。
- 现有 `handleDeepSearchMessage()` 已支持更新已有工具记录，适合承接该变更。

备选方案：

- 方案 A：每个阶段都单独落一条任务。问题是会让侧边栏迅速膨胀，也会弱化“这是同一次 deep_search”的语义。

### Decision 4: 历史回放与实时流式共享同一阶段归一化规则

采用方案：

- 保持 `agentConversation.ts` 的 `resolveDeepSearchStage()` 作为统一阶段判定入口。
- 为 `extend` 的回放展示补齐回归验证，确保旧会话重新加载时也能恢复“正在搜索”状态与查询文本。

原因：

- 该逻辑已经同时处理 `resultMap.messageType` 与 `eventSubType`，适合作为唯一事实来源。
- 如果只修实时流式，不修回放，用户刷新页面后仍会看到不一致体验。

备选方案：

- 方案 A：只改实时流式，不处理历史回放。问题是状态一致性不足，且难以排查线上问题。

## Risks / Trade-offs

- [多轮 deep_search 产生较多“正在搜索”子项] → 继续沿用现有同 task 更新机制，并通过回归验证确认不会因阶段切换插入重复占位任务。
- [extend 阶段 docs 为空导致详情面板表现不完整] → 明确空 docs 也是合法中间态，标题与动作文案优先基于 query 和阶段生成。
- [历史事件数据存在 messageType/eventSubType 不一致] → 继续通过统一归一化入口兜底，并补充 fixture 验证回放结果。
- [只做前端展示修复，未改变搜索耗时本身] → 在 proposal 与 spec 中明确本次目标是“更早反馈”，不是“缩短 deep_search 执行时间”。

## Migration Plan

1. 调整前端 deep_search 任务列表聚合逻辑，让 `extend` 进入可见任务流。
2. 复查 `Dialogue`、`ActionView` 与详情面板对 `extend` 空 docs 的渲染，确保动效、标题、查询文本一致。
3. 补充事件归一化或渲染层测试/fixture，覆盖实时 `extend -> search -> report` 与历史回放场景。
4. 前端构建与相关测试通过后发布；该变更为纯增量展示修复，无数据迁移步骤。
5. 如需回滚，仅恢复前端任务列表对 `extend` 的过滤逻辑，不影响后端协议与历史数据。

## Open Questions

- “正在搜索”阶段在侧边任务列表中应按每个子查询拆成多条，还是在详情面板中聚合展示为一组；本次设计默认复用现有按 query 拆分的渲染方式。
- 是否需要为 `extend` 阶段补充更明确的空结果文案（例如“正在检索网页，稍后展示结果”）；若现有视觉反馈已足够，可不新增文案资产。
