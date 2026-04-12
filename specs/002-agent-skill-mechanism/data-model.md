# Data Model: Agent Skill Mechanism

## 1. SkillDefinition

- **Purpose**: 表示一个已注册的运行时 skill。
- **Source**: `SKILL.md` front matter + 正文内容。
- **Fields**:
  - `name`: skill 唯一名称，用于 `skill_tool` 查找
  - `description`: 给模型看的技能摘要
  - `basePath`: skill 根目录绝对路径
  - `content`: 去除 front matter 后的 `SKILL.md` 正文
  - `frontMatter`: 原始 front matter 扩展字段
  - `scripts`: 该 skill 下可执行脚本的映射
- **Validation**:
  - `name`、`description` 必填
  - `name` 在运行时注册范围内必须唯一

## 2. SkillScriptDefinition

- **Purpose**: 统一描述一个可被 `script_runner_tool` 执行的脚本。
- **Source**:
  - 优先来自 `scripts.yaml`
  - 若无配置，则来自 `scripts/` 自动扫描
- **Fields**:
  - `scriptName`: 脚本逻辑名/别名
  - `relativePath`: 相对 skill 根目录的路径
  - `absolutePath`: 解析后的绝对路径
  - `runtime`: `python | node | shell | powershell | bat`
  - `description`: 给模型看的脚本说明
  - `metadata`: 其他扩展字段
- **Validation**:
  - 路径必须落在所属 skill 根目录内
  - runtime 必须由显式声明或文件后缀成功解析

## 3. SkillRegistry

- **Purpose**: 运行时 skill 注册中心。
- **Responsibilities**:
  - 扫描配置目录
  - 解析 `SKILL.md`
  - 发现并缓存脚本定义
  - 处理重名冲突
  - 提供 path guard
- **Lifecycle**:
  - 应用启动时初始化
  - 配置变更或显式刷新时重建缓存

## 4. SkillProperties

- **Purpose**: skill 机制运行时配置。
- **Fields**:
  - `enabled`
  - `directories`
  - `maxReadChars`
  - `maxListEntries`
  - `maxGlobResults`
  - `maxGrepMatches`
  - `defaultScriptTimeoutSeconds`
- **Notes**:
  - 配置归属 `app`
  - 业务逻辑不放入配置类

## 5. SkillToolRequest / SkillToolResult

- **Purpose**: `skill_tool` 的输入输出载体。
- **Input**:
  - `skillName`
- **Output**:
  - `name`
  - `description`
  - `basePath`
  - `content`
  - `availableScripts`
- **Behavior**:
  - 不存在的 skill 返回明确错误文本

## 6. ScriptRunnerToolRequest

- **Purpose**: Java 本地工具调用 Python 执行端点时的统一请求。
- **Fields**:
  - `requestId`
  - `skillName`
  - `skillBasePath`
  - `scriptName`
  - `scriptPath`
  - `runtime`
  - `arguments`
  - `argv`
  - `timeoutSeconds`
- **Validation**:
  - `skillName` 与 `scriptName` 必须命中注册中心
  - `scriptPath` 必须位于 `skillBasePath` 内

## 7. ScriptRunResult

- **Purpose**: `reactor-tool` 返回给 Java 的统一脚本执行结果。
- **Fields**:
  - `requestId`
  - `skillName`
  - `scriptName`
  - `runtime`
  - `success`
  - `exitCode`
  - `stdout`
  - `stderr`
  - `summary`
  - `fileInfo[]`
- **Notes**:
  - `fileInfo` 复用现有文件上传返回格式
  - `summary` 用于给模型和日志快速理解执行结果

## 8. SkillFileAccessTool

- **Purpose**: skill 目录内本地只读访问工具族。
- **Members**:
  - `read_tool`
  - `list_directory_tool`
  - `glob_tool`
  - `grep_tool`
- **Shared Rule**:
  - 所有路径必须通过 `SkillRegistry` 的 path guard 校验
  - 越界访问统一返回错误并记录日志

## Relationships

- `SkillRegistry` 1:N `SkillDefinition`
- `SkillDefinition` 1:N `SkillScriptDefinition`
- `skill_tool` 读取 `SkillDefinition`
- `script_runner_tool` 将 `SkillScriptDefinition` 转为 `ScriptRunnerToolRequest`
- `reactor-tool` 将 `ScriptRunnerToolRequest` 执行为 `ScriptRunResult`
