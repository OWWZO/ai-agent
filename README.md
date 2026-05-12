# Reactor 多智能体协同应用平台

## 项目简介

`Reactor-agent` 是一个面向复杂任务自动化与 AI 应用工程化落地的 **Reactor 多智能体协同应用平台**。  
它不是只做“单轮对话 + 单次工具调用”的 Demo，而是把复杂任务拆解、多 Agent 协作、MCP 工具编排、RAG 检索增强、会话记忆、执行事实持久化与历史回放串成一条可运行、可追踪、可复用的完整执行链路。


## 解决的痛点

- 传统单 Agent / 单轮对话难以承接复杂任务，缺少任务拆解、分工协作与结果汇聚能力
- 工具调用往往是一次性动作，搜索、分析、报告等中间结果难沉淀、难复用
- 多步骤 AI 流程过度依赖 Prompt 临场发挥，容易跑偏、漏步骤，执行稳定性不足
- 执行过程缺少结构化记录，出现问题后难审计、难回放、难定位
- AI 能力与业务系统之间常常存在落地鸿沟，Demo 能跑，但工程体系难以长期演进

## 目标用户

- 想构建 Multi-Agent 平台、复杂工作流或 AI 自动化系统的后端工程师
- 需要把检索、分析、报告、脚本执行等能力串成闭环的业务技术团队
- 想要学习 Multi-Agent 协作的学者/学生

## 典型应用场景

- 多步骤任务编排与结果汇总
- 知识检索与图文混合问答
- 数据分析与报告生成
- 复杂业务流程中的子智能体辅助执行

## 技术栈

- 后端：Java 17、Spring Boot 3、Spring AI、MyBatis 、OkHttp SSE、Elasticsearch
- 数据层：MySQL、Qdrant
- 多模态智能检索：RAG、多路混合召回、Rerank、多轮检索
- 前端：React 19、TypeScript、Vite、Ant Design

## 系统架构图

```mermaid
flowchart LR
    U[用户 / 业务场景] --> FE[前端 UI\nReact + TypeScript]
    FE --> TR[Trigger 入口层\nController / SSE / Job]
    TR --> CA[Case 应用编排层\nDispatch / Execute / Task]
    CA --> DO[Domain 核心领域层\nRuntime / Ledger / Memory / RAG / Role]
    DO --> INF[Infrastructure 基础设施层\nDAO / Gateway / Port Adapter]

    DO --> LLM[LLM / Spring AI]
    DO --> MCP[MCP 工具编排]
    DO --> PY[reactor-tool\nPython Tool Runtime]
    DO --> RAG[RAG 检索增强]

    INF --> MYSQL[(MySQL)]
    INF --> QDRANT[(Qdrant)]
    INF --> FILES[文件产物 / Artifact 存储]

    PY --> FILES
    RAG --> QDRANT
    MCP --> EXT[外部工具 / 外部系统]
```

上图描述了平台的主运行时边界：前端通过 Trigger 进入应用，Case 负责多智能体调度与任务编排，Domain 承担 Agent 运行时、记忆、RAG、执行账本等核心语义，Infrastructure 负责数据库、文件、远端工具与外部网关适配。

## 核心能力

### 1. 多智能体协同与混合思维执行

- 设计并实现 `Plan Execute + ReAct` 双模式混合思维架构：
  - `Work-Level` 负责全局规划与任务编排
  - `Task-Level` 负责细粒度执行与工具调用
- 支持将复杂任务拆解为多个可并发子任务，提升复杂场景下的可拆解性、执行效率与协同能力
- 支持多策略 Agent 动态调度，按业务场景组织不同角色、不同能力的智能体协作完成目标

### 2. 共享工作区与工具组合执行

- 搭建工具产物登记与可见性机制，将搜索结果、分析文件、报告、图片、多模态检索结果统一沉淀到会话级工作区
- 支持跨工具传递、上下文续用与任务级结果串联，形成 `搜索 -> 分析 -> 报告 -> 汇总` 的多工具组合闭环
- 让前序工具生成的文件与中间结果可以被后续工具直接复用，避免链路割裂和重复处理

