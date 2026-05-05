# Reactor AI Agent 对话平台前端

> Reactor AI Agent 对话平台前端项目，基于 React + TypeScript + Vite + Ant Design + Tailwind CSS 构建。

---

## 项目愿景

打造一个支持多 Agent 协作、深度研究、数据分析对话的 AI Agent 交互界面，通过 SSE（Server-Sent Events）流式协议与后端 Agent 服务实时通信，提供流畅的对话体验和丰富的可视化展示。

---

## 架构总览
整体结构

ui/
├── src/                    # 源代码主目录
│   ├── main.tsx            # 入口文件，挂载 React 应用到 DOM
│   ├── App.tsx             # 根组件，配置 Ant Design 中文环境 + 路由
│   ├── global.css          # 全局样式
│   ├── vite-env.d.ts       # Vite 环境变量类型声明
│   │
│   ├── router/             # 路由配置
│   │   └── index.tsx       # 定义 "/" 主页和 404 页面，懒加载组件
│   │
│   ├── layout/             # 布局层
│   │   └── index.tsx       # 最外层布局，初始化全局 message，包裹 <Outlet>
│   │
│   ├── pages/              # 页面
│   │   └── Home/index.tsx  # 主页面：未输入时显示欢迎页+案例展示，输入后切换到对话视图
│   │
│   ├── components/         # 组件库
│   │   ├── GeneralInput/   # 通用输入框（带文件上传、深度思考开关、发送按钮）
│   │   ├── ChatView/       # 核心对话视图，管理 SSE 流式通信和聊天状态
│   │   ├── Dialogue/       # 单条对话渲染（用户问题 + AI 回答）
│   │   │   └── DataDialogue.tsx  # 数据 Agent 专用对话展示（图表、表格）
│   │   ├── ActionView/     # 右侧"任务面板"，展示 Agent 执行过程、文件、浏览行为
│   │   │   ├── FileList.tsx      # 文件列表
│   │   │   ├── FilePreview.tsx   # 文件预览
│   │   │   ├── BrowserList.tsx   # 浏览行为列表
│   │   │   └── RunStatus.tsx     # 任务运行状态
│   │   ├── ActionPanel/    # 内容渲染面板（根据消息类型选择渲染器）
│   │   │   ├── MarkdownRenderer.tsx   # Markdown 渲染
│   │   │   ├── HTMLRenderer.tsx       # HTML 渲染
│   │   │   ├── TableRenderer.tsx      # 表格渲染
│   │   │   ├── SearchListRenderer.tsx # 搜索结果渲染
│   │   │   └── FileRenderer.tsx       # 文件渲染
│   │   ├── PlanView/       # 任务计划展示（Agent 执行计划步骤可视化）
│   │   ├── DataChat/       # 数据分析对话组件（图表 + 卡片 + 表格）
│   │   ├── DataListDrawer/ # 数据模型选择抽屉（选择要查询的数据库模型）
│   │   ├── Slogn/          # 首页标语动画组件（打字机效果）
│   │   ├── AttachmentList/ # 附件列表（显示上传的文件）
│   │   ├── LoadingDot/     # 加载中三点动画
│   │   ├── LoadingSpinner/ # 加载旋转动画
│   │   ├── Logo/           # Logo 组件
│   │   ├── Tabs/           # 标签页组件
│   │   └── NotFound.tsx    # 404 页面
│   │
│   ├── hooks/              # 自定义 Hook
│   │   ├── useTypeWriter.ts    # 打字机效果 Hook
│   │   ├── TypeWriterCore.ts   # 打字机核心逻辑
│   │   └── useConstants.ts     # 全局常量 Context Hook
│   │
│   ├── services/           # API 接口
│   │   ├── index.ts        # axios 封装（统一请求拦截）
│   │   └── agent.ts        # 业务 API（登录、白名单、数据模型查询等）
│   │
│   ├── utils/              # 工具函数
│   │   ├── querySSE.ts     # SSE 流式请求核心（fetch-event-source 封装）
│   │   ├── chat.ts         # 聊天数据处理（解析 Agent 返回的事件数据）
│   │   ├── constants.ts    # 全局常量（产品列表、示例问题、demo 案例）
│   │   ├── enums.ts        # 枚举定义
│   │   ├── request.ts      # HTTP 请求工具
│   │   └── utils.ts        # 通用工具（生成 ID、滚动等）
│   │
│   ├── types/              # TypeScript 类型定义
│   │   ├── chat.ts         # 对话相关类型（ChatItem、TInputInfo、Task 等）
│   │   ├── message.ts      # 消息协议类型（SSE 返回的数据结构）
│   │   └── global.ts       # 全局类型（ReactorType 命名空间）
│   │
│   └── assets/             # 静态资源
│       ├── icon/           # 文件类型图标（pdf、docx、excel 等）
│       ├── relayFonts/     # 自定义 iconfont 图标字体
│       └── styles/         # 样式文件
│           ├── RelayIcon.css       # 图标字体 CSS
│           ├── common.css          # 公共样式
│           └── github-markdown.css # Markdown 渲染样式（GitHub 风格）
│
├── .env                    # 环境变量（后端接口地址等）
├── index.html              # HTML 模板入口
├── eslint.config.js        # ESLint 代码规范配置
└── dist/                   # 构建输出目录
```
┌─────────────────────────────────────────────────────────────┐
│                        用户界面层                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  欢迎页面    │  │  对话视图    │  │     404 页面        │  │
│  │  (Home)     │  │  (ChatView) │  │   (NotFound)        │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                        组件层                               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  通用输入框  │  │  对话组件    │  │    动作视图         │  │
│  │(GeneralInp) │  │ (Dialogue)  │  │   (ActionView)      │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  动作面板    │  │  计划视图    │  │    数据对话         │  │
│  │(ActionPanel)│  │  (PlanView) │  │   (DataChat)        │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                        服务层                               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  API 封装    │  │  SSE 请求    │  │    数据处理         │  │
│  │  (agentApi) │  │  (querySSE) │  │    (chat.ts)        │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## 模块索引

| 模块路径 | 职责描述 | 关键文件 |
|---------|---------|---------|
| `src/pages/Home` | 主页面，欢迎页与对话视图切换 | `index.tsx` |
| `src/components/ChatView` | 对话视图核心，管理消息状态与 SSE 通信 | `index.tsx` |
| `src/components/GeneralInput` | 通用输入框组件，支持多模式输入 | `index.tsx` |
| `src/components/Dialogue` | 对话消息渲染，支持多种消息类型 | `index.tsx` |
| `src/components/ActionView` | 动作视图，展示工具执行详情 | `index.tsx` |
| `src/components/ActionPanel` | 动作面板，渲染各种内容类型 | `ActionPanel.tsx` |
| `src/components/PlanView` | 计划视图，展示任务计划与进度 | `PlanView.tsx` |
| `src/components/DataChat` | 数据对话组件，图表与表格展示 | `index.tsx` |
| `src/services` | API 接口封装 | `agent.ts`, `index.ts` |
| `src/utils` | 工具函数与常量 | `querySSE.ts`, `chat.ts`, `constants.ts` |
| `src/types` | TypeScript 类型定义 | `chat.ts`, `message.ts`, `global.ts` |
| `src/hooks` | 自定义 React Hooks | `useTypeWriter.ts`, `useConstants.ts` |
| `src/router` | 路由配置 | `index.tsx` |
| `src/layout` | 布局组件 | `index.tsx` |

---

## 运行与开发

### 环境要求

- Node.js >= 18
- pnpm (推荐)

### 安装依赖

```bash
pnpm install
```

### 开发模式

```bash
pnpm dev
# 或
npm run dev
```

服务默认运行在 `http://localhost:3000`

