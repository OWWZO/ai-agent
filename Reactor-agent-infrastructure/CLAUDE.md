[根目录](../CLAUDE.md) > **ai-agent-station-study-infrastructure**

# ai-agent-station-study-infrastructure 模块

## 模块职责

基础设施层，负责数据持久化、外部服务调用、仓储实现。包含 MyBatis-Plus DAO、PO 实体、外部网关接口等。

当前收敛边界下，`case` 负责应用编排，`domain` 负责领域规则，`infrastructure` 继续承接 DAO、HTTP/MCP 网关、技术执行器与仓储适配实现。
在 `019-agent-ddd-convergence` 中，HTTP / 流式远端调用、JDBC/dataquery 与文件产物能力已经明确下沉到本模块。
在 `020-prune-agent-bridges` 中，`infrastructure` 继续消费少量已登记的 legacy contract，包括 dataagent 配置契约与工作台生图契约；这些引用属于明确延期项，禁止继续扩张为新的通用 bridge。

---

## 入口与启动

本模块为基础设施模块，无启动类，由 app 模块启动时扫描加载。

---

## 对外接口

### DAO 接口
| 接口 | 职责 |
|-----|------|
| `IAiAgentDao` | Agent 配置数据访问 |
| `IAiClientDao` | 客户端数据访问 |
| `IAiClientApiDao` | API 配置数据访问 |
| `IAiClientAdvisorDao` | Advisor 配置数据访问 |
| `IAiClientConfigDao` | 客户端配置数据访问 |
| `IAiClientModelDao` | 模型配置数据访问 |
| `IAiClientRagOrderDao` | RAG 订单数据访问 |
| `IAiClientSystemPromptDao` | 系统提示词数据访问 |
| `IAiClientToolMcpDao` | MCP 工具数据访问 |
| `IAiAgentFlowConfigDao` | 流程配置数据访问 |
| `IAiAgentTaskScheduleDao` | 任务调度数据访问 |
| `IAiAgentDrawConfigDao` | 绘图配置数据访问 |
| `IAdminUserDao` | 管理员用户数据访问 |

### 仓储实现
| 类 | 职责 |
|---|------|
| `AgentRepository` | Agent 仓储实现 |
| `ExecutionLedgerReadRepository` | Reactor 执行账本读仓储实现 |
| `ExecutionLedgerWriteRepository` | Reactor 执行账本写仓储实现 |
| `ChatModelMetadataRepository` | 问数模型元数据仓储实现 |

### 外部网关
| 接口 | 职责 |
|-----|------|
| `ICSDNService` | CSDN 服务网关（在 mcp-server-csdn 中实现） |

---

## 关键依赖与配置

### 依赖
- `mybatis-spring-boot-starter`: MyBatis Spring Boot 集成
- `okhttp/okhttp-sse`: HTTP 客户端
- `ai-agent-station-study-domain`: 依赖领域层（仓储接口）
- `ai-agent-station-study-api`: 依赖 API 层（DTO）

---

## 数据模型 (PO)

### Agent 相关
| 类 | 说明 |
|---|------|
| `AiAgent` | Agent 配置 |
| `AiAgentFlowConfig` | 流程配置 |
| `AiAgentTaskSchedule` | 任务调度 |
| `AiAgentDrawConfig` | 绘图配置 |
| `AiAgentDrawNodes` | 绘图节点 |
| `AiAgentDrawEdges` | 绘图边 |
| `AiAgentDrawRelations` | 绘图关系 |
| `AiAgentDrawHistory` | 绘图历史 |

### 客户端相关
| 类 | 说明 |
|---|------|
| `AiClient` | 客户端 |
| `AiClientConfig` | 客户端配置 |
| `AiClientApi` | API 配置 |
| `AiClientAdvisor` | Advisor 配置 |
| `AiClientModel` | 模型配置 |
| `AiClientSystemPrompt` | 系统提示词 |
| `AiClientToolMcp` | MCP 工具配置 |
| `AiClientRagOrder` | RAG 订单 |

### 用户相关
| 类 | 说明 |
|---|------|
| `AdminUser` | 管理员用户 |

### Reactor Phase 2A 持久化边界
- Reactor ledger、tool-output、chat-model DAO / Mapper 统一位于 `src/main/java/org/wwz/ai/infrastructure/dao/reactor/`
- `ExecutionLedgerReadRepository`、`ExecutionLedgerWriteRepository`、`ChatModelMetadataRepository` 负责把领域仓储端口映射到这些 DAO
- `ToolOutputReaderImpl`、`ToolOutputWriterImpl`、`SessionContextMemoryServiceImpl`、`WorkspaceImageGenerationServiceImpl` 当前属于允许直接协作 DAO 的过渡态技术实现
- 后续阶段若继续收敛，会优先新增 repository seam，而不是把 DAO 或 MyBatis-Plus 细节重新带回 `domain`

### Agent DDD 最终边界（019）
- `OkHttpRemoteHttpAdapter`、`OkHttpRemoteStreamAdapter`、`ReactorToolFileArtifactAdapter` 是 runtime 对外部 HTTP / 文件产物的主适配器
- `DataQueryExecutionAdapter`、`DataQueryMetadataAdapter` 与 `infrastructure.dataquery.jdbc/**` 承接 JDBC provider、catalog、dialect、连接池
- `domain` 只能通过 port / repository seam 使用这些能力，禁止重新引入技术执行器到领域层

