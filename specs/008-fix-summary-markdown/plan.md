# Implementation Plan: 修复 React/PlanSolve 最终总结 Markdown 展示

**Branch**: `[008-fix-summary-markdown]` | **Date**: `2026-04-23` | **Spec**: [spec.md](./spec.md)  
**Input**: Feature specification from `/specs/008-fix-summary-markdown/spec.md`

## Summary

本期仅修复 `ui/` 中 `REACT / PLAN_SOLVE` 最终总结的 Markdown 展示失败问题。技术路线是保留现有后端协议、历史持久化和共享 Markdown 渲染组件不变，通过“结构化总结专用规范化规则 + 明确的调用边界”让实时 `agent_stream`、最终 `task_summary` 与历史 fallback 在进入 `Streamdown / ReactMarkdown` 前得到一致修正，同时确保代码块、合法 Markdown 和非目标场景不被误改。

## Technical Context

**Language/Version**: TypeScript 5.7 + React 19（仅 `ui/`，本期不改 Java / Python 子系统）  
**Primary Dependencies**: Vite 6、Ant Design 5、react-markdown 10.1.0、remark-gfm 4.0.1、streamdown 2.5.0、现有 `ai-elements` 消息渲染组件链  
**Storage**: N/A（纯前端展示修复，不新增数据库、接口持久化或文件存储）  
**Testing**: `cd ui && npm run lint`、`cd ui && npm run build`，以及围绕 `REACT / PLAN_SOLVE` 最终总结的手工验收  
**Target Platform**: 浏览器端 `Dialogue -> MarkdownRenderer -> MessageResponse/ReactMarkdown` 结构化总结展示链  
**Project Type**: Vite SPA 前端功能修复  
**Performance Goals**: 规范化处理保持线性文本扫描，不新增网络请求和持久化写入；流式总结渲染不出现可见卡顿；非目标场景保持现有行为  
**Constraints**: 修复范围严格限定在 `REACT / PLAN_SOLVE` 最终总结入口；必须兼容实时流式与历史回放；必须保护 fenced code block、内联代码和已合法 Markdown；不得要求后端协议、提示词或数据库同步修改  
**Scale/Scope**: 主要影响 `ui/src/utils/markdown.ts`、`ui/src/components/ActionPanel/MarkdownRenderer.tsx`、`ui/src/components/Dialogue/index.tsx`，必要时补充少量总结来源判定辅助逻辑；`agentConversation.ts` 只保持现有历史 fallback 契约，不做接口变更

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] 变更是否遵守 `types/api/domain/infrastructure/trigger/app` 的职责边界？
- [x] 是否优先复用了现有 Agent、Tool、Prompt、RAG、DAO、配置装配能力？
- [x] 是否为每个关键改动点定义了可执行验证方式？
- [x] 是否将外部调用、流式链路、任务编排的异常与可观测性纳入方案？
- [x] 若提高了复杂度，是否在 `Complexity Tracking` 中给出合理说明？

## Project Structure

### Documentation (this feature)

```text
specs/008-fix-summary-markdown/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── structured-summary-rendering.md
│   └── summary-normalization-rules.md
└── tasks.md
```

### Source Code (repository root)

```text
ui/
├── src/
│   ├── components/
│   │   ├── ActionPanel/
│   │   │   └── MarkdownRenderer.tsx
│   │   ├── Dialogue/
│   │   │   └── index.tsx
│   │   └── ai-elements/
│   │       └── message.tsx
│   ├── services/
│   │   └── agentConversation.ts
│   ├── types/
│   │   ├── chat.ts
│   │   └── message.ts
│   └── utils/
│       └── markdown.ts
└── package.json
```

**Structure Decision**: 本期限定在 `ui/` 交付。`utils/markdown.ts` 负责结构化总结专用的展示前规范化；`Dialogue/index.tsx` 负责把规范化能力限定在 `REACT / PLAN_SOLVE` 最终总结入口；`MarkdownRenderer.tsx` 继续承担统一渲染职责，但只在显式启用时应用总结修正规则；`ai-elements/message.tsx` 与 `agentConversation.ts` 保持既有协议和渲染组件链，不承担新业务分支。

## Module Impact Matrix

| Module | Change Type | Why It Is Needed |
|--------|-------------|------------------|
| `ui` | modify | 修复结构化模式最终总结的实时流式、最终完成态和历史回放 Markdown 展示 |
| `ai-agent-station-study-domain` | none | 后端输出协议与任务编排保持兼容 |
| `ai-agent-station-study-infrastructure` | none | 不涉及 DAO、存储或网关改动 |
| `ai-agent-station-study-trigger` | none | 不涉及 Controller 或 SSE 接口契约改动 |
| `ai-agent-station-study-app` | none | 不涉及配置、Mapper 或测试基建新增 |
| `reactor-tool` / `reactor-client` | none | 不涉及 Python 子系统 |

## Layer Boundary Notes

- 本期是纯展示层修复，问题必须在 `ui/` 内解决，不把“近似 Markdown 修正”下沉到后端提示词或持久化层。
- `Dialogue` 负责识别“结构化模式最终总结”这一业务边界，并显式传递总结渲染上下文；不得让所有 `MarkdownRenderer` 调用点默认共享本期修复。
- `MarkdownRenderer` 继续只做渲染装配与滚动控制；真正的文本修正规则集中在 `utils/markdown.ts`，避免多个组件各自写正则。
- `agentConversation.ts` 继续负责历史 fallback 的数据恢复，不改 `task_summary / agent_stream` 数据契约，只确保回放路径最终进入相同展示入口。

