# Implementation Plan: 当前轮图片接入 ReAct / PlanSolve 图生图链路

**Branch**: `[015-session-image-edit-bridge]` | **Date**: `2026-05-01` | **Spec**: 当前对话中已确认的方案  
**Input**: 基于“在 ReAct / PlanSolve 模式下，用户当前轮上传图片后，智能体可复用这些图片调用 `image_generation_tool` 完成图生图”的已确认改造方案

## Summary

当前 UI 已支持会话附件上传，但 SSE 请求仍把附件放在 `filesJson` 字符串中发送，`/web/api/v1/gpt/queryAgentStreamIncr` 对应的 `GptQueryReq` 与 `MultiAgentServiceImpl.buildAgentRequest(...)` 没有把这些附件桥接到 `AgentRequest.sessionFiles`。本次改造聚焦非 `chat` 的 `ReAct / PlanSolve` 链路：把用户当前轮上传后的图片元数据以强类型 `sessionFiles` 贯通到 `AgentContext.productFiles`，并增强 `ImageGenerationTool` 的图生图兜底逻辑，使用户在研究/交付模式中上传图片后，智能体能够稳定使用当前轮图片完成图生图，同时保持显式文生图参数的优先级不变。本期明确不覆盖 `chat/WORKFLOW` 路径，也不承诺跨轮会话图片复用。

## Technical Context

**Language/Version**: Java 17（后端主链路） + TypeScript 5 / React 19（`ui/`）  
**Primary Dependencies**: Spring Boot 3.4.3、Spring AI 1.1.4、OkHttp SSE、FastJSON 1.2.83、React 19、Vite 6、Vitest  
**Storage**: 复用现有文件服务会话附件存储与 `ai_agent_artifact` 输入附件登记能力；不新增数据库表或字段  
**Testing**: `mvn test -pl ai-agent-station-study-app -DskipTests=false -Dtest=ImageGenerationToolTest,MultiAgentServiceImplTest`；`cd ui && npm run test -- src/utils/agentRequest.test.ts`；`cd ui && npm run build`  
**Target Platform**: Web 非 `chat` 请求入口 `/web/api/v1/gpt/queryAgentStreamIncr` 与 Reactor Java Agent 执行链  
**Project Type**: Maven 多模块后端 + Vite SPA 的小范围跨栈改造  
**Performance Goals**: 不增加新的上传轮次；非 `chat` 请求仅附带必要的当前轮附件元数据；不改动 Python 生图服务调用次数与响应模式  
**Constraints**: 严守 DDD 分层；优先复用现有 `sessionFiles -> productFiles`、`ExecutionLedgerRunSupport`、`ImageGenerationTool` 和文件服务约定；显式 `mode=images` 必须优先于自动图生图兜底；本期不改 `chat/WORKFLOW` 执行策略  
**Scale/Scope**: 影响 `ui` 的非 `chat` 请求组包、`domain` 的请求模型和图片工具选择逻辑、`app` 的工具描述配置与测试；`trigger`、`infrastructure`、`reactor-tool`、`chat/WORKFLOW` 主流程不改

## Constitution Check

*GATE: Must pass before implementation starts.*

- [x] 变更遵守 `types/api/domain/infrastructure/trigger/app` 的职责边界：前端负责上传元数据到 SSE 请求，`domain` 负责请求桥接与工具策略，`app` 负责配置和测试装配
- [x] 优先复用了现有 `sessionFiles -> productFiles`、执行账本输入附件登记与 `image_generation_tool` 能力，没有平行新增新的生图入口
- [x] 已为关键改动点定义可执行验证方式：UI 请求映射测试、后端请求桥接测试、工具图生图兜底测试、手工端到端验证
- [x] 不改变外部文件服务和 Python 生图接口契约，只在 Java/UI 的 ReAct / PlanSolve 主链路补桥接与兜底
- [x] 当前方案没有必须额外说明的复杂度违例

## Project Structure

### Documentation (this feature)

```text
specs/015-session-image-edit-bridge/
└── plan.md
```

### Source Code (repository root)

