# Feature Specification: Agent 领域边界最终收敛

**Feature Branch**: `[019-agent-ddd-convergence]`  
**Created**: 2026-05-04  
**Status**: Draft  
**Input**: User description: "`docs/superpowers/plans/2026-05-04-agent-service-reactor-ddd-convergence-remaining-plan.md`"

## Existing System Context *(mandatory for brownfield features)*

- **Affected Modules**: `ai-agent-station-study-case`、`ai-agent-station-study-domain`、`ai-agent-station-study-infrastructure`、`ai-agent-station-study-trigger`、`ai-agent-station-study-app`
- **Existing Capabilities to Reuse**: 已落地的 `case` 应用编排层骨架、现有会话流抽象与 SSE 触发层适配、当前 Agent 执行主链路、execution ledger 与 history replay 能力、session memory 能力、现有边界回归测试与模块级 DDD 文档
- **Out of Scope**: 不改 `ui/`、`reactor-tool/`、`reactor-client/`、数据库表结构与历史数据模型，不新增终端用户功能，不顺手重命名与本次边界收敛无关的业务语义名
- **Current Constraints**: 必须保持依赖方向为 `Trigger -> Case -> Domain <- Infrastructure`，`app` 仅负责装配；`SseEmitter` 只能停留在触发层协议适配；`domain` 只能保留领域模型、领域服务、仓储契约与外部能力端口；Phase 1 已落地的 `case` 切分、会话回放、tool-output 持久化和 session memory 主链路必须继续可用

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 开发者可在清晰边界内扩展 Agent 能力 (Priority: P1)

作为维护 Agent 主链路的后端开发者，我希望调度编排、流式协议和技术执行细节都回到正确层级，这样我在扩展 Agent 能力时只需要在明确归属的位置修改代码，而不用在领域层里同时处理 SSE、HTTP、JDBC 和 Spring 运行时耦合。

**Why this priority**: 这是本次收敛的核心价值。只要主链路仍然把应用编排和技术细节混在 `domain`，后续任何能力演进都会继续扩大耦合面。

**Independent Test**: 检查主链路代码路径，并执行边界回归与源码扫描；确认 dispatch、execute、armory、task 从统一应用层进入，领域层不再直接依赖协议或技术执行细节。

**Acceptance Scenarios**:

1. **Given** 开发者需要定位一次 Agent 请求从入口到执行的主链路，**When** 查看代码归属，**Then** 调度与编排入口必须统一位于应用层，而不是分散在旧的领域服务目录中
2. **Given** 对领域层执行边界审计，**When** 收敛完成，**Then** 领域层必须不再直接承担流式协议生命周期、远程技术客户端创建或 Spring 运行时查找职责
3. **Given** 某项既有 Agent 执行能力继续被主链路使用，**When** 本次收敛上线，**Then** 该能力必须仍能通过现有入口工作，而不是因边界迁移被迫改走新的终端用户流程

---

### User Story 2 - 维护者可按子域理解和演进 Agent 核心能力 (Priority: P2)

作为长期维护该代码库的工程师，我希望原先混杂在 `reactor` 总包里的运行时、账本、会话记忆、RAG 和角色修复能力被收敛到清晰的子域边界中，这样我可以在修改某一类能力时，不再跨越大量无关目录和职责。

**Why this priority**: 如果 `reactor` 继续作为“总包垃圾桶”存在，后续任何功能修改都会重新制造跨域依赖和隐藏耦合，Phase 1 的层次切分也无法真正稳定下来。

**Independent Test**: 对 Agent 有界上下文做目录审计和主链路回归，确认核心能力能被映射到明确子域，并且历史回放、session memory、RAG 与角色修复等现有能力继续可定位、可运行。

**Acceptance Scenarios**:

1. **Given** 维护者需要定位运行时、账本、记忆、RAG 或角色修复能力，**When** 查看领域结构，**Then** 每类能力都必须有唯一且明确的主归属，而不是继续依赖 `reactor` 总包兜底
2. **Given** 历史回放、tool-output 恢复或 session memory 重建仍是现网主链路的一部分，**When** 进行边界迁移，**Then** 这些能力必须继续通过清晰的子域职责提供服务，而不是被重新塞回技术适配层
3. **Given** 某项旧目录中的类已经没有真实入口，**When** 收敛完成，**Then** 系统必须移除这类无入口兼容代码，而不是默认长期保留

---

### User Story 3 - 交付团队可用守卫与文档锁定最终边界 (Priority: P3)

作为负责收尾和后续治理的交付团队成员，我希望系统能够通过自动化边界守卫和更新后的模块文档阻止旧依赖回流，这样本次收敛完成后不会在后续需求中再次把 SSE、JDBC 或 Spring 运行时耦合带回领域层。

**Why this priority**: 架构收敛如果没有持续守卫，很快会被新需求绕回旧路径，导致这轮清理失去长期价值。

**Independent Test**: 运行边界守卫测试、目录扫描和聚焦回归，并核对根级与模块级文档，确认任何旧目录、旧根接口或禁止依赖一旦回流就能被发现。

**Acceptance Scenarios**:

1. **Given** 有人试图重新把旧目录、旧根接口或禁止依赖引回主链路，**When** 执行边界守卫测试与目录扫描，**Then** 系统必须能明确报告该回流并阻止收敛被视为完成
2. **Given** 新成员需要理解最终分层边界，**When** 阅读根级与模块级文档，**Then** 文档必须能直接解释各模块职责、依赖方向和有界上下文归属，而不需要回看历史计划
3. **Given** 某段迁移期间必须存在过渡桥接代码，**When** 交付团队检查最终状态，**Then** 这些桥接代码必须带有明确的依赖方和删除时机，否则不得作为最终状态保留

