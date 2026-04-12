# Implementation Plan: Agent Skill Mechanism

**Branch**: `[002-agent-skill-mechanism]` | **Date**: 2026-04-12 | **Spec**: [spec.md](D:\Java Code\ai-agent\ai-agent-station-study\specs\002-agent-skill-mechanism\spec.md)
**Input**: Feature specification from `/specs/002-agent-skill-mechanism/spec.md`

**Note**: This document captures design and implementation planning only. It does not imply code has been written.

## Summary

为 `PlanSolve/ReAct` 链路引入兼容主流 `SKILL.md` 目录约定的 skill 机制：Java 侧新增运行时 skill 注册中心、`skill_tool`、`script_runner_tool` 与本地只读 skill 文件工具族，统一接入现有 `BaseTool + ToolCollection` 装配链；Python `reactor-tool` 侧新增脚本执行端点，负责在最小边界下执行已注册 skill 脚本。V1 聚焦“主流 skill 可安装、可发现、可读取、可执行”，不改 `Fix` 链路，不引入完整沙箱，不处理依赖自动安装与缓存。

## Technical Context

**Language/Version**: Java 17（Spring Boot 多模块主链路）；Python 3.11+（`reactor-tool`）  
**Primary Dependencies**: Spring Boot 3.4.3、Spring AI 1.1.4、MyBatis-Plus 3.5.14、FastJSON 1.2.83、OkHttp 4.9.3、FastAPI、Pydantic v2、PyYAML  
**Storage**: 文件系统中的运行时 skill 目录；现有文件服务用于脚本输出文件上传；无新增数据库表  
**Testing**: Java 侧针对 registry / parser / path guard / tool factory 的单元测试与 Maven 编译验证；Python 侧针对 `script_runner` 路由与 runtime executor 的最小冒烟测试；端到端人工验证 `PlanSolve/ReAct` 调用链  
**Target Platform**: Spring Boot 服务 + `reactor-tool` FastAPI 服务  
**Project Type**: 跨栈 feature（Maven 多模块后端 + Python 工具服务）  
**Performance Goals**: skill 扫描仅在启动或显式刷新时进行；skill 读取工具返回控制在可读截断范围内；脚本执行默认具备超时控制且不阻塞其他工具调用链  
**Constraints**: 必须复用现有 `BaseTool + ToolCollection` 与节点装配方式；`Fix` 链路保持不变；V1 仅允许执行已注册脚本并限制在已注册 skill 根目录内访问；不引入完整容器沙箱  
**Scale/Scope**: 影响 `ai-agent-station-study-domain`、`ai-agent-station-study-app`、`reactor-tool` 与 feature 文档；不涉及 `ui`、数据库 schema、MCP 管理后台

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
specs/002-agent-skill-mechanism/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── skill-tool-contract.md
│   └── script-runner-api.md
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
mcp-server-csdn/mcp-server-csdn/
ui/
reactor-client/
reactor-tool/
├── reactor_tool/api/
├── reactor_tool/model/
└── reactor_tool/tool/
runtime/skills/
```

**Structure Decision**: 保持 Java 主能力落在 `ai-agent-station-study-domain` 的 reactor/tool 相关区域，`app` 仅承担配置绑定与资源配置，`reactor-tool` 负责脚本执行端点与 runtime adapter；新增运行时 skill 目录使用仓库独立路径 `runtime/skills/`，不复用 `.agents/skills/`。

## Module Impact Matrix

| Module | Change Type | Why It Is Needed |
|--------|-------------|------------------|
| `ai-agent-station-study-domain` | modify | 新增 skill 注册中心、工具实现、工具装配工厂、脚本执行 DTO、路径边界控制 |
| `ai-agent-station-study-app` | modify | 新增 skill 配置项、示例配置与必要测试装配 |
| `reactor-tool` | modify | 新增 `/v1/tool/script_runner` 路由、请求模型、runtime executor、输出文件上传逻辑 |
| `specs/002-agent-skill-mechanism` | modify | 补齐 research、data-model、contracts、quickstart |
| `ui` | none | 本期无前端管理页面 |
| `ai-agent-station-study-trigger` | none | 不新增 HTTP 接口，仅复用现有 agent 入口 |
| `ai-agent-station-study-infrastructure` | none | 无新增 DAO/表结构 |

## Layer Boundary Notes

- `domain` 持有 `SkillRegistry`、`SkillDefinition`、`SkillScriptDefinition`、skill 工具实现和工具装配工厂，不将业务判断下沉到 `app` 或 `trigger`。
- `app` 仅新增配置绑定与 `application-*.yml` 示例，不承担 skill 解析、路径校验或工具选择逻辑。
- `trigger` 不新增任何控制器；skill 能力通过现有 agent 执行链路暴露。
- `reactor-tool` 负责脚本执行的技术适配：接收 Java 请求、创建运行上下文、选择 runtime、收集 stdout/stderr、上传产物。
- 由于当前 `RootNode` 与 `Step1SopRecallAndPrepareNode` 存在重复工具装配逻辑，本特性将顺带收敛为共享的工具装配工厂，但不会改变执行语义。

## Data / Config / Contract Changes

- **Database**: 无新增表、字段、索引或迁移
- **Config**:
  - 新增 `autobots.autoagent.skill.enabled`
  - 新增 `autobots.autoagent.skill.directories`
  - 新增 skill 文件工具的读取上限、匹配上限、脚本默认超时等配置
  - `runtime/skills/` 作为默认运行时 skill 根目录约定
- **Contract**:
  - Java 本地工具 schema：`skill_tool`、`script_runner_tool`、`read_tool`、`list_directory_tool`、`glob_tool`、`grep_tool`
  - Java -> Python HTTP contract：`POST /v1/tool/script_runner`
  - Python 返回统一执行结果：`success/exitCode/stdout/stderr/fileInfo/summary`
- **Compatibility**:
  - `Fix` 链路保持不变
  - 现有工具名称不变，仅在 `PlanSolve/ReAct` 工具集中新增 skill 相关工具
  - 不兼容“任意命令执行”；V1 仅支持已发现脚本

## Verification Plan

- **Java**:
  - `mvn -pl ai-agent-station-study-domain -am test`
  - 至少补充/运行 skill registry、markdown/front matter 解析、路径越界拒绝、脚本自动发现、工具装配工厂相关测试
- **UI**: N/A
- **Python**:
  - 在 `reactor-tool` 下执行最小冒烟：启动 FastAPI，验证 `/v1/tool/script_runner` 对 `python/node/shell/powershell/bat` 中至少 1-2 类 runtime 的执行结果与错误路径
  - 验证输出文件可上传并回传 `fileInfo`
- **Manual**:
  - 创建一个 demo skill，包含 `SKILL.md`、`references/`、`scripts/`
  - 启动 Java 服务与 `reactor-tool`
  - 使用 `PlanSolve/ReAct` 请求触发 `skill_tool`
  - 验证 `read_tool`/`glob_tool` 可访问附属资源
  - 验证 `script_runner_tool` 执行已注册脚本成功，越界路径与未注册脚本被拒绝

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Java 与 Python 双侧同时改动 | 现有 `code_interpreter` 已采用 Java 调 Python 模式，脚本执行复用同一部署边界最稳 | 只在 Java 本地直接跑多 runtime 会绕开现有 `reactor-tool` 统一执行入口，增加部署与运维分裂 |
| 抽取共享工具装配工厂 | `RootNode` 与 `Step1SopRecallAndPrepareNode` 已存在重复装配逻辑，新增 6 个工具后重复会明显恶化 | 继续双处手工拼装虽然更快，但后续 skill 配置与工具开关会持续双改，违背可维护性目标 |
