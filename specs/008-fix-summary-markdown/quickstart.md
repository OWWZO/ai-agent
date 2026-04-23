# Quickstart: 修复 React/PlanSolve 最终总结 Markdown 展示验收

## 目标

本 Quickstart 用于实现完成后的验收，重点确认：

- `REACT / PLAN_SOLVE` 最终总结中的近似 Markdown 能被正确显示为标题、段落和列表
- 实时流式、最终完成态和历史回放看到的是同一套展示效果
- 代码块和已合法 Markdown 不会被误改
- 普通 `CHAT` 回复和其他 Markdown 场景不发生可见回归

## 0. 准备环境

启动后端与前端开发环境：

```powershell
cd D:\Java Code\ai-agent\ai-agent-station-study\ai-agent-station-study-app
mvn spring-boot:run
```

```powershell
cd D:\Java Code\ai-agent\ai-agent-station-study\ui
npm install
npm run dev
```

另开一个终端用于基础回归：

```powershell
cd D:\Java Code\ai-agent\ai-agent-station-study\ui
npm run lint
npm run build
```

## 1. 触发结构化总结样本

在前端页面里选择 `REACT` 或 `PLAN_SOLVE` 模式，输入一个容易产出分段总结的请求，例如：

```text
帮我整理一份“厦门好玩的地方推荐（按类型快速挑）”，按经典必去、文艺夜游、海边日落、自然观景、半日周边分组输出；最后再补一个“如果你想让我按1天/2天/3天排路线，需要补充什么信息”的结尾。
```

**预期**

- 总结流式生成过程中，页面不会直接裸露 `##`、`###`、`-` 等语法符号
- 类似 `###1）经典必去`、`##你如果想要...`、`-计划玩几天` 的内容会被展示为可读结构

## 2. 验证完成态展示

等待本轮结构化会话结束，确认最终总结区域。

**重点检查**

- 一级/二级小节能显示为标题，而不是纯文本前缀
- 结尾的补充项能显示成列表，而不是 `-计划玩几天`
- 总结完成后不会从“看起来正常”退化成“原始 Markdown”

## 3. 验证历史回放一致性

刷新页面或重新打开刚才的会话，再次查看同一条最终总结。

**预期**

- 历史回放的标题、段落和列表结构与首次查看时一致
- 即使该会话依赖历史 fallback 恢复总结，也不会再次裸露原始 Markdown 语法

## 4. 验证代码块保护

再发起一条结构化模式请求，让总结里带一段代码块，例如：

```text
先给我一个简短总结，再附一段 markdown 代码块，里面包含 ### 标题符号、- 列表符号和 1. 序号，最后再补一段正常结论。
```

**预期**

- 代码块中的 `###`、`-`、`1.` 保持原样
- 代码块外部的近似 Markdown 仍然能被正常修复

## 5. 验证非目标场景不回归

切换到普通 `CHAT` 模式，发送一条普通 Markdown 回复请求；同时抽查一个文件预览或其他 Markdown 展示入口。

**预期**

- 普通 `CHAT` 回复保持现有显示行为，不因本期规则出现意外换行或标题化
- 文件预览、HTML 转 Markdown 等其他场景不出现新的布局异常

## 6. 验收结论

当以下条件全部满足时，可以认为本期通过：

- `lint/build` 全部通过
- `REACT / PLAN_SOLVE` 最终总结的实时与历史展示一致
- 近似 Markdown 已被修复为可读结构
- 代码块与非目标场景无可见回归
