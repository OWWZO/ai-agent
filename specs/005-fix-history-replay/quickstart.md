# Quickstart: 对话细节统一 UI 与最终态历史重构

## 1. 切换前准备

本次不兼容旧错误历史数据，切换前先清理：

```sql
DELETE FROM ai_agent_message_event;
DELETE FROM ai_agent_message;
DELETE FROM ai_agent_conversation;
```

然后同步最新的：

- `ai-agent-station-study-app/src/main/resources/db/schema.sql`
- `ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_message_mapper.xml`
- `ai-agent-station-study-app/src/main/resources/mybatis/mapper/ai_agent_message_event_mapper.xml`
- 历史详情相关 VO / Service / UI 代码

## 2. 回归验证命令

### 2.1 Java

```bash
mvn -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ConversationHistoryPersistenceTest,ConversationHistoryDetailApiTest,ConversationHistoryArtifactTest test
```

如有新增终态和批量查询用例，再补充对应测试类：

```bash
mvn -pl ai-agent-station-study-app -am -DskipTests=false test
```

### 2.2 UI

```bash
cd ui
npm run lint
npm run build
```

## 3. 手工验收路径

### 场景 A: `PLAN_SOLVE` 结束态与历史重开是同一套 UI

1. 启动后端与前端。
2. 发起一条 `PLAN_SOLVE` 对话，确保它在进行中界面里出现：
   - 思考过程块
   - 计划块
   - 至少一个任务分组
   - 多个工具/搜索/总结细节块
   - 右侧工作区内容
3. 在对话结束时记录左侧与右侧界面的块顺序、标题、状态、可点击入口。
4. 刷新页面并重新打开同一会话。
5. 确认历史详情仍然使用相同的 `ChatView + Dialogue + ActionView/FilePreview` 体验，而不是历史专用布局。

### 场景 B: 多条同类搜索块不丢失

1. 发起一条会产生多条 `deep_search/search` 的结构化对话。
2. 在结束时记录这些搜索块的数量与顺序。
3. 刷新并重开历史。
4. 确认历史中逐条显示相同数量的搜索块，不再只剩第一条。

### 场景 C: 计划完成态不回退

1. 发起一条多步骤 `PLAN_SOLVE` 会话。
2. 等待多个计划步骤显示为 `completed`。
3. 刷新并重开历史。
4. 确认计划块仍显示相同的完成态，不退回初始计划组件。

### 场景 D: `REACT` 会话也走同一套界面

1. 发起一条 `REACT` 会话。
2. 等待它出现搜索、总结、结果块和右侧工作区内容。
3. 记录结束时界面结构。
4. 刷新并重开历史。
5. 确认历史详情与结束时的进行中界面 1:1 对齐。

### 场景 E: `error` / `force_stop` 终态仍保留最后可见细节

1. 发起一条结构化会话，让它在出现思考、计划或工具细节后异常结束，或手动停止。
2. 记录结束瞬间界面上最后仍可见的细节块。
3. 刷新并重开历史。
4. 确认历史仍显示这些块，并明确显示该轮为 `error` 或 `force_stop`。

### 场景 F: 工作区产物可再次预览，缺失时可解释

1. 发起一条会生成 HTML/Markdown/文件结果的结构化会话。
2. 在结束时记录右侧工作区和左侧相关入口。
3. 刷新并重开历史，点击同一入口。
4. 确认预览正常，或看到明确缺失原因，而不是 `Failed to fetch`。

### 场景 G: 普通 `CHAT` 不被结构化历史破坏

1. 发起一条普通 `CHAT` 会话并完成。
2. 刷新并重开历史。
3. 确认普通聊天仍维持轻量展示，不被强行迁移为复杂多区块视图。

## 4. 验收关注点

- 历史与进行中共用的是同一套 UI 基线，不只是“内容大致相似”。
- 历史详情 `payload` 必须可以直接喂给前端已有的 `combineData/handleTaskData` 处理路径。
- 每个最终可见块一条 event 记录，不能把多条搜索或多条工具块合并后再读时拆。
- `completed`、`error`、`force_stop` 三类终态都必须保留最后可见界面。
- 工作区预览依赖稳定 `artifactRefs[]`，缺失态必须显式可见。