## Data / Config / Contract Changes

- **Database**: N/A
- **Config**: N/A
- **Contract**: 新增内部 UI 渲染契约，定义结构化总结来源、规范化规则边界和非目标场景隔离策略；对外 HTTP/SSE 契约保持不变
- **Compatibility**: 现有 `task_summary`、`agent_stream`、历史 fallback 数据结构保持兼容；普通 `CHAT` 回复、文件预览、HTML/Markdown 其他渲染场景不得出现可见回归

## Verification Plan

- **Java**: N/A
- **UI**:
  - `cd ui && npm run lint`
  - `cd ui && npm run build`
- **Python**: N/A
- **Manual**:
  - 在 `REACT` 模式下生成包含 `###1）`、`##你如果想要...`、`-计划玩几天` 的最终总结，验证实时展示结构正确
  - 在 `PLAN_SOLVE` 模式下完成同类任务，验证最终完成态仍保持标题、段落和列表结构
  - 刷新页面或重新进入历史会话，验证历史 fallback 的总结展示与实时结果一致
  - 额外抽查普通 `CHAT` 回复、文件预览或其他 `MarkdownRenderer` 调用场景，确认未被本期规则误伤

## Phase 0: Research Summary

- 已确定本期关键未知点：修复边界如何限制在结构化总结区域、流式与历史链路如何共用同一套修正规则、近似 Markdown 应修到什么程度才既可读又不误伤代码块
- 研究结论已固化到 [research.md](./research.md)，没有遗留 `NEEDS CLARIFICATION`
- 研究结果直接支撑 Phase 1 的数据模型、内部 UI 契约和 Phase 2 任务拆分

## Phase 1: Design Decisions

### 1. Summary-Scoped Normalization Boundary

- 新增“结构化总结专用”规范化边界，只允许 `REACT / PLAN_SOLVE` 最终总结显式启用
- 如果 `MarkdownRenderer` 继续复用共享工具函数，必须通过独立 profile / option 控制，而不是把新规则默认为全局 Markdown 行为

### 2. Unified Rendering Semantics for Stream / Final / History

- 实时 `agent_stream`、最终 `task_summary`、历史 fallback 都通过 `ConclusionSection -> MarkdownRenderer` 同一展示入口
- 同一份原始总结文本在 streaming 与 final render 下必须得到等价的结构结果，避免“流式可读、完成后退化”或“实时正常、回放异常”

### 3. Repair Rules Focused on Near-Markdown

- 修正规则只覆盖高频近似 Markdown 问题：
  - 标题标记后缺少空格，如 `###1）`
  - 列表标记后缺少空格，如 `-计划玩几天`
  - 标题或列表被粘连在中文句尾后面，如 `...快速挑）##你如果...`
- 代码块分段保护继续保留，且合法 Markdown 不应被二次改写

### 4. Display-Time Repair Instead of Data Mutation

- 不修改 SSE 事件、历史持久化数据或后端返回文本
- 所有修复都发生在渲染前的临时文本规范化阶段，确保回放、复制、重渲染都基于同一显示语义，而不是产生新的持久化分叉

### 5. Verification Strategy Within Existing Toolchain

- 不为这次小范围前端修复引入新的测试框架
- 通过 `lint/build + 结构化总结手工场景 + 非目标场景回归检查` 形成闭环，保持改动成本和验证成本匹配

## Phase 2: Implementation Strategy

### User Story 1 - 结构化模式最终总结能正确排版

- 在 `Dialogue` 的最终总结入口显式标记“结构化总结渲染上下文”
- 扩展总结专用规范化规则，覆盖标题缺少空格、列表缺少空格、标题/列表粘连句尾的近似 Markdown 场景
- 确保 streaming 与 non-streaming 都使用同一套总结规范化结果

### User Story 2 - 历史回放后的最终总结保持相同展示效果

- 复用现有 `buildHistoryConclusionFallback` 与 `ConclusionSection` 链路，不新增历史专用渲染分支
- 确保历史 fallback 恢复出来的总结文本进入同一总结渲染入口
- 验证刷新、重进会话和历史详情场景下展示结果一致

### User Story 3 - 修复展示问题时不破坏合法 Markdown 与代码内容

- 保留 fenced code block 分段跳过策略，避免误改代码示例
- 把新规则限定为“总结专用”而非“全局 Markdown 默认规则”
- 对已合法 Markdown 和非目标场景做回归验证，避免引入新的展示偏差

## Post-Design Constitution Check

- [x] 设计仍遵守 DDD 分层边界，未把展示问题扩散到后端协议与持久化层
- [x] 复用了现有 `Dialogue`、`MarkdownRenderer`、`MessageResponse` 和 Markdown 工具链
- [x] 已为 lint/build/manual 总结场景和非目标场景回归定义验证路径
- [x] 已把流式链路、历史回放和边界保护纳入方案，没有只修单一路径
- [x] 复杂度提升仅体现在“总结专用规范化边界”和少量修正规则，理由清晰且必要

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| N/A | N/A | N/A |
