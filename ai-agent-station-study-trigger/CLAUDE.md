[根目录](../CLAUDE.md) > **ai-agent-station-study-trigger**

# ai-agent-station-study-trigger 模块

## 模块职责

触发器层，负责接收外部请求（HTTP 接口）、定时任务调度、消息监听。是系统对外的入口层。

当前收敛边界下，Trigger 入口优先依赖 `ai-agent-station-study-case` 暴露的应用服务；像 `SseEmitter` 这样的协议对象只允许停留在 trigger 适配器中，不再直接穿透到应用编排接口之外。
`AiAgentController`、`ReactorController`、`DataAgentController`、角色库和 RAG 管理入口都必须通过 `case` seam 进入主链路，不允许重新依赖已删除的 GPT query / dataagent bridge。

---

## 入口与启动

本模块为触发器模块，无独立启动类，由 app 模块启动时扫描加载。

---

## 对外接口

### HTTP 接口

#### Agent 核心接口
| 接口 | 方法 | 说明 |
|-----|------|------|
| `POST /AutoAgent` | `AiAgentController.AutoAgent` | 自动 Agent SSE 流式对话 |
| `POST /web/api/v1/gpt/queryAgentStreamIncr` | `AiAgentController.queryAgentStreamIncr` | GPT 流式查询 |
| `POST /armory_agent` | `AiAgentController.armoryAgent` | 装配 Agent |
| `POST /armory_api` | `AiAgentController.armoryApi` | 装配 API |
| `GET /query_available_agents` | `AiAgentController.queryAvailableAgents` | 查询可用 Agent |
| `GET /web/health` | `AiAgentController.health` | 健康检查 |

#### 会话管理接口
| 接口 | 方法 | 说明 |
|-----|------|------|
| `GET /api/agent/conversation/list` | `AgentConversationController.list` | 会话列表 |
| `GET /api/agent/conversation/detail` | `AgentConversationController.detail` | 会话详情 |
| `POST /api/agent/conversation/create` | `AgentConversationController.create` | 创建会话 |
| `PUT /api/agent/conversation/rename` | `AgentConversationController.rename` | 重命名会话 |
| `DELETE /api/agent/conversation/{sessionId}` | `AgentConversationController.delete` | 删除会话 |
| `PUT /api/agent/conversation/pin` | `AgentConversationController.pin` | 置顶/取消置顶 |
| `POST /api/agent/conversation/migrate` | `AgentConversationController.migrate` | 迁移匿名会话 |

#### 消息接口
| 接口 | 方法 | 说明 |
|-----|------|------|
| `POST /api/agent/message/send-stream` | `AgentMessageController.sendStream` | 发送消息（SSE 流式） |
| `POST /api/agent/message/stop` | `AgentMessageController.stop` | 强制停止 |

#### Admin 管理接口
| 接口 | 说明 |
|-----|------|
| `AdminUserAdminController` | 管理员用户管理 |
| `AiClientAdminController` | AI 客户端管理 |
| `AiClientApiAdminController` | API 配置管理 |
| `AiClientAdvisorAdminController` | Advisor 管理 |
| `AiClientModelAdminController` | 模型管理 |
| `AiClientRagOrderAdminController` | RAG 订单管理 |
| `AiClientSystemPromptAdminController` | 系统提示词管理 |
| `AiClientToolMcpAdminController` | MCP 工具管理 |
| `AiAgentDrawAdminController` | 绘图配置管理 |
| `AiAgentDataStatisticsAdminController` | 数据统计 |

### 定时任务
| 任务 | 说明 |
|-----|------|
| `AgentTaskJob` | Agent 定时任务 |

---

## 关键依赖与配置

### 依赖
- `spring-boot-starter-web`: Web 支持
- `spring-tx`: 事务支持
- `ai-agent-station-study-api`: API 层
- `ai-agent-station-study-case`: 应用编排层
- `ai-agent-station-study-types`: 基础类型
- `ai-agent-station-study-infrastructure`: 基础设施层

---

## 数据模型 (VO)

### Agent 会话 VO
- `ConversationCreateReqVO`: 创建会话请求
- `ConversationListRespVO`: 会话列表响应
- `ConversationDetailRespVO`: 会话详情响应
- `ConversationRenameReqVO`: 重命名请求
- `MessageSendReqVO`: 发送消息请求
- `MessageRespVO`: 消息响应
- `PageRespVO`: 分页响应

