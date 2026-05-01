## Context

当前工具调用账本已经有 `llmObservation` 与 `output_json` 两类结果位，但 `output_json` 还没有形成统一 contract：有的工具完全不写，有的直接落前端展示字段，有的只是随手拼出的字符串 JSON。与此同时，会话历史 replay 方案正在收敛到“事实账本 + projector 重建前端事件”的方向，如果 `output_json` 继续混入 `taskId`、`renderKind`、`eventData` 这类展示态字段，后续读侧就无法稳定区分“工具真实产出”和“某一版前端消费协议”。

这次变更是一个跨运行时写侧、工具实现、历史 replay 读侧和测试契约的横切改动，约束如下：

- 继续复用现有 `ai_agent_tool_invocation.output_json` 列，不新增新的结果持久化列。
- 与 `refine-tool-llm-observation` 的 `llmObservation` 语义保持一致，不能再次把 observation 和结构化结果混写。
- 与 `conversation-history-projector-replay` 计划对齐，历史恢复必须由共享投影层把事实翻译成前端 `eventData`。
- 不改变当前实时 SSE 协议，也不要求所有工具返回完全相同的 JSON shape。

## Goals / Non-Goals

**Goals:**

- 为每次工具调用提供稳定、可版本化的 tool-native `output_json`。
- 明确 `llmObservation` 与 `output_json` 的职责分离，避免主智能体 observation 与工具事实混淆。
- 让纯文本工具、失败结果和 rich tool 都有统一可测试的落库语义。
- 让历史 replay 基于 `tool_name + input_json + output_json + artifact` 做 per-tool projection，而不是依赖前端事件快照。
- 通过测试锁定重点工具和 projector 的 contract，避免后续新增工具时再次漂移。

**Non-Goals:**

- 不重做现有 SSE 实时事件协议，不要求前端组件链改用新的 message type。
- 不把所有工具强制收敛成同一个业务字段集合，只要求它们满足统一边界与版本化规则。
- 不回填历史旧数据，也不承诺旧 `output_json` 形态在新 projector 下完全等价重放。
- 不新增独立 artifact 表或新的文件存储协议，继续复用现有 artifact 账本。

## Decisions

### Decision 1: 将 `output_json` 固定为“工具原生结果事实”，彻底排除前端展示态字段

采用方案：

- `output_json` 只表达工具最终结果事实，允许是通用 fallback JSON，或某个 rich tool 的专属 JSON。
- 所有 `output_json` 根节点都必须可版本化；通用 fallback 与 rich tool 都至少带 `schemaVersion`。
- 禁止把 `taskId`、`taskOrder`、`messageOrder`、`renderKind`、`eventData`、`replaySeq` 这类展示态字段直接持久化到 `output_json`。

原因：

- 只有把存储层和展示层剥离，`output_json` 才能成为调试、分析和历史恢复的稳定事实源。
- 当前前端协议可能继续演进，若直接把其 payload 写库，会让账本和某一版前端实现强耦合。

备选方案：

- 方案 A：继续写 canonical render payload。问题是后端事实模型会继续被前端协议反向驱动。
- 方案 B：只存纯文本，不保留结构化结果。问题是 rich tool 的文件、分阶段结果和结果类型会丢失。

### Decision 2: 新增统一 `ToolOutputJsonBuilder`，由共享 builder 负责 fallback 与 rich tool 包装

采用方案：

- 在 domain 层新增统一 builder，提供 plain_text、error、tool-native 三类入口。
- 简单工具和失败路径走统一 fallback shape；rich tool 负责提供自己的事实对象，再由 builder 包装 `schemaVersion` 并序列化。
- `BaseAgent` 在落库前对空 `output_json` 做兜底，保证每条工具调用最终都有可持久化的 JSON。

原因：

- 如果让每个工具自己拼完整 JSON，字段名、空值策略和版本号会持续漂移。
- 统一 builder 也能把“禁止前端字段”“限制大文本预览”等约束收口到一个边界。

备选方案：

- 方案 A：完全由工具自行构造 JSON。问题是难以建立稳定 contract。
- 方案 B：在 DAO 层自动推导 JSON。问题是 DAO 无法理解不同工具的业务事实，也会丢掉 rich tool 细节。

### Decision 3: 历史 replay 使用 `ToolInvocationProjectorRegistry` 按 `tool_name` 分发，不再基于 `renderKind` 或字符串猜测

采用方案：

- 新增 `ToolInvocationProjector` 接口及 registry，按 `tool_name` 分发到具体解析器。
- 解析器读取 `input_json + output_json + artifact`，输出前端现有 `eventData` 所需参数。
- `ReplayProjector` 只负责任务顺序、消息顺序、visible 等外层展示语义，不负责理解每个工具的结果 shape。