### 构建生产版本

```bash
pnpm build
# 或
npm run build
```

### 代码检查

```bash
pnpm lint      # 检查代码
pnpm fix       # 自动修复
```

---

## 三种对话模式

### 1. 聊天模式 (chat)

- 单一大模型对话
- 流式文本输出
- 简洁的问答交互

### 2. 多 Agent / 深度研究模式 (multiAgent / deepThink)

- 多智能体协作
- 任务计划展示
- 工具调用追踪
- 实时动作面板

### 3. 数据分析模式 (dataAgent)

- 智能问数
- 图表可视化 (ECharts)
- 表格数据展示
- 知识库集成

---

## 技术栈

| 类别 | 技术 |
|-----|------|
| 框架 | React 19 + TypeScript 5.7 |
| 构建工具 | Vite 6 |
| UI 组件库 | Ant Design 5.26 |
| 样式方案 | Tailwind CSS 4.1 |
| 路由 | React Router 7 |
| HTTP 客户端 | Axios |
| SSE 通信 | @microsoft/fetch-event-source |
| 图表 | ECharts 6 |
| 动画 | Framer Motion |
| Markdown 渲染 | react-markdown + remark-gfm |
| 代码高亮 | react-syntax-highlighter |

---

## 测试策略

当前项目暂未配置测试框架，建议后续集成：

- **单元测试**: Vitest + React Testing Library
- **E2E 测试**: Playwright
- **组件测试**: Storybook

---

## 编码规范

- ESLint 配置：`eslint.config.js`
- 使用 TypeScript 严格模式
- 组件使用函数式组件 + React Hooks
- 路径别名：`@/` 指向 `src/`
- 代码风格：严格借鉴项目的其他代码来写 要求写出来的代码风格符合其他已有代码

## Frontend Maintenance Notes

- `Home/index.tsx` 只保留入口装配；会话 bootstrap 和近期会话逻辑进入 `pages/Home` 子模块。
- `GeneralInput/index.tsx` 只保留输入编排；模式推导与上传状态机进入 `components/GeneralInput` 子模块。
- `WorkspaceMRag/index.tsx` 只保留 view 装配；知识库目录、文件管理、问答流分别进入独立 hook。
- `DataChat` 不再新增 JS 配置工具；图表配置一律走 TypeScript 纯函数。
- `ActionPanel` 和 `FilePreview` 的 renderer / title / navigation 派生统一走 resolver/model 纯函数。

---

## AI 使用指引

### 修改对话逻辑

对话核心逻辑位于 `src/components/ChatView/index.tsx`，包括：
- SSE 连接管理
- 消息状态更新
- 三种模式的切换逻辑

### 添加新的消息类型

1. 在 `src/types/message.ts` 中添加类型定义
2. 在 `src/utils/chat.ts` 中添加数据处理逻辑
3. 在 `src/components/Dialogue/index.tsx` 中添加渲染逻辑
4. 在 `src/components/ActionPanel/` 中添加对应渲染器

### 修改样式

- 全局样式：`src/global.css`
- 组件样式：使用 Tailwind CSS 类名
- 主题配置：`src/App.tsx` 中的 Ant Design ConfigProvider

### 添加 API 接口

在 `src/services/agent.ts` 中添加新的 API 方法：

```typescript
export const agentApi = {
  newApi: (data: SomeType) => api.post('/path/to/api', data),
};
```

---

## 变更记录 (Changelog)

| 时间 | 变更内容 |
|-----|---------|
| 2026-03-22 | 初始化 CLAUDE.md 文档，完成项目架构梳理 |

