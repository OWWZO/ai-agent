# Spec Kit + Codex 使用说明

## 接入结果

当前仓库已经完成以下接入：

- 根目录新增 `.specify/`，用于存放 Spec Kit 的模板、脚本、集成配置与项目记忆
- 根目录新增 `.agents/skills/`，Codex 可直接使用 `$speckit-*` skills
- 根目录新增 `AGENTS.md`，让 Codex 进入仓库后立即获得项目级上下文
- `.specify/memory/constitution.md` 已按本项目的 DDD、多模块、Agent/MCP/RAG 场景定制
- `spec/plan/tasks` 模板已调整为更适合本仓库的棕地开发流程

## 推荐用法

对于这个仓库，最有效的方式不是“想到实现就直接写”，而是按下面的顺序走：

1. 先定义需求规格

```text
$speckit-specify 实现“xxx功能”，请基于 ai-agent-station-study 现有 DDD 多模块结构输出规格，明确影响模块、复用能力、边界条件和验收标准
```

2. 如果需求里有歧义，先澄清

```text
$speckit-clarify
```

3. 再出实现计划

```text
$speckit-plan
```

4. 生成可执行任务

```text
$speckit-tasks
```

5. 实施前做一次一致性检查

```text
$speckit-analyze
```

6. 最后再进入编码

```text
$speckit-implement
```

## 适合本项目的提示词写法

### Java 主链路需求

```text
$speckit-specify 为 AI Agent 工作站新增“可配置工具白名单”能力。
要求：
1. 复用现有 Agent/Tool/MCP 注册机制
2. 明确 domain、infrastructure、app 分层职责
3. 如果涉及表结构、Mapper XML、DTO 或管理接口，请写清楚
4. 给出可独立验证的验收场景
```

### UI 联动需求

```text
$speckit-specify 为 ui 管理端新增“模型配置可视化编辑”页面。
要求：
1. 优先复用现有请求封装和组件风格
2. 说明需要联动的后端接口和数据结构
3. 输出前后端验收标准
```

### Python MCP 工具需求

```text
$speckit-specify 为 reactor-tool 新增“xxx”工具。
要求：
1. 复用现有 FastAPI / MCP 工具组织方式
2. 明确输入输出协议和异常处理
3. 说明与 Java 主系统的边界
```

## 使用建议

- 棕地项目优先写“影响模块”和“复用已有能力”，这样产出的 plan 会明显更稳。
- 需求跨 `domain + infrastructure + app` 时，尽量在 `plan.md` 里把 DAO、Mapper XML、配置、测试一起列全，避免实现阶段漏项。
- 需求涉及 `ui` 或 `reactor-tool` 时，不要默认它们和 Java 主链路一起改；只有规格明确需要联动时再跨栈推进。
- 规格里如果不写清验收方式，后续任务通常会发散；最好在 `spec.md` 就写出“如何独立验证”。
- 项目已有 `CLAUDE.md` 和模块级 `CLAUDE.md`，它们是很好的现有上下文，可与 Spec Kit 一起使用。