原因：

- `tool_name` 是运行时最稳定的分发键，远比 `renderKind` 或字符串关键词更可靠。
- 这样可以把“工具事实解释”与“展示排序语义”拆开，便于局部扩展和测试。

备选方案：

- 方案 A：继续在 `ReplayProjector` 里硬编码所有工具分支。问题是读侧会快速膨胀，职责不清。
- 方案 B：要求 `output_json` 自带可直接渲染的标准 messageType。问题是又会回到“把展示协议写入账本”的旧路。

### Decision 4: 对 rich tool 允许异构 shape，但必须稳定、可测试并显式处理文件引用

采用方案：

- `deep_search`、`file_tool`、`code_interpreter`、`report_tool`、`data_analysis`、`multimodalagent_tool`、`image_generation_tool`、`script_runner_tool` 等 rich tool 保留各自更贴近业务事实的 JSON 结构。
- 每个 rich tool 至少满足：根节点有 `schemaVersion`，字段名稳定，不内嵌前端排序/渲染字段。
- 产出文件的工具默认只在 `output_json` 里保存逻辑 `fileInfo` 和必要 summary，稳定下载/预览引用仍以 artifact 账本为准。
- 对 `file_tool/get` 一类大文本场景，默认走 `artifact_only` 或等价模式，不重复把超长正文灌进 `output_json`。

原因：

- 不同工具的原生结果差异很大，强行统一到同一 shape 会损失事实表达能力。
- 文件内容是典型的存储放大点，必须把正文与稳定引用解耦。

备选方案：

- 方案 A：把所有 rich tool 映射成统一 `data` 字段。问题是语义过度抽象，后续 projector 仍然要反推业务含义。
- 方案 B：允许工具任意写 JSON，不做文件内容约束。问题是 shape 很快失控，MySQL 也会被大正文放大。

### Decision 5: 新 contract 主要约束“新写入”，旧历史采用 best-effort fallback，不做 backfill

采用方案：

- 本次不新增数据库迁移，也不对旧 `output_json` 做批量重写。
- 新 projector 必须能优先消费新 shape；遇到旧 shape、空值或异常值时，通过 default projector 或 fallback 文本尽量降级显示。
- 测试重点锁定新写入路径和新 replay 路径，而不是为历史脏数据建立长期兼容债务。

原因：

- 需求目标是建立稳定新契约，而不是为所有历史异常形态做一次性清洗工程。
- 旧数据兼容会显著放大 projector 复杂度，拖慢这次契约收敛。

备选方案：

- 方案 A：对旧数据做离线回填。问题是成本高，而且需要定义旧 shape 到新 shape 的不可靠映射。
- 方案 B：完全忽略旧值并要求历史全量失效。问题是运行中切换风险过高。

## Risks / Trade-offs

- [per-tool projector 数量增加，读侧复杂度上升] → 用 registry + 独立测试把复杂度局部化，避免再次堆回单个 projector。
- [新增工具时忘记补 `output_json` shape] → 在 `BaseAgent` 保留 fallback 兜底，并通过运行时测试断言每条 tool invocation 都有 `schemaVersion`。
- [文件类工具把正文重复塞进 `output_json` 导致存储膨胀] → 统一 builder 和测试显式限制 `artifact_only` / preview 上限策略。
- [新 replay projector 与现有实时链路不一致] → 明确让 `ReplayProjector` 只做排序语义，工具解析统一走 registry，并补实时/历史同构回归测试。
- [旧 `output_json` 形态在历史回放中表现不完整] → 采用 best-effort fallback，并把契约重点放在新写入路径，避免继续扩大旧数据兼容面。

## Migration Plan

1. 新增 `ToolOutputJsonBuilder` 与 `ToolInvocationProjector` 契约，先用测试锁定“tool-native output_json，不含前端字段”的基础规则。
2. 改造 `BaseAgent` 与 `ToolResultPayload`，确保所有工具调用都能落库独立的 `output_json` 与 `llmObservation`。
3. 逐个收敛重点 rich tool 的原生 JSON shape，补齐文件引用和 fallback 行为。
4. 将历史 replay 接到 `ToolInvocationProjectorRegistry`，让 `ReplayProjector` 只承担外层排序和事件组装。
5. 更新相关计划文档与全链路测试，锁定新 contract。

回滚策略：

- 代码回滚即可恢复旧写入/投影逻辑，因为本次不涉及新表或新列。
- 回滚后新写入的 tool-native `output_json` 仍留在原列中，不影响数据库可读性，但旧逻辑可能不会充分利用这些字段。

## Open Questions

- 当前没有阻塞性开放问题。若后续发现某类 rich tool 的 JSON shape 还需继续细分，可在 capability 已建立的前提下再增量扩展对应 projector 与测试。
