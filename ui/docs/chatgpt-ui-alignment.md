# UI 风格对齐说明

本文档记录 `ui` 项目向 `open-webui-main` / ChatGPT 风格靠拢时的差异梳理、可复用样式片段与改造优先级，便于后续继续迭代。

## 1. 原项目 vs 参考项目差异

### 视觉层

| 维度 | 原项目 | 参考风格 | 当前优化方向 |
| --- | --- | --- | --- |
| 配色 | 蓝色强调较重，灰阶层级不统一 | 中性色主导，强调色克制 | 统一为黑 / 灰 / 白体系，保留功能色但降低视觉侵略性 |
| 字体 | 默认 UI 字体偏传统 | 现代无衬线，字重和层级更克制 | 使用 `Geist` 变量字体，统一标题 / 正文 / 辅助文案节奏 |
| 表面层 | 纯色块较多，层次依赖边框 | 半透明卡片 + 极轻阴影 + 大圆角 | 引入 `surface / panel / border / shadow` token |
| 组件风格 | 输入框、按钮、气泡样式割裂 | 统一圆角、内边距、悬停反馈 | 对齐输入框、按钮、列表卡片、对话气泡 |

### 交互层

| 维度 | 原项目 | 参考风格 | 当前优化方向 |
| --- | --- | --- | --- |
| 对话气泡 | 用户消息蓝底、助手消息块感偏强 | 用户消息轻气泡，助手内容更像正文流 | 用户消息改浅灰气泡，助手消息弱容器化 |
| Hover / Active | 局部反馈不足 | 细微透明度 / 阴影 / 位移动效 | 补齐按钮、操作栏、列表项 hover / active |
| 加载态 | loading 风格独立 | 与正文风格一致、弱强调 | loading dot 改中性灰，统一消息流左侧对齐 |
| 焦点态 | 输入框聚焦感较弱 | 聚焦后容器更明确 | 输入容器增加边框、阴影、背景过渡 |

### 布局层

| 维度 | 原项目 | 参考风格 | 当前优化方向 |
| --- | --- | --- | --- |
| 主布局 | 侧边栏与主区关系偏硬 | 侧边栏弱化、主聊天区更聚焦 | 调整为更宽松的卡片布局与更自然的比例 |
| 响应式 | 中断点切换突兀 | PC / 平板 / 移动端过渡柔和 | 调整欢迎页、会话页和侧栏的断点与内边距 |
| 列表容器 | 项目感较重 | 更像会话工作台 | 历史列表、案例卡片统一卡片节奏 |

### 动效层

| 维度 | 原项目 | 参考风格 | 当前优化方向 |
| --- | --- | --- | --- |
| 骨架 / 流式输出 | 风格分散 | 微动效低存在感 | 统一 loading dot、滚动按钮、浮层过渡 |
| 侧栏展开 | 默认抽屉风格 | 柔和面板切换 | 移动端抽屉与桌面端侧栏统一语义和层次 |
| 按钮点击反馈 | 普通 hover | 细微位移与阴影 | 主按钮、案例卡片、列表项统一过渡曲线 |

## 2. 已落地的核心改造

- 全局主题 token 已收敛到 `src/global.css`，包含背景、边框、阴影、字体、滚动条与选择态样式。
- 欢迎页、聊天页、侧边栏、会话列表、输入区、对话气泡已切到同一套中性色现代风格。
- 用户消息气泡已改为浅灰卡片，助手消息改为更轻的正文流布局。
- 输入框区已改为 ChatGPT 风格的圆角合成输入面板，发送按钮改为黑色主按钮。
- 历史会话列表、新建对话、滚动到底部按钮、提示卡片、计划卡片、任务卡片已统一圆角与阴影节奏。
- 数据分析视图与普通对话视图的外层容器已统一到相同的 shell 结构。

## 3. 可复用代码片段

### 3.1 主题变量

```css
:root {
  --page-shell: linear-gradient(180deg, #f7f7f8 0%, #f3f4f6 100%);
  --panel-bg: rgba(255, 255, 255, 0.72);
  --surface-bg: rgba(255, 255, 255, 0.86);
  --surface-border: rgba(15, 23, 42, 0.08);
  --surface-shadow: 0 30px 70px -42px rgba(15, 23, 42, 0.32);
  --font-brand: "Geist Variable", "PingFang SC", "Microsoft YaHei", sans-serif;
}
```

### 3.2 聊天外层容器

```tsx
<div className="mx-auto flex h-full w-full max-w-[1480px] px-2 pb-3 pt-2 sm:px-4 xl:px-6 xl:pt-4">
  <div className="mx-auto flex min-h-0 w-full max-w-[1040px] flex-1 flex-col overflow-hidden rounded-[32px] border border-black/5 bg-white/72 shadow-[0_30px_70px_-42px_rgba(15,23,42,0.32)] backdrop-blur-xl">
    {children}
  </div>
</div>
```

### 3.3 用户 / 助手消息样式

```tsx
<Message from="user" className="max-w-[82%]">
  <MessageContent>{query}</MessageContent>
</Message>

<Message from="assistant" className="w-full max-w-full">
  <MessageContent>
    <MessageResponse>{response}</MessageResponse>
  </MessageContent>
</Message>
```

### 3.4 输入区模式

```tsx
<PromptInput className="w-full" onSubmit={handleSubmit}>
  <PromptInputBody>
    <PromptInputTextarea
      className="min-h-[88px] text-[15px] leading-7 text-[#111827] placeholder:text-[#9ca3af]"
    />
  </PromptInputBody>
  <PromptInputFooter>
    <PromptInputTools />
    <PromptInputSubmit className="h-10 w-10 rounded-full bg-[#111827] text-white transition-transform duration-200 hover:scale-[1.02]" />
  </PromptInputFooter>
</PromptInput>
```

### 3.5 统一卡片交互

```tsx
<div className="rounded-[26px] border border-black/5 bg-white/88 shadow-[0_20px_52px_-36px_rgba(15,23,42,0.32)] transition-all duration-300 hover:-translate-y-[4px] hover:border-black/10 hover:shadow-[0_24px_56px_-34px_rgba(15,23,42,0.38)]">
  {children}
</div>
```

## 4. 分步改造优先级

### P0 核心样式

1. 统一全局 token。
2. 重做输入区、消息气泡、聊天容器、侧边栏与会话列表。
3. 去除高饱和蓝色视觉干扰，建立灰阶系统。

### P1 交互动效

1. 统一按钮 hover / active / focus 反馈。
2. 统一消息 loading dot、滚动到底部按钮和操作栏显隐方式。
3. 优化侧边栏、下拉菜单、卡片悬停的过渡曲线。

### P2 响应式适配

1. 调整桌面端侧边栏宽度与聊天区最大宽度。
2. 优化平板与移动端的抽屉、欢迎页、输入区留白。
3. 保证聊天流、案例卡片、输出模式切换在小屏下仍可读可点。

## 5. 后续建议

- 当前构建已通过，但打包产物仍有 `chunk size` 过大告警，下一步建议按聊天页 / 数据页 / 富渲染组件拆包。
- `lottie-web` 仍存在 `eval` 告警，建议后续评估是否替换资源或延迟加载。
- 如果继续贴近 Open WebUI，可以再追加:
  - 更细腻的流式打字机渲染节奏
  - 更完整的骨架屏状态
  - 侧边栏折叠态与最近会话分组
