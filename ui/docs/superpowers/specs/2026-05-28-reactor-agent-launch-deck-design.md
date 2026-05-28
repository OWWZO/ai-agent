# Design: Reactor Agent Launch Control Demo Deck

**Date:** 2026-05-28
**Scope:** Reactor Agent 对外演示文稿（官网视觉介绍 + 独立转发浏览）
**Approach:** Option A amplified — `Launch Control`

---

## Goal

为 `Reactor Agent` 设计一套更接近发布会气质的短篇演示文稿，用于对外快速传达项目核心价值：

- 它不是普通聊天框式 AI 产品
- 它强调 `多智能体协同任务`
- 它已经具备真实产品形态与可运行证据

这套 deck 既要适合放在官网/项目主页做视觉介绍，也要能单独转发给别人浏览，不依赖现场讲解才能理解。

---

## Audience

### Primary audience

- 泛行业观众
- 对 AI 产品有基本认知，但不关心底层工程分层和实现细节

### Secondary audience

- 开发者 / 技术社区
- 会关注产品是否真的具备协同执行、工具闭环和工程落地能力

### Audience takeaway

观众看完后应该记住一句话：

> `Reactor Agent 是一个面向复杂 AI 工作流的多智能体协同任务工作站。`

---

## Constraints

- 交付物必须同时支持：
  - HTML 演示版
  - 可编辑 PPTX
- 因为需要导出可编辑 PPTX，HTML 从第一行开始就必须遵守可编辑导出约束：
  - 使用标准段落/标题标签承载主文字
  - 背景、边框、阴影由容器层承担
  - 不依赖 CSS 渐变、复杂 SVG、伪元素文本、web component
  - 图片必须使用真实 `<img>` 资源
- 视觉风格要足够“狠”和“科技”，但不能为了效果牺牲导出稳定性
- 不编造产品能力、业务数据或虚假场景；所有核心论断都必须能被 README 和现有 UI 资产支撑

---

## Messaging Hierarchy

### Core message

`多智能体协同任务`

### Supporting messages

1. `不是回答问题，而是组织任务完成`
2. `不是单轮工具调用，而是可闭环的协同执行链`
3. `不是概念 Demo，而是已有真实工作区、产物与历史回放能力`

### Message discipline

- 不把篇幅浪费在技术栈枚举上
- 不展开 DDD 分层、数据库表结构、SSE 等工程实现细节
- 只保留足够支撑可信度的技术语言，如 `Multi-Agent`、`Tool Chain`、`Memory`、`Replay`

---

## Visual System

### Overall direction

整体风格采用 `Launch Control`：

- 接近 Apple 发布会的冷峻发布感
- 更偏 AI 基础设施产品，而不是消费级炫技页面
- 通过极强的尺度、留白、对比度和真实截图建立高级感

### Color system

- `Obsidian`：#06080D
- `Cold White`：#F5F7FB
- `Steel Gray`：#8C96A8
- `Signal Blue`：#2CB8FF
- `Control Violet`：#6F47FF

使用原则：

- 90% 页面以深底 + 浅字为主
- `Signal Blue` 用于任务链路、状态点、关键词、数字
- `Control Violet` 只做少量辅助强调，避免整套 deck 变成泛 AI 紫色模板
- 页面不依赖大面积渐变，保证 PPTX 导出一致性

### Typography

考虑 PPTX 可编辑性，字体使用稳定系统栈：

- Display: `Aptos, Segoe UI, Helvetica Neue, Arial, sans-serif`
- Body: `Aptos, Segoe UI, Helvetica Neue, Arial, sans-serif`

排版策略：

- 超大标题 44pt - 72pt
- 副标题 18pt - 24pt
- 正文 14pt - 18pt
- 通过字重、字距、行长与留白建立“发布会感”，而不是依赖特殊字体

### Material language

- 细描边框体
- 冷色发光点
- 极简连线
- 真实产品截图作为“证据材料”
- 少量半透明深色面板承载说明文字

明确避免：

- 满屏渐变
- 装饰性图标雨
- 复杂 3D 透视组件
- 伪 Apple 玻璃拟物堆叠

---

## Deck Grammar

整套 deck 的统一语法如下：

- 每页只讲一个判断，不做传统汇报式“一个标题 + 五条 bullet”
- 大标题必须像断言句，而不是章节名
- 页面中的“协同链路”会作为贯穿性主视觉出现，形成跨页连续性
- 每页至少保留一块明显留白，保证信息有呼吸感
- 每个视觉元素都服务于“协同任务”叙事，不做无意义装饰

---

## Slide Plan

### Slide 1 · Cover

**Purpose**

建立项目的第一印象与气场。

**Primary copy**

