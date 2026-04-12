# Quickstart: Agent Skill Mechanism

## 1. 准备运行时目录

在仓库根目录创建运行时 skill 目录，例如：

```text
runtime/skills/sql-analysis/
├── SKILL.md
├── scripts/
│   └── summarize.py
├── references/
│   └── metrics.md
└── scripts.yaml   # 可选
```

## 2. 配置 skill 开关

在应用配置中启用 skill 功能，并声明运行时目录：

```yaml
autobots:
  autoagent:
    skill:
      enabled: true
      directories:
        - D:/Java Code/ai-agent/ai-agent-station-study/runtime/skills
```

## 3. 启动依赖服务

1. 启动 `reactor-tool`
2. 启动 Java 主应用
3. 确认 `autobots.autoagent.code_interpreter_url` 指向可用的 `reactor-tool`

## 4. 准备 demo skill

`SKILL.md` 示例：

```md
---
name: sql-analysis
description: 读取 SQL 分析规则并执行汇总脚本。
---

# SQL Analysis

先阅读 references 中的规则，再决定是否执行脚本。
```

`scripts/summarize.py` 示例行为：

- 接收环境变量中的 `SKILL_ARGUMENTS_JSON`
- 处理输入后把结果写入 `output/summary.md`

## 5. 验证路径

### 验证 `skill_tool`

向 `PlanSolve/ReAct` 提问一个明显匹配该 skill 的请求，确认模型可以看到并调用 `skill_tool`，返回：

- skill 名称
- skill 描述
- skill 根目录
- `SKILL.md` 正文
- 可用脚本列表

### 验证本地文件工具

让模型继续调用：

- `read_tool` 读取 `references/metrics.md`
- `list_directory_tool` 浏览 `scripts/`
- `glob_tool` 搜索 `references/**/*.md`
- `grep_tool` 搜索某个关键字

### 验证 `script_runner_tool`

让模型调用：

- `skill_name=sql-analysis`
- `script_name=summarize`
- `arguments={...}`
- `argv=[...]`

预期结果：

- 返回 stdout/stderr/exitCode
- 如脚本产出文件，结果中附带 `fileInfo`
- 生成文件被加入当前 agent 上下文文件列表

### 验证最小安全边界

尝试两类错误调用：

1. 调用未注册脚本
2. 读取 skill 根目录外文件

预期结果：

- 工具调用失败但 agent 初始化不受影响
- 返回明确错误文本
- 服务端记录错误日志
