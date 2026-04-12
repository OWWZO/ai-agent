# Feature Specification: Agent Skill Mechanism

**Feature Branch**: `[002-agent-skill-mechanism]`  
**Created**: 2026-04-12  
**Status**: Draft  
**Input**: User description: "将 `D:\Java Code\spring-ai-agent-utils-0.7.0` 中的 skill 工具机制移植到 `D:\Java Code\ai-agent\ai-agent-station-study`，基于现有项目结构实现为新工具能力，让智能体项目具备可扩展的 skill 机制；优先考虑优雅性、可维护性，且以现有结构为准，不强行套 DDD。"

## Existing System Context *(mandatory for brownfield features)*

- **Affected Modules**:
  - `ai-agent-station-study-domain`
  - `ai-agent-station-study-app`
  - `specs/002-agent-skill-mechanism`
- **Existing Capabilities to Reuse**:
  - `BaseTool` / `ToolCollection` 本地工具抽象与执行入口
  - `RootNode`、`Step1SopRecallAndPrepareNode` 的工具装配流程
  - `AiClientNode` 的 Spring AI `ChatClient` 装配流程
  - `ReactorConfig` 与 `application*.yml` 的配置装配方式
  - 现有 MCP 工具发现、缓存与 `ToolCallback` 注册机制
- **Out of Scope**:
  - 不直接复用仓库根目录 `.agents/skills/` 作为业务运行时技能目录；该目录属于其他外部 app / 协作体系，不属于本项目运行时 skill
  - 不修改现有 MCP 协议、MCP DAO、MCP 管理接口
  - 不在本阶段引入前端技能管理页面
  - 不在本阶段引入数据库持久化的技能主数据表
  - 不在本阶段为 `Fix` 链路接入 skill 机制
- **Current Constraints**:
  - skill 机制仅服务 `PlanSolve/ReAct` 链路，沿用现有 `BaseTool + ToolCollection` 工具体系
  - `Fix` 链路继续维持现有 Spring AI `ToolCallback` + MCP 装配方式，不混入 skill 能力
  - 新能力优先复用现有 `PlanSolve/ReAct` 工具装配链，不增加一次性分支逻辑
  - skill 文件需与运行时智能体隔离，避免开发协作 skill 混入生产对话
  - 若某段代码本身未严格按 DDD 组织，则允许沿用现有结构，以最小侵入方式扩展

## Clarifications

### Session 2026-04-12

- Q: `script_runner_tool` 的脚本入参协议应该支持哪种方式？ → A: V1 同时支持结构化 `arguments` 和原始 `argv`
- Q: V1 是否要把本地 skill 浏览工具一起纳入范围？ → A: V1 一次性加入 `read_tool`、`list_directory_tool`、`glob_tool`、`grep_tool`
- Q: V1 应该如何发现 skill 里的可执行脚本？ → A: `scripts.yaml` 为可选增强，默认自动扫描 `scripts/` 目录并按扩展名推断 runtime
- Q: V1 是否负责自动安装并缓存 skill 的运行时依赖？ → A: V1 假设解释器与依赖已准备好，本阶段只负责发现脚本与执行脚本
- Q: V1 的脚本执行安全边界应如何定义？ → A: V1 不引入完整沙箱，只保留最小边界：只能执行已注册脚本，且只允许访问已注册 skill 目录

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 智能体可按需加载技能 (Priority: P1)

作为使用 `PlanSolve/ReAct` 模式的用户，我希望模型在需要某类专业步骤时能够调用 `skill` 工具读取对应 `SKILL.md`，从而获得额外的领域说明、参考文件入口和脚本入口，而不是把所有长说明硬编码进系统提示词。

**Why this priority**: 这是 skill 机制的最小闭环。没有“可发现 + 可调用 + 可返回技能内容”，后续的技能生态和更多工具扩展都无法成立。

**Independent Test**: 在配置至少一个 skill 目录后，向智能体发起一个明显匹配技能描述的请求，模型可看到技能列表、调用 `skill` 工具，并收到包含技能基路径与技能正文的返回内容。

**Acceptance Scenarios**:

1. **Given** 系统已配置技能目录且目录下存在合法 `SKILL.md`，**When** 智能体初始化工具集，**Then** skill 工具描述中应包含可用技能名称与描述摘要
2. **Given** 模型决定调用 skill 工具并传入合法技能名，**When** skill 工具执行，**Then** 返回结果中应包含技能基路径与技能正文
3. **Given** 模型传入不存在的技能名，**When** skill 工具执行，**Then** 系统应返回明确的“技能不存在”结果，而不是抛出未处理异常

---

### User Story 2 - 技能机制与现有 React 工具链优雅集成 (Priority: P2)