### 3. Skill + SOP 标准作业体系

- 构建 Agent 的 `Skill + SOP` 任务编排机制，将专家能力与执行流程模块化沉淀为可复用能力
- 通过显式流程约束和标准作业步骤，增强复杂任务的执行确定性
- 降低模型自由发挥导致的任务跑偏、步骤遗漏和结果不一致问题，提升多步骤任务的完成度与交付一致性

### 4. RAG 与混合检索增强

- 基于 Qdrant 搭建 **语义向量召回 + BM25 关键词召回 + 文本到图片/页面的跨模态混合检索体系**
- 结合查询重写、子问题扩展、多轮检索与重排序机制，提升图文混合知识场景下的检索相关性
- 支持复杂知识任务中的证据补全、上下文增强与多源内容融合

### 5. 执行事实持久化与历史回放

- 统一记录对话过程产生的执行事实，覆盖对话运行、LLM 调用、工具调用、工具输出、文件产物等关键节点
- 支持复杂任务链路的审计、问题定位与历史回放，提升 Agent 系统的可观测性与可维护性
- 通过结构化工具输出与 artifact 引用，支持前端按历史记录稳定恢复结果展示

### 6. 跨语言工具运行时

- 采用 `Java 编排 + Python 工具执行` 的跨语言协同模式
- Java 主链路负责 Agent 编排、执行上下文与账本记录
- Python 工具侧负责脚本执行、文件处理、多模态能力与部分智能工具落地
- 统一封装流式调用、超时控制、失败处理与文件回传，降低新工具接入成本

## 执行链路图

```mermaid
flowchart TD
    subgraph S1[1. 请求接入阶段]
        A[用户在前端发起请求] --> B[Trigger 接收请求并建立 SSE 通道]
        B --> C[Case 创建本次 Dialogue Run 与执行上下文]
    end

    subgraph S2[2. 上下文装载阶段]
        C --> D[装载角色配置 / Tool Registry / Skill 与 SOP 能力]
        D --> E[读取历史摘要 / Session Memory / Workspace Artifact]
    end

    subgraph S3[3. 全局规划阶段]
        E --> F[Work-Level Planner 判断任务类型与执行策略]
        F --> G{是否需要拆解子任务}
        G -->|是| H[生成 Plan / 子任务列表 / 执行顺序 / 并发策略]
        G -->|否| I[生成单任务执行目标]
    end

    subgraph S4[4. Task-Level 执行循环]
        H --> J[进入 Task-Level Executor]
        I --> J
        J --> K[为当前 Task 组装上下文]
        K --> L[ReAct 循环: Think -> Act -> Observe]
        L --> M[选择 Tool / Skill / SOP]
    end

    subgraph S5[5. 工具执行与产物回流]
        M --> N1[Deep Search / RAG 检索]
        M --> N2[Data Analysis / Code Interpreter]
        M --> N3[Report / Image Generation]
        M --> N4[MultiModal / Script Runner]
        N1 --> O[返回文本结果 / 结构化数据 / 文件产物]
        N2 --> O
        N3 --> O
        N4 --> O
        O --> P[登记 Tool Output 与 Artifact]
        P --> Q[更新会话级 Workspace 与可复用中间结果]
    end

    subgraph S6[6. 阶段性输出与状态推进]
        Q --> R[通过 SSE 向前端推送阶段性结果]
        R --> S[写入 Run / LLM / Tool Invocation / Output / Artifact]
        S --> T{当前 Task 是否完成}
        T -->|否| K
        T -->|是| U{是否还有剩余 Task}
        U -->|是| J
    end

    subgraph S7[7. 结果汇聚与结束阶段]
        U -->|否| V[汇聚所有 Task 结果与中间产物]
        V --> W[生成最终答复 / 报告 / 文件 / 图像]
        W --> X[更新 Session Memory Snapshot 与会话摘要]
        X --> Y[支持历史回放 / 审计 / 问题定位 / 展示恢复]
    end
```

