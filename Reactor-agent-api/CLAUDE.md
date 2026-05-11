[根目录](../CLAUDE.md) > **ai-agent-station-study-api**

# ai-agent-station-study-api 模块

## 模块职责

定义对外服务接口契约和 DTO（数据传输对象）。本模块是领域层对外的接口声明层，遵循 DDD 分层架构中的 API 层规范。

---

## 入口与启动

本模块为纯接口定义模块，无启动类。

---

## 对外接口

### Admin 服务接口
| 接口 | 职责 |
|-----|------|
| `IAdminUserAdminService` | 管理员用户管理 |
| `IAiClientAdminService` | AI 客户端管理 |
| `IAiClientApiAdminService` | AI 客户端 API 管理 |
| `IAiClientAdvisorAdminService` | AI 客户端 Advisor 管理 |
| `IAiClientModelAdminService` | AI 客户端模型管理 |
| `IAiClientRagOrderAdminService` | RAG 订单管理 |
| `IAiClientSystemPromptAdminService` | 系统提示词管理 |
| `IAiClientToolMcpAdminService` | MCP 工具管理 |
| `IAiAgentDrawAdminService` | Agent 绘图配置管理 |
| `IAiAgentDataStatisticsAdminService` | 数据统计 |

### Agent 服务接口
| 接口 | 职责 |
|-----|------|
| `IAiAgentService` | Agent 执行服务（装配、查询） |

---

## 关键依赖与配置

### 依赖
- `lombok`: 代码简化
- `jakarta.validation-api`: 参数校验注解
- `spring-webmvc`: Web 相关注解
- `tomcat-embed-core`: Servlet 支持

---

## 数据模型 (DTO)

### 请求 DTO
- `AdminUserRequestDTO`: 管理员用户请求
- `AdminUserLoginRequestDTO`: 登录请求
- `AiClientRequestDTO`: AI 客户端请求
- `AiClientApiRequestDTO`: API 配置请求
- `AiClientAdvisorRequestDTO`: Advisor 配置请求
- `AiClientModelRequestDTO`: 模型配置请求
- `AiClientRagOrderRequestDTO`: RAG 订单请求
- `AiClientSystemPromptRequestDTO`: 系统提示词请求
- `AiClientToolMcpRequestDTO`: MCP 工具请求
- `AiAgentDrawConfigRequestDTO`: 绘图配置请求
- `ArmoryAgentRequestDTO`: 装配 Agent 请求
- `ArmoryApiRequestDTO`: 装配 API 请求
- `AutoAgentRequestDTO`: 自动 Agent 请求

### 响应 DTO
- `AdminUserResponseDTO`: 管理员用户响应
- `AiClientResponseDTO`: AI 客户端响应
- `AiClientApiResponseDTO`: API 配置响应
- `AiClientAdvisorResponseDTO`: Advisor 配置响应
- `AiClientModelResponseDTO`: 模型配置响应
- `AiClientRagOrderResponseDTO`: RAG 订单响应
- `AiClientSystemPromptResponseDTO`: 系统提示词响应
- `AiClientToolMcpResponseDTO`: MCP 工具响应
- `AiAgentResponseDTO`: Agent 响应
- `AiAgentDrawConfigResponseDTO`: 绘图配置响应
- `DataStatisticsResponseDTO`: 数据统计响应
- `Response<T>`: 通用响应包装

---

## 测试与质量

本层主要为 DTO 定义，通过 Bean Validation 注解确保参数合法性。

---

## 常见问题 (FAQ)

**Q: DTO 和 Domain 的 VO/Entity 有什么区别？**
A: DTO 用于跨层数据传输，通常与前端接口对应；VO/Entity 是领域模型，包含业务逻辑。

---

## 相关文件清单

| 文件路径 | 说明 |
|---------|------|
| `src/main/java/org/wwz/ai/api/response/Response.java` | 通用响应包装类 |
| `src/main/java/org/wwz/ai/api/dto/*RequestDTO.java` | 请求 DTO 集合 |
| `src/main/java/org/wwz/ai/api/dto/*ResponseDTO.java` | 响应 DTO 集合 |
| `src/main/java/org/wwz/ai/api/I*Service.java` | 服务接口定义 |

---

## 变更记录 (Changelog)

### 2026-04-07
- 初始化模块文档