作为平台开发者，我希望 skill 机制直接复用 `BaseTool`、`ToolCollection`、`RootNode`、`Step1SopRecallAndPrepareNode` 这一套现有 React/PlanSolve 工具装配链，而不是另起一套特殊执行框架。

**Why this priority**: 你的要求已经明确 skill 只给 `PlanSolve/ReAct`。这时最优方案不是“双适配”，而是把 skill 作为一个标准 `BaseTool` 插件融入现有链路，避免未来维护额外心智负担。

**Independent Test**: 配置 skill 目录后，`PlanSolve/ReAct` 初始化时可自动装载 `skill` 工具，且不影响 `Fix` 链路原有行为。

**Acceptance Scenarios**:

1. **Given** skill 机制已启用，**When** `RootNode` 或 `Step1SopRecallAndPrepareNode` 构建 `ToolCollection`，**Then** 应将 `skill` 作为标准本地工具注入工具集合
2. **Given** `Fix` 链路正常装配，**When** skill 机制启用，**Then** `AiClientNode` 的 Spring AI `ChatClient` 行为不应发生变化

---

### User Story 3 - 新增技能和后续新工具接入更轻量 (Priority: P3)

作为后续扩展工具的开发者，我希望新增一个 skill 只需要新增目录和 `SKILL.md`，必要时附加参考文件或脚本，而不是改 Java 代码；同时 skill 机制本身要为未来加入更多“本地工具 + Spring AI 工具”保留统一注册入口。

**Why this priority**: 用户明确提出“背景是新增其他工具”，因此 skill 机制不能只是一次性功能，而要成为后续工具扩展的基础设施。

**Independent Test**: 新增一个新技能目录并补充 `SKILL.md` 后，无需改业务代码即可被系统发现并供模型调用。

**Acceptance Scenarios**:

1. **Given** 运行时技能目录新增一个合法技能，**When** 应用重新加载技能注册，**Then** 新技能应自动出现在 `skill` 工具的可用技能列表中
2. **Given** 技能目录下存在 `reference.md` 或 `scripts/`，**When** skill 工具返回技能结果，**Then** 返回内容中应包含基路径，供模型继续通过现有文件/脚本工具访问附属资源

---

### Edge Cases

- 技能目录不存在、为空目录、或没有任何合法 `SKILL.md` 时如何降级？
- `SKILL.md` frontmatter 缺少 `name` / `description` 时如何跳过并记录日志？
- 出现重名技能时如何处理，才能避免模型调用到不确定目标？
- 当 `Fix` 链路保持纯 MCP / Spring AI 工具集合时，skill 机制如何保证完全不侵入其现有装配流程？
- 当技能正文很长时，是否允许完整返回，还是需要后续再引入截断与分段策略？
- 当脚本尝试访问 skill 根目录之外的文件，或者请求执行未注册脚本时，系统应如何拒绝并返回可观测错误？

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 引入运行时技能定义格式，兼容 `SKILL.md + YAML frontmatter`，至少支持 `name` 与 `description` 字段
- **FR-002**: 系统 MUST 提供统一的技能加载与注册组件，负责从配置目录扫描技能、解析元数据、缓存技能定义，并向不同工具链提供统一查询能力
- **FR-003**: 系统 MUST 在 `PlanSolve/ReAct` 链路中以新的 `BaseTool` 形式暴露 `skill` 工具，而不是把技能逻辑硬编码进某个 Agent
- **FR-004**: 系统 MUST 保持 `Fix` 链路现有装配逻辑不变，不在本特性范围内为其注入 `skill` 能力
- **FR-005**: 系统 MUST 让 `skill` 工具的描述中包含“如何调用 skill”以及“当前有哪些技能可用”的信息，帮助模型自行选择技能
- **FR-006**: 系统 MUST 指定扩展归属：
  - 技能注册与加载归属 `ai-agent-station-study-domain` 中现有 reactor/tool 相关区域
  - Spring 装配与配置归属 `ai-agent-station-study-app`
  - `PlanSolve/ReAct` 工具接入归属现有执行节点的工具装配逻辑
  - `Fix` 不新增接入点
- **FR-007**: 系统 MUST 描述所需配置变更：
  - 新增运行时 skill 开关
  - 新增运行时 skill 目录列表配置
  - 新增 `PlanSolve/ReAct` 链路的启用范围配置
