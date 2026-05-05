# Feature Specification: Agent Legacy Bridge 实质删除与子域再收敛

**Feature Branch**: `[020-prune-agent-bridges]`  
**Created**: 2026-05-05  
**Status**: Draft  
**Input**: User description: "直接开下一轮，专门做这些 legacy bridge 的实质删除与子域模型再收敛"

## Existing System Context *(mandatory for brownfield features)*

- **Affected Modules**: `ai-agent-station-study-case`、`ai-agent-station-study-domain`、`ai-agent-station-study-infrastructure`、`ai-agent-station-study-trigger`、`ai-agent-station-study-app`
- **Existing Capabilities to Reuse**: `019-agent-ddd-convergence` 已落地的 case 应用编排入口、runtime/ledger/memory/rag/role 子域骨架、现有 port/repository seam、聚焦边界守卫测试、history replay / execution ledger / session memory 主链路
- **Out of Scope**: 不改 `ui/`、`reactor-tool/`、`reactor-client/`，不新增数据库表/列，不新增终端用户功能，不顺手重做与 legacy bridge 删除无关的通用重命名
- **Current Constraints**: 必须保持 `Trigger -> Case -> Domain <- Infrastructure`；`app` 仅负责装配；删除 bridge 后现有控制器、任务调度、history replay、tool-output 恢复、session memory 与 dataagent / gpt query 既有入口仍需保持可用；最终状态不能再把 `domain/agent/service` 或 `domain/agent/reactor` 当作长期兼容停放区

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 开发者可以沿唯一主链路维护 legacy query 与 dataagent 能力 (Priority: P1)

作为维护 Agent 主链路的后端开发者，我希望 legacy GPT 查询、multi-agent 协作和 dataagent 这些在上一轮仍通过 bridge 兜底的能力，正式切换到清晰的 case/domain seam，这样我后续维护这些能力时，不需要继续从 `reactor/service` 旧接口绕回主路径。

**Why this priority**: 这些 bridge 仍然是当前主链路里最明显的遗留入口。如果不先移除，`019` 的边界收敛仍然停留在“结构变了但语义入口没真正切走”的过渡态。

**Independent Test**: 检查 controller、job、auto-configuration 与 case 服务依赖，并执行聚焦边界与主链路回归；确认 gpt query、multi-agent、dataagent 入口都只通过稳定 case/domain seam 进入，而不再依赖 `reactor/service` bridge。

**Acceptance Scenarios**:

1. **Given** 开发者查看 GPT 查询或 multi-agent 入口，**When** 追踪调用链，**Then** 主链路必须先进入 case 或稳定领域契约，而不是先命中 `IGptProcessService` 或 `IMultiAgentService`
2. **Given** 开发者查看 dataagent 入口，**When** 追踪 chat、NL2SQL、召回与同步链路，**Then** 主链路必须不再把 `DataAgentService`、`Nl2SqlService` 作为长期 bridge 入口
3. **Given** 旧 bridge 类已经没有真实依赖方，**When** 本轮完成，**Then** 这些 bridge 必须被删除，而不是继续保留为“未来可能有用”的兼容壳

---

### User Story 2 - 维护者可以按稳定子域理解剩余 legacy 模型与配置归属 (Priority: P2)

作为长期维护这套 Agent 有界上下文的工程师，我希望仍残留在 `reactor` 或 `service` 目录中的模型、配置、工厂和步骤节点，进一步压回明确的 runtime、ledger、memory、rag、role 或 infrastructure 归属，这样我在阅读或修改代码时，不会再遇到“目录已收敛但关键模型还挂在旧包里”的半收敛状态。

**Why this priority**: 如果 bridge 删除后，旧模型和旧配置仍然长期挂在 `reactor` / `service`，代码理解成本依然高，而且后续很容易在这些旧包里继续堆新逻辑。

**Independent Test**: 对 `domain/agent` 与相关 `app/infrastructure` 做目录审计、依赖扫描和聚焦回归；确认剩余 legacy 模型/配置/步骤节点都具备明确主归属，旧目录不再承担稳定语义承载职责。

**Acceptance Scenarios**:

1. **Given** 维护者查看运行时模型、请求响应模型、dataagent 配置或 image generation 相关语义，**When** 检查目录归属，**Then** 这些语义必须位于清晰且可解释的稳定边界，而不是继续散落在 `reactor` 总树
2. **Given** 某个 legacy 模型仍被多个模块依赖，**When** 本轮迁移完成，**Then** 必须收敛为新的稳定契约或稳定子域模型，而不是继续通过旧包名维持默认耦合
3. **Given** 旧目录中仍有工具类、配置类、步骤工厂或桥接 DTO，**When** 这些内容已具备新归属，**Then** 系统必须删除旧目录副本，避免新旧双存

---

### User Story 3 - 交付团队可以用最终守卫锁定“bridge 已删完”的边界状态 (Priority: P3)

作为负责后续治理的交付团队成员，我希望自动化守卫与模块文档能够把“bridge 已删完、旧目录不再承载稳定语义”固化下来，这样后续需求不会再把已经清空的 `service/reactor` 入口重新引回。

**Why this priority**: 没有最终守卫，bridge 删除只会是一次性的人工清理；后续需求很容易再次把 convenience bridge 或 legacy 模型放回旧目录。

**Independent Test**: 执行目录扫描、禁止依赖扫描、聚焦回归与文档核对；确认任何旧 bridge、旧目录回流或错误主归属都会被自动发现。

**Acceptance Scenarios**:

1. **Given** 有人重新引入 `reactor/service` bridge 或让旧目录重新承载稳定语义，**When** 执行边界守卫与目录扫描，**Then** 系统必须明确报告违规
2. **Given** 新成员阅读根级与模块级文档，**When** 他们理解本轮结果，**Then** 文档必须直接说明哪些旧 bridge 已删除、哪些目录仍被允许存在以及允许存在的原因
3. **Given** 最终状态仍保留少量历史包名或兼容文件，**When** 团队审查交付结果，**Then** 这些内容必须被界定为稳定契约或明确延期项，而不是“暂时先这样放着”

### Edge Cases

- 某些旧接口虽然仍可编译引用，但真实依赖方只剩测试或过渡装配，系统必须区分“仍有生产入口”与“仅剩历史引用”
- 删除 bridge 过程中，controller 入参、history replay 输出和 dataagent 相关 DTO 可能仍暂时使用旧命名语义，但这不等于必须继续保留旧服务入口
- 某些配置或模型可能跨多个模块被引用，迁移时必须先建立稳定契约，再删除旧包，不能通过复制一份相同模型维持双轨
- 本轮允许把明确延期的共享配置契约暂留在旧包，但必须在规格、计划和守卫中写清楚其被允许存在的边界，避免被误当成新的 catch-all 入口
- 最终状态既要满足“bridge 删除”，也要满足“稳定语义归属更清晰”，不能只删类不收敛模型

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST 将 legacy GPT query、multi-agent 与 dataagent 主链路从 `reactor/service` 旧 bridge 正式切换到稳定的 case/domain seam
- **FR-002**: System MUST 删除已经没有生产依赖方的 legacy bridge，包括旧接口、旧实现、旧委派壳与无效兼容注释
- **FR-003**: System MUST 将仍残留在 `reactor` 或 `service` 目录中的稳定业务语义，继续收敛到 `runtime / ledger / memory / rag / role` 或明确的 infrastructure/app 归属
- **FR-004**: System MUST 保证 bridge 删除后，现有 controller、job、history replay、tool-output 恢复、session memory、dataagent 与 GPT 查询入口的终端用户可见行为保持稳定
- **FR-005**: System MUST 为仍允许暂存的历史包名或共享配置契约定义明确边界，说明其存在原因、归属性质与后续处理方式
- **FR-006**: System MUST 将模型、配置、步骤工厂、请求响应对象与执行支持类的主归属收敛为可解释的稳定语义，而不是继续默认挂靠旧 `reactor/service` 包树
- **FR-007**: System MUST 保持 `domain` 只承载领域语义、稳定契约与端口，不得因 bridge 删除而把 HTTP、JDBC、Spring runtime lookup 或其他技术执行细节重新带回
- **FR-008**: System MUST 更新边界守卫，使其不仅检查旧根接口零依赖，还检查旧 bridge 文件、旧目录稳定语义回流与未解释的 legacy 包残留
- **FR-009**: System MUST 更新根级与模块级文档，明确本轮删除了哪些 bridge、哪些 legacy 目录已不再允许承载主逻辑，以及剩余延期项的边界
- **FR-010**: System MUST 将 required contract changes 限定为内部层间 seam、模型归属、配置归属、守卫与文档更新，不得借本特性引入新的数据库结构、前端工作流或外部产品接口
- **FR-011**: System MUST 保证 case、trigger、app 与 infrastructure 不再对已删除的 legacy bridge 产生生产级依赖
- **FR-012**: System MUST 让目录审计结果能够直接区分“稳定子域目录”“允许延期的历史契约目录”“应被清空的旧 bridge 目录”

### Key Entities *(include if feature involves data)*

- **Legacy Bridge**: 迁移期间用于承接旧入口到新 seam 的过渡接口、实现或委派壳，目标是在本轮被删除或被界定为明确延期项
- **Stable Domain Seam**: 由 case/domain/infrastructure 共同组成的稳定调用与建模边界，用于替代旧 `reactor/service` 主入口
- **Subdomain Ownership Map**: 一份描述 runtime、ledger、memory、rag、role 与相关配置/模型/步骤工厂归属的稳定映射
- **Legacy Package Allowlist Rule**: 对仍可暂存的历史包名建立的有限许可规则，要求说明存在原因与禁止扩张边界
- **Bridge Removal Guard**: 用于检测 bridge 是否已被删除、旧目录是否回流承载主逻辑的自动化守卫规则

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 聚焦受影响后端模块执行一次全链路编译时，编译失败数为 0
- **SC-002**: 针对 gpt query、dataagent、history replay、session memory 与边界守卫的聚焦回归集合，在验收时通过率达到 100%
- **SC-003**: 生产代码对已标记待删除的 legacy bridge 依赖数在验收时为 0
- **SC-004**: `domain/agent/service` 与 `domain/agent/reactor` 中被定义为“应清空的旧 bridge 目录”在验收时残留文件数为 0
- **SC-005**: 自动化目录扫描与禁止依赖扫描对未解释的 legacy bridge、旧目录主逻辑回流和错误子域归属的违规报告数为 0
- **SC-006**: 根级与相关模块级文档在同一交付批次完成更新，并覆盖 100% 的 bridge 删除规则、剩余延期项边界与稳定子域归属说明

## Assumptions

- `019-agent-ddd-convergence` 已经完成第一轮主边界切分，因此本轮可以直接建立在现有 case seam、port/repository seam 和守卫测试之上
- 现有数据库结构、控制器路由、前端消费方式与 Python 子系统都被视为稳定事实源，本轮不以改变这些事实源为目标
- 若某些历史配置或模型暂时无法在本轮彻底迁出，其存在必须被明确界定为稳定契约或明确延期项，而不是默认继续当 bridge
- 对本特性而言，目录扫描、依赖扫描、聚焦编译与聚焦回归是足够的验收证据，不要求采用 TDD 流程
- 本轮优先追求“删掉遗留入口并让剩余语义更清晰”，而不是大规模追求命名美化或跨模块重构
