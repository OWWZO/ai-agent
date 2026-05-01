## 1. 定义 tool-native output_json 契约

- [x] 1.1 新建 `ToolOutputJsonBuilder`，提供 plain_text、error 和 tool-native 三类统一构建入口
- [x] 1.2 新建 `ToolInvocationProjector` 接口与 `ToolInvocationProjectorRegistry`，明确按 `tool_name` 分发的读侧契约
- [x] 1.3 补充 builder / registry 聚焦测试，锁定 `output_json` 必须带 `schemaVersion` 且不得含前端展示字段

## 2. 收敛工具调用写侧语义

- [x] 2.1 调整 `ToolResultPayload` 与 `BaseAgent`，明确 `llmObservation` 和 `output_json` 的分工
- [x] 2.2 为纯文本工具、失败路径和未显式返回结构化结果的路径补齐 fallback `output_json`
- [x] 2.3 补充运行时账本测试，验证每条 tool invocation 都会独立持久化 `llmObservation` 与 `output_json`

## 3. 统一重点工具的原生 JSON shape

- [x] 3.1 收敛 `file_tool`、`code_interpreter`、`report_tool`、`data_analysis` 的 stable output_json shape，并补齐 `fileInfo` / summary 等核心字段
- [x] 3.2 收敛 `deep_search`、`multimodalagent_tool`、`image_generation_tool`、`script_runner_tool` 的 stable output_json shape，并保留各自 replay 所需事实
- [x] 3.3 对 artifact-heavy 场景落实 artifact-first 规则，避免默认把长正文或大体积结果重复写入 `output_json`
- [x] 3.4 补充重点工具回归测试，锁定 rich tool 的 `schemaVersion`、核心字段和无前端展示字段约束

## 4. 接入 per-tool replay projection

- [x] 4.1 为 default、`file_tool`、`deep_search` 先实现专属 projector，验证 native JSON 能投影回现有前端事件参数
- [x] 4.2 为其余 rich tool 实现 projector，并统一通过 registry 读取 `input_json + output_json + artifact`
- [x] 4.3 改造 `ReplayProjector` 或对应共享 replay 入口，只保留排序/分组语义，工具解析全部委托给 registry
- [x] 4.4 补充 replay 聚焦测试，验证未知工具会走 default projector，已知工具会走专属 projector

## 5. 对齐文档并完成全链路回归

- [x] 5.1 同步更新会话历史 replay 相关设计文档，明确 `output_json` 只存工具事实、展示参数由 projector 重建
- [x] 5.2 补充端到端测试，验证 React / PlanSolve 等主链路里的每条 tool invocation 都有 tool-native `output_json`
- [x] 5.3 运行后端聚焦回归，覆盖 builder、runtime、rich tool、projector 与 replay 相关测试
