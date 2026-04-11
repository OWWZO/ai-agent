# Implementation Plan: Fix 模式 AI 角色库

**Branch**: `[001-fix-role-library]` | **Date**: 2026-04-11 | **Spec**: `/specs/001-fix-role-library/spec.md`
**Input**: Feature specification from `/specs/001-fix-role-library/spec.md`

## Summary

基于现有 `ai_agent`、`ai_agent_flow_config` 和 Fix 执行链路，新增一个只读的 Fix 角色库视图；在 chat 模式中允许用户选择角色，并把角色绑定持久化到 `ai_agent_conversation`。聊天执行改为从会话绑定角色解析 Fix 流程配置，移除 `FixedAgentExecuteStrategy` 中写死的 `"1"`，同时通过默认角色配置和首发消息兜底保证旧聊天链路平滑兼容。

## Technical Context

**Language/Version**: Java 17（后端）；TypeScript 5 + React 19（`ui/`）  
**Primary Dependencies**: Spring Boot 3.4.3, Spring AI 1.1.4, MyBatis/MyBatis-Plus 风格 DAO + Mapper XML, OkHttp SSE, React 19, Vite 6, Ant Design 5, Radix UI  
**Storage**: MySQL（`ai_agent` / `ai_agent_flow_config` / `ai_agent_conversation` / `ai_client*`）  
**Testing**: Maven 测试（重点覆盖 Fix 策略、DAO、会话服务）；`ui` 的 `pnpm lint` / `pnpm build`；聊天链路手工冒烟  
**Target Platform**: Spring Boot 服务 + 浏览器端 SPA  
**Project Type**: Maven 多模块后端 + Vite 前端联动特性  
**Performance Goals**: 角色列表查询保持单次 DB 读；chat 首发链路不增加额外远程调用；Fix 对话首包延迟不显著高于现状  
**Constraints**: 保持 DDD 边界；`ai_agent` 仍是角色唯一事实源；不新增角色表；不允许会话内切换角色；默认角色必须稳定且始终排第一；旧 chat 会话需可兼容读取  
**Scale/Scope**: 影响 `domain` / `infrastructure` / `trigger` / `app` / `ui`；新增 1 个角色库接口，扩展 2 个现有会话接口 DTO 和 1 条 SSE 发送契约，调整 1 张会话表

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] 变更遵守 `types/api/domain/infrastructure/trigger/app` 的职责边界：角色可用性与默认角色解析放在 `domain`，SQL/Mapper 放在 `infrastructure`/`app`，Controller 仅做契约编排，UI 只持有展示与交互状态。
- [x] 优先复用了现有 Agent、Armory、会话持久化、Fix 策略、`GeneralInput`/`ChatView` 等能力；不新增平行角色主数据。
- [x] 为关键改动点定义了验证方式：Fix 角色列表过滤、会话绑定、SSE 首发消息兜底、历史会话恢复、角色失效拦截均在 Verification Plan/Quickstart 中有对应路径。
- [x] 将异常与可观测性纳入方案：无可用角色、角色失效、会话内切换角色、聊天首发时会话不存在等场景都有明确兜底。
- [x] 本方案未引入必须额外说明的复杂度升级，`Complexity Tracking` 保持空。

## Project Structure

### Documentation (this feature)

```text
specs/001-fix-role-library/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── conversation.md
│   ├── message-send.md
│   └── role-library.md
└── tasks.md
```

### Source Code (repository root)

```text
ai-agent-station-study-api/
ai-agent-station-study-app/
├── src/main/java/org/wwz/ai/config/
├── src/main/resources/
│   ├── application-*.yml
│   ├── db/
│   └── mybatis/mapper/
└── src/test/java/org/wwz/ai/test/
ai-agent-station-study-domain/
├── src/main/java/org/wwz/ai/domain/
└── src/test/java/
ai-agent-station-study-infrastructure/
├── src/main/java/org/wwz/ai/infrastructure/
└── src/test/java/
ai-agent-station-study-trigger/
├── src/main/java/org/wwz/ai/trigger/
└── src/test/java/
ai-agent-station-study-types/
ui/
├── src/
└── package.json
```

**Structure Decision**: 这是一个后端主导、前端联动的棕地特性。角色可用性判断、默认角色解析、会话角色绑定和 Fix 执行入口调整放在后端；前端只新增 chat 模式角色选择组件与状态同步，不改动深度思考 / deep research / dataAgent 的主链路。

