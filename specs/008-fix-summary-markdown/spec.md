# Feature Specification: 修复 React/PlanSolve 最终总结 Markdown 展示

**Feature Branch**: `[008-fix-summary-markdown]`  
**Created**: 2026-04-23  
**Status**: Draft  
**Input**: User description: "react 和 plansolve 模式的前端最终总结输出的 markdown 总是解析失败，页面会直接展示各种 markdown 语法；需要修复这个问题，使类似“厦门好玩的地方推荐（按类型快速挑）”这类总结内容能够按预期展示为标题、段落和列表。"

## Existing System Context *(mandatory for brownfield features)*

- **Affected Modules**: `ui`
- **Existing Capabilities to Reuse**: 现有 `ui/src/components/Dialogue/index.tsx` 最终总结展示区块、`ui/src/components/ActionPanel/MarkdownRenderer.tsx` 的统一 Markdown 渲染入口、`ui/src/utils/markdown.ts` 的展示前轻量修正规则、`ui/src/services/agentConversation.ts` 的结构化会话历史恢复与最终总结补偿逻辑
- **Out of Scope**: 不改后端 Agent 提示词与输出协议、不新增数据库表或会话字段、不调整 `ai-agent-station-study-domain` / `reactor-tool` / `reactor-client`、不重做普通 `CHAT` 模式的独立回复交互、不要求模型必须产出完全规范的 Markdown 才能展示、不把同一 Markdown 渲染链路下的其他展示场景一并纳入本期修复范围
- **Current Constraints**: 现有最终总结同时存在实时流式展示与历史回放两条路径；结构化会话的最终总结依赖既有 `task_summary` / `agent_stream` / 历史 fallback 契约；修复必须兼容中文内容与模型常见的“近似 Markdown”输出，并继续遵守只在前端展示链路内做最小闭环修复；普通聊天回复、文件预览等非目标场景本期必须保持现有可见行为不变

## Clarifications

### Session 2026-04-23

- Q: 修复范围是否要扩大到同一 Markdown 渲染链路的所有场景？ → A: 严格限定在 `REACT/PLAN_SOLVE` 最终总结区域

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 结构化模式最终总结能正确排版 (Priority: P1)

作为在 `REACT` 或 `PLAN_SOLVE` 模式下查看任务结果的用户，我希望最终总结里的标题、列表、段落能够直接按阅读结构展示，而不是把 `##`、`###`、`-` 这些 Markdown 语法原样显示在页面上。

**Why this priority**: 这是用户当前最直接可见的问题，已经影响最终结果的可读性与交付质量。

**Independent Test**: 发起一个结构化模式请求，让最终总结返回包含中文标题、分段说明、无序列表和“缺少空格的近似 Markdown”内容，验证最终总结区域能展示出正确的标题层级、段落和列表。

**Acceptance Scenarios**:

1. **Given** `REACT` 或 `PLAN_SOLVE` 会话的最终总结包含 `###1）经典必去`、`##你如果想要...`、`-计划玩几天` 这类常见的近似 Markdown 写法，**When** 用户在对话区查看最终总结，**Then** 页面必须按结构化内容展示，而不是把这些标记当成普通文本原样输出
2. **Given** 最终总结中的标题或列表被模型粘在上一句中文句尾后面，**When** 用户查看最终总结，**Then** 页面必须自动按合理段落拆分，让标题与列表独立成块
3. **Given** 最终总结是流式逐段生成的，**When** 内容持续补全直到总结结束，**Then** 用户在整个过程中都应看到稳定、可阅读的结构，而不是最终收尾时突然退化为原始 Markdown 符号

---

### User Story 2 - 历史回放后的最终总结保持相同展示效果 (Priority: P2)

作为会重新打开旧会话的用户，我希望同一条结构化会话在刷新页面、重进会话或查看历史详情后，最终总结仍然保持与实时生成时一致的展示效果。

**Why this priority**: 如果只修实时链路而不修历史恢复链路，用户在刷新或回看后仍会再次看到解析失败的问题。

**Independent Test**: 生成一条包含最终总结的结构化会话，确认实时展示正常后，刷新页面或重新进入历史会话，验证总结内容仍按同样的标题、段落和列表结构展示。

**Acceptance Scenarios**:

1. **Given** 某条结构化会话已经持久化完成，**When** 用户重新进入该会话查看最终总结，**Then** 历史总结的排版结果必须与实时展示保持一致
2. **Given** 历史详情里没有完整的结构化总结事件，只能依赖既有最终总结补偿内容恢复，**When** 用户查看该历史会话，**Then** 系统仍必须按同样规则展示为可读的结构化内容

---

### User Story 3 - 修复展示问题时不破坏合法 Markdown 与代码内容 (Priority: P3)

作为阅读技术型总结内容的用户，我希望系统在修复近似 Markdown 的同时，不要误改已经合法的 Markdown、代码块或普通文本，避免新的展示回归。

**Why this priority**: 该修复会触碰统一的 Markdown 展示前处理逻辑，如果没有边界保护，很容易把已有正常内容也改坏。

