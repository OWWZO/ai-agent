# Contract: Skill Tooling

## 1. `skill_tool`

### Request Schema

```json
{
  "skill_name": "sql-analysis"
}
```

### Response Shape

```text
技能名称：sql-analysis
技能描述：...
技能目录：D:/.../runtime/skills/sql-analysis

可用脚本：
- summarize | runtime=python | path=scripts/summarize.py | 自动发现脚本

===== SKILL.md =====
...
```

### Rules

- `skill_name` 必须命中已注册 skill
- 返回中必须包含绝对基路径
- 返回中应列出可执行脚本摘要，供模型继续调用 `script_runner_tool`

## 2. `script_runner_tool`

### Request Schema

```json
{
  "skill_name": "sql-analysis",
  "script_name": "summarize",
  "arguments": {
    "table": "sales_data"
  },
  "argv": [
    "--limit",
    "10"
  ],
  "timeout_seconds": 120
}
```

### Response Shape

```text
skill=sql-analysis
script=summarize
runtime=python
exitCode=0
success=true
summary=脚本执行成功

stdout:
...

生成文件：
- summary.md
```

### Rules

- 仅允许执行注册中心已发现的脚本
- `arguments` 与 `argv` 同时支持，均为可选
- 超时未传时使用默认配置
- 若产出文件，需透传到 agent 文件上下文

## 3. `read_tool`

### Request Schema

```json
{
  "path": "D:/.../runtime/skills/sql-analysis/references/metrics.md",
  "start_line": 1,
  "line_count": 80
}
```

### Rules

- 只能读取已注册 skill 根目录内的文件
- 响应允许截断，但必须提示已截断

## 4. `list_directory_tool`

### Request Schema

```json
{
  "path": "D:/.../runtime/skills/sql-analysis/scripts",
  "max_depth": 2
}
```

### Rules

- 只能访问已注册 skill 根目录内目录
- 返回类型应区分文件和目录

## 5. `glob_tool`

### Request Schema

```json
{
  "path": "D:/.../runtime/skills/sql-analysis",
  "pattern": "references/**/*.md"
}
```

## 6. `grep_tool`

### Request Schema

```json
{
  "path": "D:/.../runtime/skills/sql-analysis",
  "pattern": "revenue",
  "regex": false,
  "case_sensitive": false
}
```

### Shared Guard

- 对于越界路径、未注册 skill、未注册脚本，工具必须返回明确错误文本，不得静默失败