### Legacy Contract Allowlist（020）
- 允许继续引用 `org.wwz.ai.domain.agent.reactor.config.data.*`，因为 `DataQueryExecutionAdapter`、`DataQueryMetadataAdapter`、`JdbcUtils` 仍需要共享数据库连接配置契约
- 允许继续引用 `org.wwz.ai.domain.agent.reactor.model.imagegeneration.*` 与 `org.wwz.ai.domain.agent.reactor.service.IWorkspaceImageGenerationService` / `org.wwz.ai.domain.agent.reactor.service.imagegeneration.*`，因为工作台生图入口与技术执行器仍共用这组稳定历史契约
- 不允许新增对 `org.wwz.ai.domain.agent.service.execute.*`、`org.wwz.ai.domain.agent.service.armory.*`、已删除 bridge 或其它未登记 legacy 包的依赖

---

## 测试与质量

DAO 层测试位于 `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/dao/`

---

## 常见问题 (FAQ)

**Q: PO 和 Domain Entity 的区别？**
A: PO 是持久化对象，与数据库表结构一一对应；Domain Entity 是领域实体，包含业务逻辑，可能由多个 PO 组合而成。

**Q: 为什么仓储实现在 Infrastructure 层？**
A: 遵循 DDD 分层架构，Domain 层定义仓储接口，Infrastructure 层提供具体实现（如 MyBatis-Plus、JPA 等）。

---

## 相关文件清单

### DAO 接口
| 文件路径 | 说明 |
|---------|------|
| `src/main/java/org/wwz/ai/infrastructure/dao/IAiAgentDao.java` | Agent DAO |
| `src/main/java/org/wwz/ai/infrastructure/dao/IAiClientDao.java` | Client DAO |
| `src/main/java/org/wwz/ai/infrastructure/dao/IAiClientApiDao.java` | API DAO |
| `src/main/java/org/wwz/ai/infrastructure/dao/IAiClientAdvisorDao.java` | Advisor DAO |
| `src/main/java/org/wwz/ai/infrastructure/dao/IAiClientConfigDao.java` | Config DAO |
| `src/main/java/org/wwz/ai/infrastructure/dao/IAiClientModelDao.java` | Model DAO |
| `src/main/java/org/wwz/ai/infrastructure/dao/IAiClientRagOrderDao.java` | RAG Order DAO |
| `src/main/java/org/wwz/ai/infrastructure/dao/IAiClientSystemPromptDao.java` | System Prompt DAO |
| `src/main/java/org/wwz/ai/infrastructure/dao/IAiClientToolMcpDao.java` | MCP DAO |
| `src/main/java/org/wwz/ai/infrastructure/dao/IAiAgentFlowConfigDao.java` | Flow Config DAO |
| `src/main/java/org/wwz/ai/infrastructure/dao/IAiAgentTaskScheduleDao.java` | Task Schedule DAO |
| `src/main/java/org/wwz/ai/infrastructure/dao/IAiAgentDrawConfigDao.java` | Draw Config DAO |
| `src/main/java/org/wwz/ai/infrastructure/dao/IAdminUserDao.java` | Admin User DAO |

### PO 实体
| 文件路径 | 说明 |
|---------|------|
| `src/main/java/org/wwz/ai/infrastructure/dao/po/AiAgent.java` | Agent PO |
| `src/main/java/org/wwz/ai/infrastructure/dao/po/AiClient.java` | Client PO |
| `src/main/java/org/wwz/ai/infrastructure/dao/po/AiClientApi.java` | API PO |
| `src/main/java/org/wwz/ai/infrastructure/dao/po/AiClientAdvisor.java` | Advisor PO |
| `src/main/java/org/wwz/ai/infrastructure/dao/po/AiClientConfig.java` | Config PO |
| `src/main/java/org/wwz/ai/infrastructure/dao/po/AiClientModel.java` | Model PO |
| `src/main/java/org/wwz/ai/infrastructure/dao/po/AiClientRagOrder.java` | RAG Order PO |
| `src/main/java/org/wwz/ai/infrastructure/dao/po/AiClientSystemPrompt.java` | System Prompt PO |
| `src/main/java/org/wwz/ai/infrastructure/dao/po/AiClientToolMcp.java` | MCP PO |
| `src/main/java/org/wwz/ai/infrastructure/dao/po/AiAgentFlowConfig.java` | Flow Config PO |
| `src/main/java/org/wwz/ai/infrastructure/dao/po/AiAgentTaskSchedule.java` | Task Schedule PO |
| `src/main/java/org/wwz/ai/infrastructure/dao/po/AiAgentDrawConfig.java` | Draw Config PO |
| `src/main/java/org/wwz/ai/infrastructure/dao/po/AdminUser.java` | Admin User PO |

### 仓储实现
| 文件路径 | 说明 |
|---------|------|
| `src/main/java/org/wwz/ai/infrastructure/adapter/repository/AgentRepository.java` | Agent 仓储实现 |

---

## 变更记录 (Changelog)

### 2026-04-07
- 初始化模块文档