---

## 测试与质量

### 测试类
- `SimpleParserTest`: 简单解析测试
- `TestDrawConfigParser`: 绘图配置解析测试

---

## 常见问题 (FAQ)

**Q: Trigger 层和 API 层的区别？**
A: Trigger 层是实际的控制器实现（Controller），API 层是接口契约定义（DTO + Interface）。

**Q: 为什么 Admin 接口都在 trigger 层？**
A: Admin 接口是面向运营后台的 HTTP 接口，属于触发器职责。

---

## 相关文件清单

### Controller
| 文件路径 | 说明 |
|---------|------|
| `src/main/java/org/wwz/ai/trigger/http/AiAgentController.java` | 核心 Agent 接口 |
| `src/main/java/org/wwz/ai/trigger/http/reactor/ReactorController.java` | legacy Reactor HTTP 入口 |
| `src/main/java/org/wwz/ai/trigger/http/dataagent/DataAgentController.java` | legacy DataAgent HTTP 入口 |
| `src/main/java/org/wwz/ai/trigger/http/agent/AgentConversationController.java` | 会话管理接口 |
| `src/main/java/org/wwz/ai/trigger/http/agent/AgentMessageController.java` | 消息接口 |
| `src/main/java/org/wwz/ai/trigger/http/admin/AdminUserAdminController.java` | 管理员接口 |
| `src/main/java/org/wwz/ai/trigger/http/admin/AiClientAdminController.java` | 客户端管理 |
| `src/main/java/org/wwz/ai/trigger/http/admin/AiClientApiAdminController.java` | API 管理 |
| `src/main/java/org/wwz/ai/trigger/http/admin/AiClientAdvisorAdminController.java` | Advisor 管理 |
| `src/main/java/org/wwz/ai/trigger/http/admin/AiClientModelAdminController.java` | 模型管理 |
| `src/main/java/org/wwz/ai/trigger/http/admin/AiClientRagOrderAdminController.java` | RAG 订单管理 |
| `src/main/java/org/wwz/ai/trigger/http/admin/AiClientSystemPromptAdminController.java` | 系统提示词管理 |
| `src/main/java/org/wwz/ai/trigger/http/admin/AiClientToolMcpAdminController.java` | MCP 工具管理 |
| `src/main/java/org/wwz/ai/trigger/http/admin/AiAgentDrawAdminController.java` | 绘图配置管理 |
| `src/main/java/org/wwz/ai/trigger/http/admin/AiAgentDataStatisticsAdminController.java` | 数据统计 |

### VO
| 文件路径 | 说明 |
|---------|------|
| `src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationCreateReqVO.java` | 创建会话 VO |
| `src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationListRespVO.java` | 会话列表 VO |
| `src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationDetailRespVO.java` | 会话详情 VO |
| `src/main/java/org/wwz/ai/trigger/http/agent/vo/ConversationRenameReqVO.java` | 重命名 VO |
| `src/main/java/org/wwz/ai/trigger/http/agent/vo/MessageSendReqVO.java` | 发送消息 VO |
| `src/main/java/org/wwz/ai/trigger/http/agent/vo/MessageRespVO.java` | 消息 VO |
| `src/main/java/org/wwz/ai/trigger/http/agent/vo/PageRespVO.java` | 分页 VO |

### Job
| 文件路径 | 说明 |
|---------|------|
| `src/main/java/org/wwz/ai/trigger/job/AgentTaskJob.java` | Agent 定时任务 |

### 工具类
| 文件路径 | 说明 |
|---------|------|
| `src/main/java/org/wwz/ai/trigger/http/reactor/support/SseEmitterAgentSessionStream.java` | trigger 侧 SSE 协议适配 |
| `src/main/java/org/wwz/ai/trigger/http/reactor/support/SseLifecycleSupport.java` | SSE 生命周期与心跳支持 |
| `src/main/java/org/wwz/ai/trigger/http/admin/util/DrawConfigParser.java` | 绘图配置解析器 |
| `src/main/java/org/wwz/ai/trigger/config/BaseFilterConfig.java` | 过滤器配置 |

---

## 变更记录 (Changelog)

### 2026-04-07
- 初始化模块文档