```text
ui/
├── src/components/ChatView/
├── src/components/GeneralInput/
├── src/types/
└── src/utils/
ai-agent-station-study-domain/
└── src/main/java/org/wwz/ai/domain/agent/reactor/
    ├── agent/tool/common/
    ├── model/dto/
    ├── model/req/
    └── service/impl/
ai-agent-station-study-app/
├── src/main/resources/
└── src/test/java/org/wwz/ai/test/domain/
```

**Structure Decision**: 前端新增一个独立的请求映射 helper，把当前轮 `CHAT.TFile[]` 转成后端 `sessionFiles` 结构，避免继续把协议拼装逻辑堆在 `ChatView` 组件内；后端继续复用既有 `GptQueryReq -> AgentRequest -> productFiles` 的 ReAct / PlanSolve 主链路，不新增额外服务层或并行 DTO，也不触碰 `chat/WORKFLOW`。

## Module Impact Matrix

| Module | Change Type | Why It Is Needed |
|--------|-------------|------------------|
| `ui` | modify | 非 `chat` 请求不再依赖 `filesJson`，改为发送强类型 `sessionFiles`；补当前轮附件映射 helper 与前端单测 |
| `ai-agent-station-study-domain` | modify | `GptQueryReq` 增加 `sessionFiles`，`MultiAgentServiceImpl` 把当前轮附件桥接到 `AgentRequest.sessionFiles`，`ImageGenerationTool` 增加图生图兜底与显式模式优先逻辑 |
| `ai-agent-station-study-app` | modify | 更新 `application-dev.yml` 中 `image_generation_tool` 的描述与参数提示，补 Java 回归测试 |
| `ai-agent-station-study-trigger` | none | 控制器仍按 JSON 直接接收请求对象，不需要单独解析 `sessionFiles` |
| `ai-agent-station-study-infrastructure` | none | 文件上传与预览 URL 约定已满足本次桥接，不需要新增网关逻辑 |
| `reactor-tool` | none | Python 端已按 `requestId/sessionId + fileName` 解析参考图预览地址，本次不改 |

## Layer Boundary Notes

- `ui`
  - 负责把当前轮上传成功后的附件元数据转成后端请求结构
  - 不在组件中硬编码后端字段细节，转换逻辑收敛到 helper
- `domain`
  - `GptQueryReq` 只承载当前轮附件元数据，不承担上传行为
  - `MultiAgentServiceImpl` 只做请求桥接，不重新实现文件解析或文件服务访问
  - `ImageGenerationTool` 只在 ReAct / PlanSolve 链路里对“未显式指定文生图且未传 fileNames”场景复用当前轮图片，不覆盖明确的 `mode=images`
- `app`
  - 负责工具描述、参数提示和测试装配
- 明确禁止
  - 在 `trigger` 层单独解析 `filesJson`
  - 在 `MultiAgentServiceImpl` 中重新拼接文件服务 URL
  - 为图生图单独新增新的 Controller 或直接绕开 Agent 主链路
  - 顺手把 `chat/WORKFLOW` 改造成支持工具调用

## Data / Config / Contract Changes

- **Database**: N/A；继续复用现有 `ai_agent_artifact` 输入附件登记逻辑
- **Config**:
  - 更新 `ai-agent-station-study-app/src/main/resources/application-dev.yml` 中 `image_generation_tool` 的 `desc`
  - 必要时同步 `params.fileNames` 描述，明确“未显式传入时可自动复用当前轮上传图片”
- **Contract**:
  - `ui` 发往 `/web/api/v1/gpt/queryAgentStreamIncr` 的请求体新增 `sessionFiles`
  - `GptQueryReq` 新增 `List<FileInformation> sessionFiles`
  - 非 `chat` 链路不再依赖 `filesJson`
- **Compatibility**:
  - `sessionFiles` 保持可选，旧客户端不传时仍可继续文生图与普通对话
  - `ImageGenerationTool` 仅在安全条件下触发当前轮图片兜底，不破坏已有显式文生图调用

## Verification Plan

- **Java**:
  - `mvn test -pl ai-agent-station-study-app -DskipTests=false -Dtest=ImageGenerationToolTest,MultiAgentServiceImplTest`
