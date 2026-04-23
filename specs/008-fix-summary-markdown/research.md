# Research: 修复 React/PlanSolve 最终总结 Markdown 展示

## Decision 1: 修复范围必须锁定在结构化模式最终总结入口

- **Decision**: 本期只修 `REACT / PLAN_SOLVE` 的最终总结展示入口，不把同一 Markdown 渲染链路上的普通聊天回复、文件预览或其他内容展示一并纳入修复范围。
- **Rationale**: 用户在 clarify 中已经明确要求选择最小范围闭环。如果直接把规则铺到共享渲染层，虽然实现更省事，但会把回归面扩展到整个 `ui/`。
- **Alternatives considered**:
  - 把新规则作为全局 Markdown 默认行为：回归风险过大，与澄清结果冲突。
  - 为 `REACT` 和 `PLAN_SOLVE` 各写一套规则：会产生重复逻辑，不利于维护。

## Decision 2: 实时流式、最终完成态和历史回放必须共用同一展示语义

- **Decision**: `agent_stream`、`task_summary` 和历史 fallback 都继续走 `ConclusionSection -> MarkdownRenderer` 这一条总结展示入口，并在进入渲染器前应用同一套总结专用规范化规则。
- **Rationale**: 当前问题的根源不是某一种消息类型单独渲染失败，而是结构化总结在不同阶段都可能遇到“近似 Markdown”。如果实时和回放走不同规则，用户刷新页面后会再次看到解析失败。
- **Alternatives considered**:
  - 只修实时链路：历史回放仍会失败。
  - 只修历史回放：用户在首次查看结果时仍然体验很差。

## Decision 3: 修正规则聚焦“近似 Markdown”，而不是重写整段内容

- **Decision**: 规范化只处理模型高频产出的近似 Markdown 问题，包括：
  - 标题标记后缺少空格，如 `###1）`
  - 列表标记后缺少空格，如 `-计划玩几天`
  - 标题或列表被粘连在上一句中文句尾后面，如 `...快速挑）##你如果...`
- **Rationale**: 用户给出的失败样本已经说明问题集中在“标记接近 Markdown，但不完全合法”。只修这些模式可以最大化提升可读性，同时避免引入“帮模型重写文案”的副作用。
- **Alternatives considered**:
  - 只保留现有断行修正：无法处理缺少空格导致的标题、列表不生效问题。
  - 做大规模文本重写：规则过重，容易改变原始内容语义。

## Decision 4: 规范化必须继续对代码块免打扰

- **Decision**: 继续以 fenced code block 为边界对正文分段，代码块内的 `#`、`-`、数字列表等字符一律不做 Markdown 修复。
- **Rationale**: 规格里已经把“代码块不能被误改”列为高优先级验收标准。当前 `normalizeMarkdownForDisplay` 已有代码块保护，这是本期最应该复用的安全边界。
- **Alternatives considered**:
  - 全文直接做正则替换：最容易把示例代码、命令输出和配置片段改坏。
  - 完全放弃代码块保护：与 P3 用户故事直接冲突。

## Decision 5: 修复发生在显示层，不改后端协议和持久化文本

- **Decision**: 不修改 SSE 事件结构、数据库中的历史内容或模型输出协议，所有修复都在前端展示前临时执行。
- **Rationale**: 规格已明确本期不改后端。显示层修复可以同时覆盖实时流式和历史回放，并且保持对现有 `task_summary / agent_stream` 契约的兼容。
- **Alternatives considered**:
  - 修改后端提示词强制输出严格 Markdown：见效慢，且不能解决已有历史数据的回放问题。
  - 在历史恢复时改写并回存文本：会让展示修复和持久化语义耦合过深。

## Decision 6: 验证沿用现有前端工具链，不额外引入测试框架

- **Decision**: 本期验证采用 `npm run lint`、`npm run build` 和围绕结构化总结的手工验收；不为这次小范围修复引入新的前端测试框架。
- **Rationale**: 当前 `ui/` 只有 lint/build 脚本，没有现成测试 runner。为一个展示修复临时增加新测试基础设施，会让改动范围明显超过需求本身。
- **Alternatives considered**:
  - 立即引入 Vitest/RTL：长期有价值，但不是这次问题的最小闭环。
  - 只做肉眼手测：缺少最基本的静态和构建回归保障。
