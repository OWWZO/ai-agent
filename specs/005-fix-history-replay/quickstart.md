# Quickstart: 对话历史最终态重构与一致性修复

## 1. 切换前准备

旧历史数据与新模型不兼容，切换前先清理：

```sql
DELETE FROM ai_agent_message_event;
DELETE FROM ai_agent_message;
DELETE FROM ai_agent_conversation;
```

然后应用最新的 `schema.sql` 与 Mapper XML。

## 2. 回归验证命令

### Java

```bash
mvn -pl ai-agent-station-study-app -DskipTests=false -Dtest=ConversationHistoryPersistenceTest,ConversationHistoryDetailApiTest,ConversationHistoryArtifactTest test
mvn -pl ai-agent-station-study-domain,ai-agent-station-study-trigger,ai-agent-station-study-app -am -DskipTests=false test
```

### UI

```bash
cd ui
npm run lint
npm run build
```

## 3. 手工验收路径

### 场景 A: 最终细节不丢失

1. 启动后端与前端。
2. 发起一条 `PLAN_SOLVE` 对话，让它产生多个最终工具细节项。
3. 记录对话结束时的最终答案、最终工具细节数量、最终 plan 状态。
4. 刷新页面，重新打开该会话。
5. 确认历史详情与结束时一致，且同类最终细节未被错误折叠。

### 场景 B: plan 完成态不回退

1. 发起一条带多个计划步骤的深度思考对话。
2. 等待其执行完成，确认页面里相关步骤已显示完成。
3. 刷新页面并重新打开该会话。
4. 确认计划组件仍显示完成态，而不是回到初始计划状态。

### 场景 C: 历史工作区文件可预览

1. 发起一条会生成 HTML/Markdown/文件结果的对话。
2. 对话结束后刷新页面并重新打开会话。
3. 在右侧工作区或文件预览区域点击最终产物。
4. 确认可正常预览。

### 场景 D: Artifact 缺失态可解释

1. 复用场景 C 的一条历史记录。
2. 让对应稳定引用失效或删除文件资源。
3. 重新打开会话并点击该结果。
4. 确认页面显示明确的“引用内容不可读取/资源已失效”状态，而不是通用 `Failed to fetch`。

### 场景 E: 深度研究回归

1. 发起一条 `REACT` 对话并完成。
2. 刷新页面并重新打开历史会话。
3. 确认最终答案、最终细节和工作区结果仍可正常查看，没有因为 005 收敛而缺失。
