# Quickstart: Fix 模式 AI 角色库

## 1. 准备数据

1. 准备至少 2 个满足以下条件的智能体：
   - `ai_agent.status = 1`
   - `ai_agent.strategy = 'flowAgentExecuteStrategy'`
   - `ai_agent_flow_config` 至少存在 1 条记录
   - FlowConfig 关联的客户端可正常装配
2. 在应用配置中指定默认 chat 角色：

```yaml
spring:
  ai:
    agent:
      chat:
        default-role-id: 85374287
```

3. 保留至少 1 个历史 chat 会话不填角色字段，用于验证兼容回退。

## 2. 启动服务

后端：

```bash
mvn spring-boot:run -pl ai-agent-station-study-app
```

前端：

```bash
cd ui
pnpm install
pnpm dev
```

## 3. 验证角色库接口

请求：

```http
GET /api/agent/role-library/list
```

期望：

- 返回结果只包含 Fix 角色
- 默认角色排在第一位
- 每个角色都包含 `agentId`、`agentName`、`description`、`defaultRole`

## 4. 验证“未选择角色直接聊天”

1. 进入前端 chat 模式
2. 不打开角色库，直接发送第一条消息
3. 检查：
   - 新建的 `ai_agent_conversation.ai_agent_id` 为默认角色
   - `ai_agent_name_snapshot` 已被写入
   - 返回内容走的是 Fix 策略，而不是深度研究/深度思考链路

## 5. 验证“主动选择其他角色”

1. 新建 chat 草稿
2. 点击角色库按钮，选择非默认角色
3. 发送第一条消息
4. 检查：
   - 会话绑定的是所选角色
   - `FixedAgentExecuteStrategy` 使用所选角色的 FlowConfig
   - 同一条消息换另一个新角色时，会创建新会话而不是污染原会话

## 6. 验证刷新恢复

1. 打开一个已绑定角色的历史会话
2. 刷新页面后重新进入
3. 检查：
   - 会话列表与详情都能展示角色名
   - 前端角色按钮显示当前绑定角色
   - 再发一条消息时仍使用同一角色

## 7. 验证历史会话兼容

1. 找到一条老的 chat 会话（`ai_agent_id` 为空）
2. 打开会话详情
3. 检查：
   - 页面仍展示一个可解释的默认角色摘要
   - 首次继续对话时不会报空指针
   - 若实现选择“懒补齐”，则本次发送后会把默认角色写回会话表

## 8. 验证角色失效处理

1. 将某个已绑定到历史会话的角色改为不可用（停用或破坏 FlowConfig）
2. 打开对应历史会话
3. 检查：
   - 历史消息仍可读
   - 会话角色摘要返回 `available=false`
   - 继续发送会收到结构化错误提示，不会进入真正的 Fix 执行

## 9. 建议执行的验证命令

后端测试：

```bash
mvn test -pl ai-agent-station-study-app -Dtest=FixedAgentExecuteStrategyTest,AiAgentDaoTest,AiAgentFlowConfigDaoTest
```

前端检查：

```bash
cd ui
pnpm lint
pnpm build
```