## Module Impact Matrix

| Module | Change Type | Why It Is Needed |
|--------|-------------|------------------|
| `ai-agent-station-study-domain` | modify | 新增角色库领域服务/解析器；扩展会话服务与 `AgentStreamPersistServiceImpl`；让 `FixedAgentExecuteStrategy` 按会话绑定角色执行 |
| `ai-agent-station-study-infrastructure` | modify | 为 Fix 角色库增加专用查询/投影；补充 `ai_agent` / `ai_agent_flow_config` 的聚合读取实现 |
| `ai-agent-station-study-trigger` | modify | 新增角色库接口；扩展会话创建/列表/详情/发送消息 VO 与 Controller 映射 |
| `ai-agent-station-study-app` | modify | 更新 `schema.sql`、`ai_agent_conversation_mapper.xml`、相关 Mapper/配置属性，接入默认角色配置 |
| `ui` | modify | 新增 chat 角色选择器、角色列表请求、会话角色状态、SSE 发送参数和历史恢复展示 |
| `ai-agent-station-study-api` | none | 本次前端走 trigger 层现有接口风格，不强制扩 API 层公共契约 |
| `ai-agent-station-study-types` | none | 不需要新增跨模块基础类型 |

## Layer Boundary Notes

- `domain` 负责角色是否可入库、默认角色是谁、会话该绑定哪个角色、会话内是否允许切换等业务规则。
- `infrastructure` 负责把 `ai_agent`、`ai_agent_flow_config`、`ai_client` 等表组合成“可用于 Fix 角色库”的数据投影，不在 SQL 层写默认角色与会话切换规则。
- `trigger` 只暴露 `role-library/list`、扩展会话/消息接口，并把领域对象映射为前端需要的 VO。
- `app` 只做 Mapper XML、`schema.sql` 和默认角色配置装配，不承载业务判断。
- `ui` 通过一个可复用的 chat 角色选择组件承接欢迎页与会话页，避免在多个页面分别拼装角色逻辑。

## Data / Config / Contract Changes

- **Database**:
  - `ai_agent_conversation` 增加 `ai_agent_id`、`ai_agent_name_snapshot` 两个字段。
  - 现有历史 chat 会话允许这两个字段为空；读取时按默认角色回退，继续对话时按需懒补齐。
- **Config**:
  - 新增可选配置 `spring.ai.agent.chat.default-role-id`，显式指定“现有 chat 角色”。
  - 当配置缺失或对应角色已失效时，后端自动回退到当前角色库中的首个可用角色。
- **Contract**:
  - 新增 `GET /api/agent/role-library/list`。
  - `ConversationCreateReqVO` 新增可选 `aiAgentId`。
  - `ConversationListRespVO` / `ConversationDetailRespVO` 新增会话绑定角色摘要。
  - `MessageSendReqVO` 新增可选 `aiAgentId`，用于首发消息创建会话或校验是否试图在原会话切换角色。
- **Compatibility**:
  - 非 chat 模式不受影响。
  - 旧 chat 会话在没有角色绑定字段时仍按默认角色展示和继续。
  - 未改造到位的旧聊天入口即使不传 `aiAgentId`，也会默认走当前默认角色，保持已有行为不断档。

## Verification Plan

- **Java**:
  - 增加/更新 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/domain/FixedAgentExecuteStrategyTest.java`
  - 为角色库查询与会话角色绑定补充 DAO/Service 测试（可放在 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/dao/` 与会话服务相关测试类中）
  - 至少执行 `mvn test -pl ai-agent-station-study-app -Dtest=FixedAgentExecuteStrategyTest,AiAgentDaoTest,AiAgentFlowConfigDaoTest`
- **UI**:
  - 在 `ui/` 执行 `pnpm lint`
  - 在 `ui/` 执行 `pnpm build`
- **Manual**:
  - chat 模式未手选角色直接发消息，应绑定默认角色
  - chat 模式手选其他角色后发消息，应使用所选角色
  - 刷新页面/进入历史会话，应恢复绑定角色
  - 在已有消息会话中改选其他角色，应新建会话而不是污染原会话
  - 角色被停用后，历史会话仍可显示角色名，但继续发送会被阻止并给出清晰提示

## Complexity Tracking

无。
