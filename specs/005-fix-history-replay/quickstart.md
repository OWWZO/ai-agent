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
mvn -pl ai-agent-station-study-app -am -DskipTests=false -Dtest=ConversationHistoryPersistenceTest,ConversationHistoryDetailApiTest,ConversationHistoryArtifactTest test
mvn -pl ai-agent-station-study-domain,ai-agent-station-study-trigger,ai-agent-station-study-app -am -DskipTests=false test
```

说明：第一条命令带 `-am`，避免 `ai-agent-station-study-app` 在单独执行定向测试时因为依赖模块未先编译而失败。

### UI

```bash
cd ui
npm run lint
npm run build
```

## 3. 手工验收路径

### 场景 A: `PLAN_SOLVE` 历史恢复 1:1 最终界面细节

1. 启动后端与前端。
2. 发起一条 `PLAN_SOLVE` 对话，让它产生：
   - 思考过程面板
   - 计划面板
   - 至少一个任务分组
   - 多个工具调用/搜索/总结细节卡片
   - 最终答案
3. 在对话结束时记录这些细节块的数量、顺序和主要文案。
4. 刷新页面，重新打开该会话。
5. 确认历史详情仍展示相同数量、相同顺序、相同主要文案的最终细节块，而不是退化成少量摘要。

### 场景 B: 计划完成态不回退

1. 发起一条带多个计划步骤的深度思考对话。
2. 等待其执行完成，确认页面中多个步骤已显示为完成。
3. 刷新页面并重新打开该会话。
4. 确认计划组件仍显示最终完成态，不回退为初始计划状态，也不退化成单条“计划完成”摘要。

### 场景 C: 工作区结果可再次预览

1. 发起一条会生成 HTML/Markdown/文件结果的结构化会话。
2. 对话结束后刷新页面并重新打开会话。
3. 在右侧工作区或历史关联入口中点击最终产物。
4. 确认可正常预览，且相关时间线细节与工作区结果保持关联。

### 场景 D: Artifact 缺失态可解释

1. 复用场景 C 的一条历史记录。
2. 让对应稳定引用失效或删除文件资源。
3. 重新打开会话并点击该结果。
4. 确认页面显示明确的“引用内容不可读取/资源已失效”状态，而不是通用 `Failed to fetch`。

### 场景 E: `REACT` 历史也恢复最终界面细节

1. 发起一条 `REACT` 对话并完成。
2. 记录其结束时仍可见的思考/工具/搜索/结果细节。
3. 刷新页面并重新打开历史会话。
4. 确认这些最终可见细节继续存在，且没有因为 005 收敛而只剩摘要。

### 场景 F: 普通 `CHAT` 轻量历史不回归

1. 发起一条普通 `CHAT` 对话并完成。
2. 刷新页面并重新打开该会话。
3. 确认聊天历史仍正常展示最终答案与基础上下文，没有被结构化细节模型破坏。

## 4. 验收关注点

- 历史恢复对齐的是“对话结束时界面最终仍可见的细节块”，不是实时执行轨迹。
- 若某段瞬时增量在结束前已从界面消失，则不要求在历史中重放。
- 若某个思考/工具块结束时仍可见，则必须在历史中完整保留。
- 同一份同时出现在对话区和工作区的内容应保持单一真相源，不能出现两份内容漂移的拷贝。
