[根目录](../CLAUDE.md) > **mcp-server-csdn**

# mcp-server-csdn 模块

## 模块职责

独立的 MCP（Model Context Protocol）服务器，提供 CSDN 文章相关的工具能力。基于 Spring AI MCP 实现，可被主应用通过 MCP Client 调用。

---

## 入口与启动

### 启动类
```java
cn.bugstack.mcp.server.csdn.McpServerApplication
```

### 启动方式
```bash
cd mcp-server-csdn/mcp-server-csdn
mvn spring-boot:run
```

---

## 对外接口

### MCP 工具
本模块通过 Spring AI MCP 暴露以下工具：

| 工具 | 说明 |
|-----|------|
| `CSDNArticleService` | CSDN 文章服务，提供文章搜索、获取等功能 |

### HTTP 接口
| 接口 | 说明 |
|-----|------|
| `ICSDNService` | CSDN API 网关接口（Retrofit 实现） |

---

## 关键依赖与配置

### 核心依赖
- `spring-boot-starter`: Spring Boot 基础
- `spring-ai-mcp-server-spring-boot-starter`: MCP Server 支持
- `retrofit2`: HTTP 客户端
- `jackson`: JSON 序列化

### 配置项
```yaml
csdn:
  api:
    cookie: ${CSDN_COOKIE}
```

---

## 数据模型

### Domain 模型
| 类 | 说明 |
|---|------|
| `ArticleFunctionRequest` | 文章功能请求 |
| `ArticleFunctionResponse` | 文章功能响应 |

### DTO
| 类 | 说明 |
|---|------|
| `ArticleRequestDTO` | 文章请求 DTO |
| `ArticleResponseDTO` | 文章响应 DTO |

### Properties
| 类 | 说明 |
|---|------|
| `CSDNApiProperties` | CSDN API 配置属性 |

---

## 测试与质量

### 测试类
- `ApiTest`: API 测试

---

## 常见问题 (FAQ)

**Q: MCP Server 是什么？**
A: MCP（Model Context Protocol）是 Anthropic 提出的协议，用于标准化 AI 模型与外部工具的交互。

**Q: 如何配置 CSDN Cookie？**
A: 在 `application.yml` 中设置 `csdn.api.cookie` 或通过环境变量 `CSDN_COOKIE` 传入。

**Q: 主应用如何调用 MCP Server？**
A: 主应用通过 `spring-ai-starter-mcp-client-webflux` 配置 MCP Server 地址，即可调用其中的工具。

---

## 相关文件清单

### 启动与配置
| 文件路径 | 说明 |
|---------|------|
| `mcp-server-csdn/src/main/java/cn/bugstack/mcp/server/csdn/McpServerApplication.java` | 启动类 |
| `mcp-server-csdn/src/main/java/cn/bugstack/mcp/server/csdn/types/properties/CSDNApiProperties.java` | 配置属性 |

### 领域层
| 文件路径 | 说明 |
|---------|------|
| `mcp-server-csdn/src/main/java/cn/bugstack/mcp/server/csdn/domain/service/CSDNArticleService.java` | 文章服务 |
| `mcp-server-csdn/src/main/java/cn/bugstack/mcp/server/csdn/domain/adapter/ICSDNPort.java` | 端口接口 |
| `mcp-server-csdn/src/main/java/cn/bugstack/mcp/server/csdn/domain/model/ArticleFunctionRequest.java` | 请求模型 |
| `mcp-server-csdn/src/main/java/cn/bugstack/mcp/server/csdn/domain/model/ArticleFunctionResponse.java` | 响应模型 |

### 基础设施层
| 文件路径 | 说明 |
|---------|------|
| `mcp-server-csdn/src/main/java/cn/bugstack/mcp/server/csdn/infrastructure/adapter/CSDNPort.java` | 端口实现 |
| `mcp-server-csdn/src/main/java/cn/bugstack/mcp/server/csdn/infrastructure/gateway/ICSDNService.java` | CSDN API 网关 |
| `mcp-server-csdn/src/main/java/cn/bugstack/mcp/server/csdn/infrastructure/gateway/dto/ArticleRequestDTO.java` | 请求 DTO |
| `mcp-server-csdn/src/main/java/cn/bugstack/mcp/server/csdn/infrastructure/gateway/dto/ArticleResponseDTO.java` | 响应 DTO |

### 工具类
| 文件路径 | 说明 |
|---------|------|
| `mcp-server-csdn/src/main/java/cn/bugstack/mcp/server/csdn/types/utils/MarkdownConverter.java` | Markdown 转换器 |

### 测试
| 文件路径 | 说明 |
|---------|------|
| `mcp-server-csdn/src/test/java/cn/bugstack/mcp/server/csdn/test/ApiTest.java` | API 测试 |

---

## 变更记录 (Changelog)

### 2026-04-07
- 初始化模块文档