- **UI**:
  - `cd ui && npm run test -- src/utils/agentRequest.test.ts`
  - `cd ui && npm run build`
- **Python**: N/A
- **Manual**:
  - 在 `ReAct` 或 `PlanSolve` 模式下，上传 1 张图片并输入“基于这张图改成赛博朋克风”，确认智能体调用 `image_generation_tool` 且为图生图
  - 在 `ReAct` 或 `PlanSolve` 模式下，上传 1 张图片并输入“不要参考上传图，重新生成一张猫咪海报”，确认显式文生图仍可走 `images`
  - 在 `ReAct` 或 `PlanSolve` 模式下，不上传图片直接输入生图需求，确认普通文生图能力不回归
  - 切换到 `chat` 模式验证现状不变，本期不要求 `chat` 具备图生图能力

## Phase 0: Research Summary

- 前端上传链路已存在：`GeneralInput` 会先调用 `agentFileApi.uploadConversationFile(sessionId, file)` 上传文件，再把上传结果保存为 `CHAT.TFile`。[`ui/src/components/GeneralInput/index.tsx`]
- 当前 SSE 请求仍发送 `filesJson` 字符串，而不是后端可直接消费的 `sessionFiles` 结构。[`ui/src/components/ChatView/index.tsx`]
- `/web/api/v1/gpt/queryAgentStreamIncr` 入口当前接收 `GptQueryReq`，其中没有 `sessionFiles` 字段。[`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/req/GptQueryReq.java`]
- `MultiAgentServiceImpl.buildAgentRequest(...)` 目前只桥接基础查询字段，没有把会话附件带到 `AgentRequest.sessionFiles`。[`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/MultiAgentServiceImpl.java`]
- `RootNode` 与 `Step1SopRecallAndPrepareNode` 已支持把 `request.getSessionFiles()` 转成 `productFiles`，ReAct / PlanSolve 主链路可以直接复用。[`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/react/step/RootNode.java`][`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/service/execute/planexecute/step/Step1SopRecallAndPrepareNode.java`]
- `ImageGenerationTool` 已支持在 `mode=edits` 且 `fileNames` 为空时，从当前上下文 `productFiles` 中收集图片文件名；当前缺口是前面的上传图片并没有稳定进入 `productFiles`，以及 `mode` 为空时没有充分利用这层能力。[`ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/ImageGenerationTool.java`]
- `chat` 当前走 `WORKFLOW -> flowAgentExecuteStrategy`，不在本期范围内；该路径没有现成的 `sessionFiles -> productFiles` + 工具调用闭环，因此本期刻意排除。

## Phase 1: Design Decisions

### 1. 非 `chat` 请求改为发送强类型 `sessionFiles`

- 不再继续扩散 `filesJson` 这种“JSON 字符串套 JSON”的做法
- 前端把当前轮上传成功的 `CHAT.TFile` 映射为后端可直接消费的 `FileInformation` 结构
- 映射规则以稳定访问地址与资源标识为主：`fileName / domainUrl / ossUrl / fileSize / fileType / resourceKey / mimeType / originFileName`
- 本期不做按 `sessionId` 回补历史附件，不承诺跨轮复用

### 2. 后端只补 ReAct / PlanSolve 桥接，不改既有执行链

- `GptQueryReq` 新增 `sessionFiles`
- `MultiAgentServiceImpl.buildAgentRequest(...)` 直接 `request.setSessionFiles(req.getSessionFiles())`
- `RootNode / Step1SopRecallAndPrepareNode / ExecutionLedgerRunSupport` 现有逻辑不新增分叉，继续消费 `sessionFiles`
- `chat/WORKFLOW` 路径不纳入本期验收，不为它新增并行逻辑

### 3. `ImageGenerationTool` 在 ReAct / PlanSolve 中采用保守兜底策略

- 调用方显式传 `mode=images` 时，严格尊重文生图，不自动带入会话图片
- 调用方未传 `fileNames` 且当前轮上下文里存在图片附件时，自动补入图片文件名
- `mode` 为空时不强行写死为 `edits`，让现有下游“有参考图则推断为图生图”的逻辑生效
- `mode=edits` 但最终仍拿不到任何参考图时，保持失败语义，避免伪造图生图请求

