[根目录](../CLAUDE.md) > **ai-agent-station-study-infrastructure**

# ai-agent-station-study-infrastructure 模块

## 模块职责

基础设施层，负责数据持久化、外部服务调用、仓储实现。包含 MyBatis-Plus DAO、PO 实体、外部网关接口等。

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