- `Reactor Agent`
- `多智能体协同任务工作站`

**Visual treatment**

- 超大标题置中或偏左
- 深色背景上只有一条被点亮的执行路径
- 页面元素极少，靠比例和克制建立高级感

### Slide 2 · Reframe the Problem

**Purpose**

快速把项目从“聊天型 AI”中区分出来。

**Primary copy**

`大多数 AI 产品只会回答。`
`Reactor 负责协同完成任务。`

**Visual treatment**

- 双句对比式排版
- 左右或上下形成强烈断裂
- 用一处高亮色落在 `协同` / `任务` 两个关键词上

### Slide 3 · Capability Panorama

**Purpose**

用最少的结构讲清能力闭环。

**Primary modules**

- 任务拆解
- 多 Agent 协同
- 工具链执行
- 记忆与回放

**Visual treatment**

- 四个模块不做均匀卡片墙
- 更像发布会式能力地图：1 个主模块 + 3 个辅助模块围绕主线展开

### Slide 4 · Coordination Flow

**Purpose**

把“多智能体协同任务”具体化成一条易懂的执行旅程。

**Narrative chain**

`理解目标 → 拆分任务 → 并发执行 → 汇总结果 → 沉淀事实`

**Visual treatment**

- 使用一条贯穿全页的任务路径
- 节点文案简短，重点在节奏与方向感
- 不做重型技术架构图

### Slide 5 · Product Evidence

**Purpose**

证明项目已经具备真实产品形态，不是概念稿。

**Assets**

- 首页对话界面截图
- 工作区 / 文件结果展示截图
- 视版式需要，可补充 ReAct 或 Plan Execute 截图

**Visual treatment**

- 截图放大使用，像发布会展示真实产品一样处理
- 少量标注说明“协同执行”“工作区产物”“结果回放”这几类证据

### Slide 6 · Why It Matters

**Purpose**

把价值判断收紧为一句可传播的差异陈述。

**Primary copy**

`从单 Agent Demo，走向可追踪、可复用、可扩展的协同系统。`

**Visual treatment**

- 强对比排版
- 可以采用左侧旧范式、右侧新范式的结构，但必须克制
- 不堆大量对比表格

### Slide 7 · Closing / CTA

**Purpose**

完成收口，适合官网尾屏或独立转发落点。

**Primary copy**

`为复杂 AI 工作流而生的多智能体协同底座`

**Supporting content**

- 项目名
- 仓库入口或占位链接
- 可选的一句短 CTA

**Visual treatment**

- 回到最纯净的品牌终章
- 让结尾像发布会尾页，而不是传统“谢谢”

---

## Asset Plan

### Confirmed assets

- Logo：`ui/src/components/Logo/logo.png`
- 产品截图：
  - `assets/readme/2e138de7-0974-401b-bf01-15e59cf55b47.png`
  - `assets/readme/f337b673-263c-4809-b560-cb382dba2e59.png`
  - `assets/readme/deepsearch展示.png`
  - `assets/readme/planexecute展示.png`
- 文案来源：根目录 `README.md`

### Asset usage rules

- Logo 只在封面和结尾页作为品牌识别使用，不在每页重复堆放
- 截图优先大尺度使用，避免缩成小图证据墙
- README 的能力描述会被压缩成发布会级短文案，而不是直接搬运段落

---

## Technical Delivery

### Output format

1. HTML 演示版
2. 从同一套 HTML 导出的可编辑 PPTX

### HTML architecture

- 采用多文件 slide 结构
- 每页独立 HTML，最后用聚合入口串联
- 画布按可编辑 PPTX 推荐尺寸实现

### Why this architecture

- 每页样式隔离，稳定性更高
- 更适合后续增删页与单页调整
- 更容易保障 PPTX 导出时的版式一致性

### Implementation discipline

- 主文字全部放在 `<h1>-<h6>` 与 `<p>` 标签中
- 背景和边框放在外层容器
- 所有图像使用真实图片资源
- 避免任何只在 HTML 中好看、但无法稳定导出到 PPTX 的表现手法

---

## Verification

交付前至少完成以下验证：

1. HTML 版本逐页检查无破图、无文字溢出、无布局错位
2. 聚合版浏览器翻页正常
3. PPTX 导出成功，且标题、正文可直接编辑
4. 关键截图在 PPTX 中位置稳定，不发生缩放漂移
5. 全文没有遗留占位文案、`TODO` 或临时说明

---

## Non-Goals

- 不做完整项目商业官网
- 不做长篇技术汇报 PPT
- 不覆盖所有功能细节
- 不追求重动画视频感交付

本次目标是：用 7 页以内的发布会式 deck，把 `Reactor Agent` 的价值讲狠、讲准、讲可信。
