# Implementation Plan: 对话历史最终态重构与一致性修复

**Branch**: `005-fix-history-replay` | **Date**: 2026-04-16 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `D:\Java Code\ai-agent\wt-003-conversation-history-refactor\specs\005-fix-history-replay\spec.md`

## Summary

本次 005 的实现目标不是保留实时回放，而是把历史模型收敛为“对话结束时界面最终仍可见的细节真相源”。后端在消息完成时将思考过程、计划状态、任务分组、工具调用、搜索/总结卡片、最终答案和工作区产物投影为最终可见细节块，并以一条事件对应一个细节块的方式持久化到 `ai_agent_message_event`；`ai_agent_conversation` 与 `ai_agent_message` 只保留摘要与单轮账本职责。前端历史重开只消费这些最终态细节，不再从最小摘要或过程回放片段重新推理 UI。

## Technical Context

**Language/Version**: Java 17（后端主链路）；TypeScript 5 + React 19（`ui/`）  
**Primary Dependencies**: Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis / Mapper XML、MySQL 8、React 19、Vite 6、Ant Design 5  
**Storage**: MySQL（`ai_agent_conversation`、`ai_agent_message`、`ai_agent_message_event`）+ 现有文件服务稳定资源引用  
**Testing**: `ConversationHistoryPersistenceTest`、`ConversationHistoryDetailApiTest`、`ConversationHistoryArtifactTest`、`mvn -pl ai-agent-station-study-domain,ai-agent-station-study-trigger,ai-agent-station-study-app -am -DskipTests=false test`、`cd ui && npm run lint && npm run build`、Quickstart 手工场景 A-F  
**Target Platform**: Spring Boot 服务 + 浏览器前端历史详情页 / 工作区预览  
**Project Type**: Java 多模块后端 + React SPA 跨栈特性改造  
**Performance Goals**: 历史详情读取继续按 turn/event 顺序一次性返回最终细节；不因历史重开重新触发实时拼装或文件临时生成；同一轮多个同类细节块必须稳定返回  
**Constraints**: 严守 DDD 分层；不恢复实时回放模型；`PLAN_SOLVE` 与 `REACT` 采用结构化最终态历史，`CHAT` 保持轻量；旧历史数据允许直接删除；工作区预览只能依赖稳定引用  
**Scale/Scope**: 涉及三张历史表结构与语义、领域层最终态投影与装配、详情接口契约、前端历史恢复与文件预览体验

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] 变更遵守 `types/api/domain/infrastructure/trigger/app` 的职责边界
- [x] 优先复用了现有 Agent、Tool、Prompt、RAG、DAO、配置装配能力
- [x] 为每个关键改动点定义了可执行验证方式
- [x] 将外部调用、流式链路、任务编排的异常与可观测性纳入方案
- [x] 无额外宪章违例，不需要填写 `Complexity Tracking`

## Project Structure

### Documentation (this feature)

```text
specs/005-fix-history-replay/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── conversation-history-api.md
│   └── final-detail-event-payload.md
└── tasks.md
```

### Source Code (repository root)

```text
ai-agent-station-study-app/
├── src/main/resources/db/
└── src/main/resources/mybatis/mapper/
ai-agent-station-study-domain/
└── src/main/java/org/wwz/ai/domain/agent/reactor/
ai-agent-station-study-trigger/
└── src/main/java/org/wwz/ai/trigger/http/agent/
ui/
├── src/hooks/
├── src/pages/Home/
├── src/services/
└── src/utils/
```

**Structure Decision**: `domain` 负责最终态细节投影、payload 归一化和历史装配；`app` 负责 `schema.sql` 与 Mapper XML；`trigger` 只负责详情接口与 VO 映射；`ui` 负责按最终态事件恢复时间线与工作区，不再自行从摘要事件推断真实状态。

## Module Impact Matrix

| Module | Change Type | Why It Is Needed |
|--------|-------------|------------------|
| `ai-agent-station-study-domain` | modify | 收敛事件语义、在消息完成时投影最终可见细节、按 `seq_no` 组装历史详情 |
| `ai-agent-station-study-app` | modify | 同步三张历史表字段职责、默认值、索引与 Mapper XML |
| `ai-agent-station-study-trigger` | modify | 输出最终态 `turns[].events[]` 契约与 artifact 缺失态 |
| `ui` | modify | 历史重开只消费最终态细节事件，恢复计划完成态、多个工具细节和工作区预览 |
| `ai-agent-station-study-infrastructure` | none | 当前实现依赖现有 DAO/Mapper 模式，无需新增独立基础设施模块 |
| `reactor-tool` | none | 本次不改 Python 工具链，只复用已有稳定文件引用能力 |

## Layer Boundary Notes

- `domain` 内新增或调整最终态投影、事件归一化、历史装配逻辑，不把 UI 组件状态判断下沉到 `trigger`。
- `trigger` 仅输出接口 VO 和兼容字段（如 `isFinal=1`、`messageIdExt` 派生值），不承担事件筛选规则。
- `app` 负责数据库结构、Mapper XML 和测试装配，不在配置层做事件语义判断。
- `ui` 不再把“搜索完成 / 总结完成 / 最终回复”这种摘要当作唯一真相，而是直接消费后端给出的最终细节块。
- 同一份既出现在时间线又出现在工作区的内容只保留一条 canonical 记录，通过 `displayArea + payload.presentation + artifactRefs` 恢复多区域关系。

## Data / Config / Contract Changes

- **Database**: 收敛 `ai_agent_conversation` 为列表摘要表、`ai_agent_message` 为单轮账本、`ai_agent_message_event` 为最终界面细节表；清理仅服务于实时回放的冗余字段与索引；保持 `(message_id, seq_no)` 唯一。
- **Config**: 无新增运行时配置；继续复用现有文件服务稳定 URL / key。
- **Contract**: `GET /api/agent/conversation/detail` 继续返回 `turns[].events[]`，但事件语义改为最终可见细节块；保留 `messageIdExt`、`isFinal` 兼容输出；`artifactRefs[]` 成为工作区结果的 canonical 表达。
- **Compatibility**: 不做旧历史迁移与双路径兼容；切换前允许清空旧数据；`PLAN_SOLVE` 与 `REACT` 必须返回结构化最终态细节，`CHAT` 继续轻量。

## Verification Plan

- **Java**: `mvn -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ConversationHistoryPersistenceTest,ConversationHistoryDetailApiTest,ConversationHistoryArtifactTest test`
- **Java Full Regression**: `mvn -pl ai-agent-station-study-domain,ai-agent-station-study-trigger,ai-agent-station-study-app -am -DskipTests=false test`
- **UI**: `cd ui && npm run lint`；`cd ui && npm run build`
- **Python**: N/A
- **Manual**: 按 `quickstart.md` 执行场景 A-F，重点核对 1:1 最终细节恢复、plan 完成态不回退、工作区可预览或显示明确缺失原因、`REACT` 无回归

## Complexity Tracking

无。当前方案通过收敛历史语义降低复杂度，没有引入需要额外豁免的宪章违例。
