# Feature Specification: Tool Output Refactor

**Feature Branch**: `[014-tool-output-refactor]`  
**Created**: 2026-05-01  
**Status**: Draft  
**Input**: User description: "按现在的计划来：工具输出重构为独立表，移除主账本 output_json，rich tool 改为强类型输出模型，projector 只读新表。"

## Existing System Context *(mandatory for brownfield features)*

- **Affected Modules**: `ai-agent-station-study-domain`、`ai-agent-station-study-infrastructure`、`ai-agent-station-study-app`
- **Existing Capabilities to Reuse**: 现有执行账本记录链路、工具调用回放投影链路、产物账本与文件引用合并逻辑、rich tool 已有的人类可读 observation 输出、会话执行详情查询入口
- **Out of Scope**: `ui/` 的新交互设计、`reactor-tool/` 与 `reactor-client/` 的外部服务实现、八类 rich tool 之外的通用工具历史重构、旧历史数据兼容回放
- **Current Constraints**: 必须保持 DDD 分层边界清晰；主账本只保留调用事实与终态信息；rich tool 回放不得再依赖主账本中的结构化 JSON；`deep_search` 必须保留阶段级回放能力；直接工具调用需要在没有主账本关联时仍可检索

## Clarifications

### Session 2026-05-01

- Q: 同一个工具调用收到重复终态写入时采用什么落账策略？ → A: 首次终态写入生效，后续重复终态写入忽略并记录冲突
- Q: `deep_search` 提前结束时，阶段轨迹如何落账？ → A: `stages_json` 只保存已实际完成且有内容的阶段，未发生的后续阶段不写入

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 独立记录工具输出 (Priority: P1)

作为平台维护者，我希望八类 rich tool 的结构化结果独立记录，而不是混在主调用记录里，这样我可以稳定追踪每次工具执行的业务输出，并避免主账本继续承担结构化结果存储职责。

**Why this priority**: 这是整次重构的核心价值；如果独立记录能力不成立，后续查询、回放、直接调用检索都无法成立。

**Independent Test**: 触发任一受支持 rich tool 执行一次成功场景与一次失败场景，验证主调用记录仍可查询终态信息，同时结构化输出可通过独立记录单独检索。

**Acceptance Scenarios**:

1. **Given** 一次受支持 rich tool 的执行已成功结束，**When** 系统持久化执行结果，**Then** 主调用记录只保留调用元数据、终态状态、可读 observation 与错误信息，且存在一条与该调用关联的结构化工具输出记录
2. **Given** 一次受支持 rich tool 的执行以失败或超时结束，**When** 系统持久化执行结果，**Then** 主调用记录与结构化工具输出记录都反映一致的终态状态与错误信息

---

### User Story 2 - 仅基于新输出记录回放历史 (Priority: P2)

作为查看会话历史的使用者，我希望 rich tool 的历史回放完全依赖新的结构化输出记录，这样在主账本不再保存结构化 JSON 后，历史展示仍然完整、可理解且可验证。

**Why this priority**: 独立记录之后，历史回放是最直接的消费方；如果回放链路不能脱离旧字段，重构就不算闭环。

**Independent Test**: 构造包含多种 rich tool 的历史执行明细，验证历史回放可以正确生成对应的用户可见事件，且不读取主调用记录中的结构化结果字段。

**Acceptance Scenarios**:

1. **Given** 一次历史执行包含文件、报告、数据分析、图像生成等 rich tool 输出，**When** 使用历史回放入口重建事件流，**Then** 每个工具事件都仅基于独立输出记录与产物引用被正确还原
2. **Given** 一次 `deep_search` 历史执行包含查询拆解、搜索结果与最终回答，**When** 使用历史回放入口重建事件流，**Then** 系统按原顺序还原 `extend`、`search`、`report` 三类阶段事件
3. **Given** 一次 `deep_search` 在部分阶段完成后失败或被中断，**When** 使用历史回放入口重建事件流，**Then** 系统只回放已实际完成的阶段，并结合终态状态表达后续阶段未执行

---

### User Story 3 - 支持直接工具调用检索 (Priority: P3)

作为集成平台能力的维护者，我希望即使某次工具调用不经过主智能体调度链路，也能通过稳定标识检索该次 rich tool 的结构化输出，这样直接工具调用场景不会因为缺少主账本关联而丢失结果可见性。

**Why this priority**: 这决定了本次重构是否真正把 rich tool 输出从“依附主账本”改为“独立能力”。

**Independent Test**: 模拟一次没有主调用关联的直接工具调用，验证结构化输出可以通过请求标识与工具调用标识被检索并用于结果展示。

**Acceptance Scenarios**:

1. **Given** 一次直接工具调用没有主调用记录关联，**When** 该调用写入结构化输出结果，**Then** 平台仍可通过请求标识与工具调用标识定位该结果
2. **Given** 同一次直接工具调用收到重复终态写入，**When** 平台再次落账，**Then** 调用结果仍表现为同一条终态输出记录，而不是生成重复结果