### 4. 工具描述要明确引导 LLM 选择图生图

- 配置层描述要强调：当用户说“基于这张图修改 / 重绘 / 换风格 / 扩图”时，应优先调用 `image_generation_tool`
- 参数描述要强调：`fileNames` 可来自当前轮已上传图片，未显式传入时系统会自动复用
- 如存在非 `application-dev.yml` 环境依赖默认描述，则同步更新 `ImageGenerationTool` 默认描述，避免环境漂移

## Phase 2: Implementation Strategy

### User Story 1 - 当前轮上传图片可进入 ReAct / PlanSolve 请求上下文

- 新增前端请求映射 helper，例如 `ui/src/utils/agentRequest.ts`，把当前轮 `CHAT.TFile[]` 映射为后端 `sessionFiles`
- 修改 `ui/src/components/ChatView/index.tsx`，非 `chat` 请求体去掉 `filesJson` 依赖，改为发送 `sessionFiles`
- 复用现有上传成功后的 `CHAT.TFile`，不改 `AgentFileController` 和上传接口返回结构
- 新增前端单测，覆盖空附件、普通图片附件、带 `resourceKey/mimeType/originFileName` 的映射结果

### User Story 2 - `GptQueryReq` 到 `AgentRequest` 的当前轮附件桥接生效

- 修改 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/model/req/GptQueryReq.java`，新增 `List<FileInformation> sessionFiles`
- 修改 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/service/impl/MultiAgentServiceImpl.java`，在 `buildAgentRequest(...)` 中直接透传 `sessionFiles`
- 新增 `MultiAgentServiceImplTest.java`，验证：
  - `sessionFiles` 会被带入 `AgentRequest`
  - 无附件时行为不变
  - ReAct / PlanSolve 两种 agentType 分支都不会丢附件
  - `chat/WORKFLOW` 分支不作为本期能力承诺，但透传字段本身不回归

### User Story 3 - `image_generation_tool` 在当前轮图片存在时稳定走图生图

- 修改 `ai-agent-station-study-domain/src/main/java/org/wwz/ai/domain/agent/reactor/agent/tool/common/ImageGenerationTool.java`
- 在参数归一化阶段引入“显式文生图优先、当前轮图片保守兜底”的决策顺序
- 仅过滤图片扩展名白名单，继续复用现有 `collectContextImageFileNames()`，不新增第二套附件来源
- 扩展 `ImageGenerationToolTest.java`，覆盖：
  - `mode=edits` 且未传 `fileNames` 时自动复用当前轮图片
  - `mode` 为空且当前轮里有图片时，请求体会带上参考图
  - `mode=images` 时即使当前轮有图片也不自动走图生图

### User Story 4 - 提示词与回归验证同步到位

- 修改 `ai-agent-station-study-app/src/main/resources/application-dev.yml` 中 `image_generation_tool` 的 `desc` 与 `params`
- 必要时同步 `ImageGenerationTool` 默认描述，确保本地默认配置和显式配置语义一致
- 运行 UI 单测、Java 单测和手工链路验证，确认：
  - 上传图片后智能体能真正复用当前轮图片
  - 文生图场景不被误伤
  - 输入附件会继续进入执行账本输入产物链路
  - `chat` 模式行为保持现状，不被误改

## Post-Design Constitution Check

- [x] 边界清晰：上传文件仍由文件服务负责，请求仅承载当前轮元数据，工具策略只在 `domain` 层处理
- [x] 最大化复用现有能力：`sessionFiles -> productFiles`、执行账本输入附件登记、`ImageGenerationTool` 参考图收集逻辑均继续沿用
- [x] 每个关键变更点都有对应验证：前端映射、后端桥接、工具兜底、配置引导和手工端到端
- [x] 失败与兼容语义清晰：旧客户端不传 `sessionFiles` 仍可继续运行；显式文生图优先级高于自动图生图；`chat` 模式不被本期误伤
- [x] 没有引入新的并行入口或跨层耦合

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| N/A | N/A | N/A |