### Edge Cases

- 某些旧类可能仍能被编译引用，但已经没有真实入口，系统必须把“可编译”与“仍应保留”区分开
- 子域迁移过程中，历史回放、session memory 和 tool-output 恢复等主链路能力不能因包结构调整而丢失行为一致性
- 某些技术能力可能短期需要桥接适配，但桥接代码不能成为新的长期主路径
- 触发层、应用层和基础设施层可能需要最小适配来承接迁出的职责，但不能借此把业务判断重新塞回错误层级
- 最终收敛必须同时满足目录边界、依赖边界、文档边界和回归验证，不能只完成其中一项就宣布结束

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST 将 Agent 的 dispatch、execute、armory、task 应用编排职责统一收敛到应用层，作为主链路唯一的编排入口
- **FR-002**: System MUST 从旧的领域服务目录中移除应用编排职责；若个别能力仍需暂存，必须改造成明确的领域契约，而不是继续作为主路径实现
- **FR-003**: System MUST 将历史 `reactor` 总包拆分为 runtime、ledger、memory、rag、role 五类明确子域，并为每类能力建立唯一主归属
- **FR-004**: System MUST 保持 `domain` 只承载领域模型、领域服务、仓储契约与外部能力端口，不再直接承载协议适配、技术执行器或运行时装配职责
- **FR-005**: System MUST 将会话流式输出的协议生命周期、连接关闭、错误收口和协议适配限制在触发层或应用层，并让领域层输出契约保持协议无关
- **FR-006**: System MUST 将远程调用、数据查询执行、文件与工具运行时交互、模型调用网关等技术执行细节迁到基础设施或装配层，而不是在领域代码中直接完成
- **FR-007**: System MUST 在边界收敛过程中保持现有 Agent 执行、任务调度、history replay、tool-output 恢复和 session memory 能力的终端用户可见行为稳定
- **FR-008**: System MUST 只允许短期过渡桥接代码在明确标注依赖方、保留原因和删除时机的前提下存在；未标注的兼容桥不得进入最终状态
- **FR-009**: System MUST 提供可执行的边界守卫，能够检测旧目录回流、领域层协议泄漏、领域层直接技术依赖和运行时查找回流等违规情况
- **FR-010**: System MUST 更新根级与模块级开发文档，使新的依赖方向、模块职责和 Agent 有界上下文结构可以被直接理解和复用
- **FR-011**: System MUST 明确 required ownership：触发层拥有协议适配，应用层拥有会话与编排契约，领域层拥有子域模型与端口，基础设施层拥有技术适配器，装配层拥有 Spring 运行时装配
- **FR-012**: System MUST 将 required contract changes 限定为包归属、流式抽象、端口/仓储 seam、边界守卫与文档更新，不得借本特性引入新的数据库结构或新的终端用户工作流
- **FR-013**: System MUST 保证 dispatch、execute、armory、task、history replay 和 session memory 只有一条主线执行路径，不允许旧路径与新路径长期并行共存
- **FR-014**: System MUST 对无真实入口的历史兼容类执行清理，而不是以“可能以后有用”为由默认保留

### Key Entities *(include if feature involves data)*

- **Agent Application Workflow**: 一组由应用层拥有的主链路编排能力，负责调度 dispatch、execute、armory、task 等跨能力流程
- **Agent Domain Subdomain**: Agent 有界上下文中的一个稳定领域归属单元，表示 runtime、ledger、memory、rag、role 中的任一核心能力边界
- **Domain Output Contract**: 领域层用于表达过程输出和最终结果的协议无关契约，不绑定任何具体传输方式
- **Technical Capability Port**: 由领域层声明、由基础设施层实现的外部能力接口，用于隔离模型调用、数据查询、文件处理和远程工具运行时等技术细节
- **Boundary Guard Rule**: 用于验证旧目录、禁止依赖和错误分层是否回流的自动化约束规则
- **Compatibility Bridge**: 在迁移过程短期存在的过渡适配单元，用于连接旧路径和新路径，并要求具备明确删除计划

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 受影响的后端模块在收敛完成后能够完成一次聚焦全链路编译，且编译失败数为 0
- **SC-002**: Agent 边界、HTTP 入口、持久化边界、运行时边界、history replay 和 session memory 的聚焦回归集合在验收时通过率达到 100%
- **SC-003**: 自动化边界审计对旧目录回流、领域层协议泄漏、领域层直接技术依赖和运行时查找回流的违规报告数为 0
- **SC-004**: 主链路代码在验收时对旧 `domain/agent/service` 根接口的生产级依赖数为 0，且旧 `domain/agent/service` 与 `domain/agent/reactor` 不再作为主路径目录存在
- **SC-005**: 根级与相关模块级文档在同一交付批次完成更新，且能够覆盖 100% 的最终依赖方向与职责归属说明
- **SC-006**: 在既有 Agent 控制器入口、任务调度、history replay、tool-output 恢复和 session memory 重建的验收样本中，100% 保持与收敛前基线一致的终端用户可见行为

## Assumptions

- Phase 1 已经引入的 `case` 模块、会话流抽象和聚焦边界测试可继续作为本次收敛的基础，而不需要重新设计第一阶段方案
- 当前数据库结构、执行账本数据、会话记忆数据和前端消费契约都被视为稳定事实源，本次只做边界收敛而不重做这些事实源
- 对这类架构收敛特性，现有聚焦回归测试、目录扫描和源码边界审计被视为可接受的验收证据
- 非目标模块原则上不修改；如果为了保持编译链路或边界闭环需要最小适配，则只允许做与本次边界收敛直接相关的改动
- 没有真实入口的历史兼容代码可被移除，不要求为了假设性未来场景长期保留