---

### Edge Cases

- 当 `deep_search` 只走到部分阶段就结束时，系统必须只保留已实际完成且有内容的阶段，未执行的后续阶段不得写入占位数据，并由终态状态表达提前结束
- 当 rich tool 没有产出文件时，系统如何保持结构化结果完整且不制造空引用歧义？
- 当同一个调用收到重复的终态写入通知时，系统必须以首次终态写入为准，忽略后续重复写入，并记录冲突供排查与观测使用
- 当失败场景只返回可读错误信息、没有完整结构化业务内容时，系统如何保证历史回放仍可解释该次失败？

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST 为八类受支持的 rich tool 分别维护独立的结构化输出记录，并将其与主调用记录分离存储
- **FR-002**: System MUST 停止在主调用记录中保存 rich tool 的结构化输出结果
- **FR-003**: System MUST 让主调用记录只承担调用元数据、终态状态、可读 observation 与错误信息的记录职责
- **FR-004**: System MUST 为每次受支持 rich tool 的终态执行结果保留且仅保留一条可检索的结构化输出记录
- **FR-005**: System MUST 支持通过主调用标识检索 agent 驱动场景下的结构化工具输出
- **FR-006**: System MUST 支持通过请求标识与工具调用标识检索直接工具调用场景下的结构化工具输出
- **FR-007**: System MUST 仅基于独立结构化输出记录与关联产物信息重建八类 rich tool 的历史回放结果
- **FR-008**: System MUST 保留 `deep_search` 的阶段轨迹，使历史回放能够按顺序重建查询拆解、搜索结果与最终回答；当执行提前结束时，只允许保存已实际完成且有内容的阶段
- **FR-009**: System MUST 在结构化输出记录中保留各工具最关键的业务字段，使查询方无需检查原始 JSON 即可理解该次工具结果
- **FR-010**: System MUST 在主调用记录与结构化输出记录之间保持一致的终态语义，尤其是失败与超时结果
- **FR-011**: System MUST 明确由既有执行账本写入链路、结构化工具输出写入入口与历史回放投影入口分别承接本能力，而不是引入并行运行路径
- **FR-012**: System MUST 更新所有依赖主调用结构化输出的查询与回放契约，使其改为消费独立结构化输出模型
- **FR-013**: System MUST 将本次重构的影响范围限定在八类受支持 rich tool 及其相关账本、查询、回放链路中，不扩散到通用纯文本工具的结果模型
- **FR-014**: System MUST 对同一工具调用的重复终态写入采用“首次终态写入生效”策略，后续重复终态写入不得覆盖既有结果，并必须留下可观测的冲突记录

### Key Entities *(include if feature involves data)*

- **Tool Invocation Record**: 表示一次工具调用的主追踪记录，包含调用身份、请求关联、终态状态、可读 observation 与错误信息
- **Structured Tool Output Record**: 表示某次 rich tool 终态业务结果的独立记录，包含公共关联标识与工具特有业务字段
- **Deep Search Stage Trace**: 表示一次深度搜索执行中的有序阶段轨迹，仅包含已实际完成且有内容的阶段，用于回放查询拆解、搜索过程与最终回答
- **Direct Tool Call Reference**: 表示不依赖主调用记录的直接工具调用定位信息，由请求标识与工具调用标识组成
- **Tool File Reference**: 表示工具结果附带的文件引用信息，用于历史展示与下载预览定位

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 在回归验证中，八类受支持 rich tool 的已完成执行有 100% 能生成一条可检索的独立结构化输出记录
- **SC-002**: 在回归验证中，八类受支持 rich tool 的历史回放场景有 100% 能在不依赖主调用结构化结果的前提下生成预期的用户可见事件
- **SC-003**: 在覆盖的失败与超时场景中，100% 的执行详情查询与历史回放都能展示一致的终态状态与可读错误信息
- **SC-004**: 在覆盖的直接工具调用场景中，100% 的结构化工具结果都能通过请求标识与工具调用标识被成功检索
- **SC-005**: 在覆盖的 `deep_search` 回放场景中，100% 的阶段事件都能按原始顺序被恢复，并保留阶段类型对应的关键信息

## Assumptions

- 本次需求聚焦后端执行账本、结构化结果存储与历史回放闭环，不新增前端产品级交互范围
- 现有产物账本与稳定文件引用能力继续作为文件下载、预览与展示的权威来源
- 只有八类既定 rich tool 被纳入本次重构，其他工具继续沿用通用文本结果展示方式
- 本次重构不承担旧历史数据的兼容回放责任，新的结构化输出模型只要求服务重构后的主路径
- 直接工具调用场景能够提供稳定的请求标识与工具调用标识，作为独立结构化输出检索键
- 现有会话执行详情查询入口与历史回放入口将继续作为本功能的对外消费面，而不是新增并行查询入口