**Independent Test**: 使用同时包含合法 Markdown、中文近似 Markdown、代码块和普通文本的总结样本，验证页面既能修复应修复的结构问题，也不会改坏代码块和已合法内容。

**Acceptance Scenarios**:

1. **Given** 最终总结中包含 fenced code block 或内联代码，**When** 页面执行展示前修正，**Then** 代码内容必须保持原样，不得因为标题/列表修复而被改写
2. **Given** 最终总结本身已经是合法 Markdown，**When** 页面展示该内容，**Then** 现有标题、列表、链接和表格结构不得被过度修正或破坏
3. **Given** 最终总结主要是普通段落文本而非 Markdown，**When** 页面展示该内容，**Then** 用户仍应看到稳定、可阅读的段落，而不是出现空白、乱码或错误分块

### Edge Cases

- 模型输出把 `##` / `###` 标题直接贴在中文句尾后面，且标题标记后没有空格
- 模型输出把 `-` 列表项直接写成 `-计划玩几天` 这类缺少空格的写法
- 同一段文本中既有中文说明，又有被粘连的标题、列表与问句提示，需要分段但不能把普通文本切碎
- 总结内容包含代码块时，代码块里的 `#`、`-`、数字列表等字符不能被误判为要修复的正文 Markdown
- 结构化会话历史没有完整 `task_summary` 事件，只能使用既有最终总结 fallback 时，也必须得到同样的展示结果

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 让 `REACT` 与 `PLAN_SOLVE` 模式的最终总结在实时展示时以可读的标题、段落和列表结构呈现，而不是直接暴露原始 Markdown 标记
- **FR-002**: 系统 MUST 容忍模型常见的近似 Markdown 输出，至少覆盖以下情况：标题标记后缺少空格、列表标记后缺少空格、标题或列表被粘连在上一句句尾后面
- **FR-003**: 系统 MUST 对结构化模式最终总结使用统一的展示前规范化规则，确保流式总结、非流式总结和总结完成态看到的是同一套结构结果
- **FR-004**: 系统 MUST 在历史会话恢复与最终总结补偿链路中复用同样的展示规则，确保重进会话后的最终总结效果与实时展示一致
- **FR-005**: 系统 MUST 保证代码块、内联代码和已经合法的 Markdown 结构不会因为近似 Markdown 修复而被改写或破坏
- **FR-006**: 系统 MUST 将该修复限定在 `REACT` 与 `PLAN_SOLVE` 的最终总结展示入口内生效；如需复用共享 Markdown 展示前处理能力，MUST 通过显式边界控制保证其他展示场景不会被默认纳入本期修复
- **FR-007**: 系统 MUST 保持现有 `task_summary`、`agent_stream` 与历史会话恢复契约兼容，不要求后端协议、数据库或模型提示词做同步调整
- **FR-008**: 当最终总结仍不足以形成完整 Markdown 结构时，系统 MUST 优先保证内容可读性，避免出现空白区域、整段消失或明显错乱的分块
- **FR-009**: 系统 MUST 明确该修复范围只针对结构化模式最终总结展示问题，普通 `CHAT` 模式与其他文件/工具预览能力不得因此产生可见回归

### Key Entities *(include if feature involves data)*

- **Final Summary Content**: 结构化模式任务完成后展示给用户的最终总结文本，可能来自实时流式结果、最终结果包或历史 fallback
- **Summary Display Normalization**: 前端在渲染最终总结前执行的展示修正规则，用于把近似 Markdown 调整为稳定可读的结构
- **Structured Conversation Turn**: `REACT` 或 `PLAN_SOLVE` 产生的一轮结构化对话，包含思考、任务执行和最终总结展示链路
- **History Summary Fallback**: 历史详情回放时从既有最终结果补偿出的总结内容，需要与实时总结共享同样的展示语义

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 在覆盖标题缺少空格、列表缺少空格、标题/列表粘连句尾的验收样本中，100% 的 `REACT` 与 `PLAN_SOLVE` 最终总结都能展示为结构化内容，而不是直接暴露原始 Markdown 标记
- **SC-002**: 在实时生成后刷新页面或重新进入历史会话的验收场景中，100% 的最终总结展示结果与实时展示保持一致
- **SC-003**: 在包含代码块、合法 Markdown 和普通段落的混合验收样本中，0 个样本出现代码块被改写、合法结构被破坏或内容消失的回归
- **SC-004**: 对用户提供的“厦门好玩的地方推荐（按类型快速挑）”类型样本，用户能够直接按标题和列表浏览信息，不再需要人工辨认 `##`、`###`、`-` 等原始语法符号

## Assumptions

- 本期问题根因位于前端最终总结展示链路，修复范围严格限定在结构化模式最终总结区域；若内部复用现有展示前处理与渲染能力，也必须加显式边界，避免影响其他 Markdown 展示场景
- `REACT` 与 `PLAN_SOLVE` 的最终总结都会继续复用现有结构化会话时间线与最终总结展示入口，因此可以通过共享规则实现一致修复
- 历史回放中由最终结果补偿出的总结内容，业务上应与实时最终总结视为同一种展示对象
- 本期成功标准聚焦“用户看到的最终总结是否可读”，不包含对 Agent 总结内容质量本身的优化
