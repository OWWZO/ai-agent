# AI Agent 对话 1:1 回显执行计划

## 目标
在现有代码已完成改造的基础上，按环境落地、联调、验收三个阶段推进上线，确保历史消息能够按原始事件顺序回放 `思考 -> 搜索 -> 再思考 -> 总结`，并兼容旧消息回退逻辑。

## 阶段一：数据库与后端部署
1. 在目标环境执行 `schema.sql` 中的增量 DDL，创建 `ai_agent_message_event` 表，并为 `ai_agent_message`、`ai_agent_conversation` 增加新字段。
2. 启动后端服务后验证 MyBatis 映射是否生效，重点检查新增 mapper、实体字段和 VO 返回值。
3. 手工验证消息占位写入时是否带上 `started_at`，终态更新时是否写入 `render_snapshot_json`、`metrics_json`、`finished_at`。

## 阶段二：前后端联调
1. 发送一条 `PLAN_SOLVE` 消息，确认流式过程中前端仍走旧渲染路径，不卡住、不闪烁。
2. 等消息完成后刷新页面，确认历史消息走 `TimelineReplay`，并且能按事件顺序显示多段 `plan_thought`、`task`、`deep_search`、`result`。
3. 发送一条 `REACT` 消息，确认 `tool_thought`、`tool_result`、`browser`、`file` 等节点能正常回放。
4. 验证 `taskId = null` 的工具项是否以独立卡片展示，匹配不到 `chat.tasks` 时 `SimpleToolCard` 仍可读可点。
5. 验证旧消息在没有 `render_snapshot_json` 时仍走 fallback，不影响历史查看。

## 阶段三：异常场景验收
1. 模拟上游异常或中途断流，确认消息会写入 `error` 状态的 partial snapshot，并能在历史中看到中断前进度。
2. 验证 `render_snapshot_json` 中的 `timeline` 顺序与 `ai_agent_message_event.seq_no` 一致。
3. 验证 `last_message_preview`、`message_count`、自动标题在 `completed / error / force_stop` 三类终态下都能更新。
4. 检查 workspace 类事件当前只在左侧时间线展示，不把“右侧精确恢复”纳入一期验收。

## 上线前检查清单
- 后端编译通过：`mvn -pl ai-agent-station-study-domain,ai-agent-station-study-trigger,ai-agent-station-study-app -am -DskipTests compile`
- 前端构建通过：`pnpm build`
- 数据库唯一约束 `uk_request_seq` 生效
- 新消息历史回放正常
- 旧消息 fallback 正常
- 异常消息可见 partial 过程

## 二期衔接建议
1. 新增 artifact 表并建立 `artifact_id` 关联。
2. 将 `workspace_realtime / workspace_browser / workspace_file` 事件与右侧工作区状态做精确绑定。
3. 为历史回放增加“点击时间线节点恢复右侧上下文”的精确联动能力。