这条链路比传统“用户提问 -> LLM 回答”的流程更强调运行时闭环。一次请求进入系统后，平台会先建立执行会话并装载历史摘要、会话记忆、角色配置、工具能力和已有工作区产物，再由 `Work-Level Planner` 决定是否拆解任务、是否并行，以及每个子任务适合走哪类执行策略。

进入执行阶段后，`Task-Level Executor` 会围绕单个 Task 进入 `ReAct` 循环，在思考、选择工具、观察结果之间不断推进。无论调用的是检索、分析、报告、图像还是脚本能力，工具返回的中间结果都会被统一登记为 `Tool Output + Artifact`，沉淀到会话级工作区，后续任务可以继续复用，而不是像一次性脚本那样执行完即丢失。

同时，阶段性结果会通过 SSE 持续推送给前端，执行事实则会同步写入运行账本，覆盖 `Run / LLM / Tool Invocation / Output / Artifact / Memory Snapshot` 等关键节点。这样，平台不仅能完成复杂任务，还能在结束后支持历史恢复、过程审计、问题定位和结果展示重建。

## 项目结构

```text
Reactor-agent/
├── Reactor-agent-types/           # 基础类型、常量、任务调度接口
├── Reactor-agent-api/             # DTO 与服务接口契约
├── Reactor-agent-case/            # 应用编排层：调度、执行、任务与能力组织
├── Reactor-agent-domain/          # 领域核心：runtime / ledger / memory / rag / role
├── Reactor-agent-infrastructure/  # DAO、仓储实现、外部网关、持久化适配
├── Reactor-agent-trigger/         # Controller、SSE、Job 等入口适配层
├── Reactor-agent-app/             # Spring Boot 启动、配置、Mapper XML
├── reactor-tool/                           # Python 工具集、脚本执行与工具服务
├── ui/                                     # React 前端
├── runtime/skills/                         # 运行时 Skill 目录
└── docs/                                   # 设计与补充文档
```

## 架构说明

项目整体遵循 DDD 分层设计，并在 Agent 主链路中逐步收敛为以下职责边界：

- `trigger`：负责 HTTP / SSE / Job 等外部入口协议适配
- `case`：负责多智能体编排、任务调度、执行组织与能力协调
- `domain`：负责 Agent runtime、执行账本、记忆、RAG、角色能力等核心领域语义
- `infrastructure`：负责 DAO、外部服务、文件、远端工具、检索与持久化适配
- `app`：负责 Spring Boot 装配、配置绑定与运行时启动

这种分层方式使平台既能承载复杂 Agent 场景，又能保持较好的演进能力和维护性。

## 快速启动

### 1. 启动后端

```bash
mvn -pl Reactor-agent-app spring-boot:run
```

### 2. 启动前端

```bash
cd ui
npm install
npm run dev
```

### 3. 启动 Python 工具服务

```bash
cd reactor-tool
uv run python server.py
```

## 常用命令

### 后端

```bash
# 启动应用
mvn -pl Reactor-agent-app spring-boot:run

# 运行应用测试
mvn test -pl Reactor-agent-app -DskipTests=false

# 运行领域层回归测试
mvn test -pl Reactor-agent-domain -am -DskipTests=false
```

### 前端

```bash
cd ui
npm install
npm run dev
npm run build
npm run lint
```

### Python 工具侧

```bash
cd reactor-tool
uv run python server.py
```

## 亮点总结

- 支持 `Plan Execute + ReAct` 双模式混合思维架构
- 支持多智能体协同与复杂任务拆解执行
- 支持会话级共享工作区与工具产物复用
- 支持 `Skill + SOP` 标准作业体系，增强任务执行稳定性
- 支持基于 Qdrant 的图文混合 RAG 检索增强
- 支持工具输出、执行事实与历史结果的结构化持久化与回放

## 后续演进方向

- 更细粒度的工具权限与运行时隔离
- 更智能的多 Agent 协作策略与角色编排
- 更完善的管理后台、配置中心与可观测性能力

## 说明

本项目聚焦于 **Reactor 多智能体协同应用平台** 的工程化落地，不仅关注模型调用本身，更关注复杂任务在真实业务系统中的规划、执行、工具协同、结果沉淀与运行可观测性。
