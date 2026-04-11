# Contract: Role Library

## GET `/api/agent/role-library/list`

返回 chat 模式可选的 Fix 角色库。

### Response

```json
{
  "code": "0000",
  "info": "success",
  "data": [
    {
      "agentId": "85374287",
      "agentName": "测试Agent",
      "description": "测试Agent",
      "defaultRole": true
    },
    {
      "agentId": "20099179",
      "agentName": "111-测试model",
      "description": "111",
      "defaultRole": false
    }
  ]
}
```

### Rules

- 只返回满足 Fix 可执行条件的角色。
- 默认角色必须始终位于列表第一位。
- 当没有任何可用角色时，返回空数组；前端据此展示“当前暂无可用角色”提示。
- 返回结果不承担历史失效角色展示职责；历史会话的失效角色由会话接口单独返回。
