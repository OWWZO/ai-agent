# Research: Agent Skill Mechanism

## Decision 1: 运行时 skill 采用 `SKILL.md + 可选 scripts.yaml + 目录约定`

- **Decision**: V1 兼容主流 skill 目录约定，必需文件为 `SKILL.md`，可选 `scripts.yaml`、`scripts/`、`references/`、`assets/`；脚本默认从 `scripts/` 自动发现，`scripts.yaml` 只作为增强配置。
- **Rationale**: 这能最大化兼容现有 skill 包，满足“安装后尽量不改代码直接用”的目标，同时保留结构化增强入口。
- **Alternatives considered**:
  - 强制 `scripts.yaml`：配置整齐，但对现有 skill 兼容性差。
  - 只支持单脚本入口：实现最轻，但无法覆盖多脚本 skill。

## Decision 2: Java 侧新增 `SkillRegistry + AgentToolCollectionFactory`

- **Decision**: 在 `domain` 中新增 `SkillRegistry` 负责扫描、解析、缓存 skill，并新增统一工具装配工厂收敛 `PlanSolve/ReAct` 重复逻辑。
- **Rationale**: `SkillRegistry` 符合领域层“运行时能力编排元数据”的职责；共享装配工厂能避免 `RootNode` 与 `Step1SopRecallAndPrepareNode` 双处重复注册 skill 工具。
- **Alternatives considered**:
  - 把 skill 扫描写进 `ReactorConfig`：配置类会继续膨胀，且混入业务逻辑。
  - 在每个节点各自 new skill 工具：变更点分散，后续工具开关与测试成本更高。

## Decision 3: `Fix` 链路完全排除 skill 机制

- **Decision**: skill 工具仅注册到 `PlanSolve/ReAct` 的 `BaseTool + ToolCollection` 链路，`Fix` 继续沿用原有 Spring AI `ToolCallback` 与 MCP 装配。
- **Rationale**: 这是当前 spec 的明确边界，也避免一轮 feature 同时触达两套工具体系。
- **Alternatives considered**:
  - 同时改 `Fix`：看似统一，实则会把 feature 放大到另一套装配模型与提示词设计。

## Decision 4: `reactor-tool` 承担脚本执行后端

- **Decision**: 新增 `POST /v1/tool/script_runner` 到 `reactor-tool`，由 Python 服务根据 runtime 适配器执行脚本。
- **Rationale**: 现有 `code_interpreter`、`report`、`file_tool` 已形成“Java 编排 + Python 执行”的部署边界；沿用这条链路最稳，也最利于后续加隔离或沙箱。
- **Alternatives considered**:
  - Java 直接用 `ProcessBuilder` 跑所有脚本：实现表面更短，但会让执行后端分裂，难以和现有文件上传、日志、运行目录机制统一。

## Decision 5: V1 只做最小安全边界，不做完整沙箱

- **Decision**: V1 只允许执行已注册/已发现脚本，并限制本地 skill 文件工具只能访问已注册 skill 根目录内的文件；越界访问直接拒绝并记录日志。
- **Rationale**: 这是在“先不做完整安全体系”的前提下仍然必须具备的最小边界，既能控制风险，又不会把 V1 扩成完整沙箱项目。
- **Alternatives considered**:
  - 完整容器沙箱：方向正确，但会显著拖慢当前 feature 落地。
  - 完全不设边界：与生产可控性目标冲突。

## Decision 6: V1 不负责自动安装与缓存依赖

- **Decision**: skill 脚本运行所需解释器与依赖由部署环境预先准备；V1 只负责发现、调度、执行与结果回传。
- **Rationale**: 自动安装依赖会引入环境缓存、失败恢复、版本冲突等新问题，不适合与 skill 机制主体一起首次落地。
- **Alternatives considered**:
  - 自动识别 `requirements.txt` / `package.json` 并安装：可用性更高，但复杂度明显升高，适合作为后续增强。

## Decision 7: 本地只读工具族一起进入 V1

- **Decision**: V1 同时提供 `read_tool`、`list_directory_tool`、`glob_tool`、`grep_tool`。
- **Rationale**: 如果没有这些工具，主流 skill 的 `references/` 与 `scripts/` 很难被模型继续消费，`skill_tool` 只能停留在“读说明”。
- **Alternatives considered**:
  - 只做 `skill_tool + script_runner_tool`：闭环不完整，主流 skill 兼容性不够。

## Decision 8: `script_runner_tool` 同时支持 `arguments` 与 `argv`

- **Decision**: `script_runner_tool` 对模型暴露双通道入参：结构化 `arguments` 与顺序型 `argv`。
- **Rationale**: 结构化参数更易扩展，`argv` 更兼容现有 CLI 风格脚本；两者并存最平衡。
- **Alternatives considered**:
  - 只支持 `arguments`：对现有脚本兼容性不足。
  - 只支持 `argv`：结构化元数据与后续校验空间不足。
