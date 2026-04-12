# Contract: `reactor-tool` Script Runner API

## Endpoint

- **Method**: `POST`
- **Path**: `/v1/tool/script_runner`

## Request Body

```json
{
  "requestId": "req-001",
  "skillName": "sql-analysis",
  "skillBasePath": "D:/Java Code/ai-agent/ai-agent-station-study/runtime/skills/sql-analysis",
  "scriptName": "summarize",
  "scriptPath": "scripts/summarize.py",
  "runtime": "python",
  "arguments": {
    "table": "sales_data"
  },
  "argv": [
    "--limit",
    "10"
  ],
  "timeoutSeconds": 120
}
```

## Execution Rules

- `runtime` 枚举：`python`、`node`、`shell`、`powershell`、`bat`
- `skillBasePath` 与 `scriptPath` 组合后必须位于 skill 根目录内
- 仅执行 Java 侧已通过注册中心校验的脚本
- `arguments` 通过 JSON 文件与环境变量传递给脚本
- `argv` 追加到真实命令行参数中
- 执行工作目录建议为 skill 根目录下的临时副本或隔离工作目录
- 结果中的产出文件统一上传到现有文件服务

## Response Body

```json
{
  "requestId": "req-001",
  "skillName": "sql-analysis",
  "scriptName": "summarize",
  "runtime": "python",
  "success": true,
  "exitCode": 0,
  "stdout": "done",
  "stderr": "",
  "summary": "脚本执行成功",
  "fileInfo": [
    {
      "fileName": "summary.md",
      "ossUrl": "https://...",
      "domainUrl": "https://...",
      "fileSize": 1024
    }
  ]
}
```

## Error Cases

### Unregistered Script / Path Escape

```json
{
  "requestId": "req-001",
  "skillName": "sql-analysis",
  "scriptName": "hack",
  "runtime": "python",
  "success": false,
  "exitCode": -1,
  "stdout": "",
  "stderr": "script path is outside registered skill directory",
  "summary": "脚本执行被拒绝",
  "fileInfo": []
}
```

### Timeout

```json
{
  "requestId": "req-001",
  "skillName": "sql-analysis",
  "scriptName": "summarize",
  "runtime": "python",
  "success": false,
  "exitCode": -1,
  "stdout": "",
  "stderr": "execution timed out after 120 seconds",
  "summary": "脚本执行超时",
  "fileInfo": []
}
```
