# Reactor UI

Reactor UI 是一个基于 React、TypeScript 和 Vite 的现代化项目。

## 功能特性

- 基于 React 19 和 TypeScript
- 使用 Vite 作为构建工具，提供快速的开发体验
- 集成 Ant Design 组件库
- 支持 Markdown 渲染
- 文件预览和处理功能
- 使用 ESLint 和 Prettier 进行代码规范化

## 快速开始

1. 安装依赖：

```bash
pnpm install
```

2. 启动开发服务器：

```bash
pnpm run dev
```

3. 在浏览器中打开 http://localhost:3000 查看应用。

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVICE_BASE_URL` | `http://127.0.0.1:8100` | 后端基础路径；生产留空走同源 |
| `VITE_SUBAGENT_CHILD_SESSION` | 未设置（等同 `0`） | 子智能体真实子会话开关，`1` 开启 |

### 子会话开关必须两端成对

`VITE_SUBAGENT_CHILD_SESSION=1` 只开启前端的「详情面板读真实子会话」；
后端必须同时把 `autobots.agent.subagent-isolated-session` 置为 `true`
（`application-{dev,test,prod}.yml`），子 Agent 才会把事件改道到独立子会话流。

- 只开后端：父流已经只剩 start/end 摘要帧，但前端详情面板仍读父时间线 → **面板空白**。
- 只开前端：拿不到 `childSessionId` 的父卡会自动回退旧的合成投影，不会报错，但也不会生效。

两端默认都是关，灰度时先后端后前端、回滚时先前端后后端。

## 可用脚本

- `pnpm dev`: 启动开发服务器
- `pnpm build`: 构建生产版本
- `pnpm lint`: 运行 ESLint 检查
- `pnpm fix`: 自动修复 ESLint 问题
- `pnpm preview`: 预览生产构建

## 贡献

我们欢迎所有形式的贡献！请查看 [CONTRIBUTING.md](CONTRIBUTING.md) 了解如何开始。

## 许可证

本项目基于 MIT 许可证开源。详情请查看 [LICENSE](LICENSE) 文件。