- **FR-008**: 系统 MUST 将外部 app 使用的 `.agents/skills/` 与本项目业务运行时 skill 目录隔离，默认运行时目录应使用独立路径
- **FR-009**: 系统 MUST 在技能名不存在、配置为空、技能解析失败等场景下返回可观测的错误结果并记录日志，不能影响其他工具正常装配
- **FR-010**: 系统 MUST 对重名技能定义明确规则，默认采用“启动失败并提示冲突”或“拒绝注册后记录冲突日志”的确定性行为，不允许静默覆盖
- **FR-011**: 系统 MUST 允许 skill 返回其基路径，供模型继续借助现有 `file_tool`、`code_agent`、`deep_search_tool` 或未来新增工具访问附属资源
- **FR-012**: 系统 MUST 优先复用来源项目中已验证的 skill 核心思想，但需按当前项目现有结构适配，不直接照搬其全部周边能力
- **FR-013**: 系统 MUST 提供 `script_runner_tool` 来执行已注册或自动发现的 skill 脚本，且 V1 必须同时支持结构化 `arguments` 与原始 `argv` 两种传参方式，以兼容主流 skill 中的 CLI 脚本与结构化脚本
- **FR-014**: 系统 MUST 在 V1 同时提供本地 skill 浏览工具：`read_tool`、`list_directory_tool`、`glob_tool`、`grep_tool`，用于读取 `SKILL.md`、浏览 `references/`、定位 `scripts/`，保证主流 skill 的附属资源可直接被模型继续消费
- **FR-015**: 系统 MUST 将 `scripts.yaml` 设计为可选增强配置；当 skill 未提供 `scripts.yaml` 时，系统 MUST 默认自动扫描 `scripts/` 目录并按脚本扩展名推断 runtime，以兼容主流 skill 的目录约定
- **FR-016**: 系统 MUST 将 V1 的脚本执行范围限定为“发现并执行已注册脚本”；V1 MUST 假设目标运行环境中的解释器与依赖已预先准备完成，不在本阶段引入自动安装或依赖缓存机制
- **FR-017**: 系统 MUST 在 V1 维持最小安全边界：`script_runner_tool` 只能执行已注册或自动发现的 skill 脚本，本地 skill 文件访问工具只能访问已注册 skill 根目录内的文件；对于越界路径或未注册脚本请求，系统 MUST 返回明确错误并记录日志

### Key Entities *(include if feature involves data)*

- **SkillDefinition**: 运行时技能定义，包含技能名、描述、基路径、frontmatter 扩展元数据、正文内容
- **SkillRegistry**: 技能注册中心，负责目录扫描、缓存、按名查询、冲突检测和技能摘要输出
- **SkillToolRequest**: `skill` 工具入参，至少包含技能名；后续可扩展为命名空间、版本等
- **SkillToolResult**: `skill` 工具出参，包含技能基路径、技能正文、状态信息
- **ScriptRunnerToolRequest**: `script_runner_tool` 入参，至少包含 `skillName`、`scriptName`、结构化 `arguments` 与原始 `argv`
- **SkillScriptDefinition**: skill 中脚本的统一定义，来源可以是 `scripts.yaml` 或 `scripts/` 自动扫描结果，包含脚本名、路径、runtime、说明等信息
- **SkillFileAccessTool**: 本地只读 skill 文件访问工具族的统称，负责读取、列目录、模式匹配与内容检索
- **SkillProperties**: 运行时配置对象，包含启用开关、目录列表、是否注入 `PlanSolve/ReAct` 等

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 在至少 1 个合法技能目录存在时，应用启动后可稳定发现全部合法技能，且技能加载成功率达到 100%
- **SC-002**: `PlanSolve/ReAct` 链路初始化后始终可见 `skill` 工具，而 `Fix` 链路行为保持与接入前一致
- **SC-003**: 新增一个符合规范的技能目录后，无需改 Java 代码，仅通过配置与重启即可让模型可见该技能
- **SC-004**: 当技能目录为空、技能缺失或技能名错误时，系统仍能完成 Agent 初始化，并给出清晰的日志与工具级错误结果

## Assumptions

- 运行时业务 skill 与 `.agents/skills/` 的外部 app / 协作 skill 目标不同，因此默认不复用 `.agents/skills/`
- V1 先支持文件系统目录加载；JAR/classpath 技能包可作为后续增强能力
- V1 先支持显式工具调用与模型依据描述自主选择 skill，不额外引入数据库管理界面
- 现有 `FileTool`、`CodeInterpreterTool`、`DeepSearchTool` 等能力会继续保留，skill 只负责注入知识和路径，不替代这些执行工具
- 为降低侵入性，`PlanSolve/ReAct` 的 skill 接入优先沿用 `BaseTool` 模式，不额外改造 `Fix`
- 来源项目中的 `allowed-tools`、`model` 等 frontmatter 字段在 V1 先作为可解析但不强制执行的扩展元数据，为后续演进预留空间
- `scripts.yaml` 的价值主要是补充脚本别名、说明与增强元数据，而不是成为唯一脚本发现入口
- 运行时依赖准备（如 Python 虚拟环境、Node 依赖安装与缓存）在 V1 默认由部署环境或运维侧保障，后续如需要再单独增强
- V1 的安全策略聚焦“只执行已知脚本、只访问已知目录”的最小边界；完整沙箱、容器隔离与更细粒度权限模型留待后续增强
