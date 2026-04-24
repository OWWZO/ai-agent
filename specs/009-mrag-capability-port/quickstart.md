# Quickstart

## 1. 配置 Java 侧多模态工具

在 [application-dev.yml](/D:/Java%20Code/ai-agent/ai-agent-station-study/ai-agent-station-study-app/src/main/resources/application-dev.yml) 的 `autobots.autoagent` 下补齐 MRAG 配置：

```yaml
autobots:
  autoagent:
    tool:
      multimodalagent_tool:
        desc: "本工具用于查询与用户相关的知识，支持图文混合知识检索。"
        params: '{"type":"object","properties":{"question":{"type":"string","description":"查询所需要的question，需要在知识库中进行检索的检索短语或句子。"}},"required":["question"]}'
    tool_list: '{"default":"search,code,report,multimodalagent"}'
    multimodalagent_url: "http://127.0.0.1:1601"
    message_interval: '{"knowledge":"1,4"}'
```

说明：

- 保留现有 `knowledge_url` 配置，继续服务 `sopRecall` 等既有能力。
- 若需要关闭默认暴露，只需从 `tool_list.default` 中移除 `multimodalagent`。
- `dataAgent` 路径不应注册该工具。

## 2. 配置 `reactor-tool` 的 MRAG 运行环境

确保 `reactor-tool` 具备以下条件：

- 已把参考项目 MRAG 代码选择性迁入 `reactor_tool/tool/mrag/`
- `pyproject.toml` 已补齐 MRAG 实际 import 所需依赖
- 环境变量中已提供 MRAG 所需知识库与文件服务配置，例如：

```powershell
$env:DEFAULT_KB_ID="your-default-kb"
$env:FILE_SERVER_URL="http://127.0.0.1:8080"
```

如 MRAG 模块依赖对象存储、向量库或 OCR/文档解析配置，也应在此步骤补齐。

## 3. 启动 `reactor-tool`

在仓库根目录执行：

```powershell
cd reactor-tool
uv run python server.py
```

确认 `/v1/tool/mragQuery` 已被路由注册。

## 4. 直接冒烟验证 MRAG 接口

使用 `curl.exe` 验证 `reactor-tool` SSE 输出：

```powershell
curl.exe -N -X POST "http://127.0.0.1:1601/v1/tool/mragQuery" ^
  -H "Content-Type: application/json" ^
  -d "{\"question\":\"总结知识库中关于多模态检索的核心能力\",\"image_urls\":[],\"kb_id\":\"your-default-kb\"}"
```

期望结果：

- 返回 `text/event-stream`
- SSE `data:` 片段为 OpenAI 兼容 `choices[].delta.content`
- 结束时输出 `data: [DONE]`

## 5. 启动 Java 应用

回到仓库根目录执行：

```powershell
mvn -pl ai-agent-station-study-app spring-boot:run
```

## 6. 通过现有对话链路做集成验收

在现有对话入口分别验证 `REACT` 与 `PlanSolve`：

1. 发送一条需要图文混合知识理解的文本问题。
2. 确认 Agent 能选择 `multimodalagent_tool`。
3. 确认会话中持续出现 MRAG 检索结果。
4. 确认最终生成 Markdown 产物，并可通过现有 artifact 展示链路查看。

## 7. 验证失败与兼容场景

建议至少覆盖以下验收样本：

- 将 `multimodalagent_url` 指向错误地址，确认返回明确失败，不自动降级到 `deep_search`
- 从 `tool_list.default` 移除 `multimodalagent`，确认新会话不再暴露该工具
- 切换到 `dataAgent` 风格请求，确认仍只保留原有工具语义
- 复测普通搜索、报告、文件工具与历史回放，确认无高严重度回归
