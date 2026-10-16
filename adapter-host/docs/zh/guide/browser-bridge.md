# Browser Bridge 设置

> **⚠️ 重要**: 浏览器命令复用你的 Chrome 登录会话。运行命令前必须在 Chrome 中登录目标网站。

OpenCLI 通过轻量级 **Browser Bridge** Chrome 扩展 + 微守护进程连接浏览器（零配置，自动启动）。

## 扩展安装

### 方法 1：下载预构建版本（推荐）

1. 前往 GitHub [Releases 页面](https://github.com/jackwener/opencli/releases) 下载最新的 `opencli-extension-v{version}.zip`。
2. 解压后打开 `chrome://extensions`，启用**开发者模式**。
3. 点击**加载已解压的扩展程序**，选择解压后的文件夹。

### 方法 2：加载源码（开发者）

1. 打开 `chrome://extensions`，启用**开发者模式**。
2. 点击**加载已解压的扩展程序**，选择仓库中的 `extension/` 目录。

## 验证

```bash
opencli doctor            # 检查扩展 + 守护进程连接
```

## 多 Tab 定位

浏览器命令必须紧跟一个 `<session>` 位置参数。同一个多步骤流程使用同一个 session；并行任务使用不同 session 隔离。

```bash
opencli browser baidu open https://www.baidu.com/
opencli browser baidu click "a[target='_blank']" --follow-new-tab
opencli browser baidu tab list
opencli browser baidu tab new https://www.baidu.com/
opencli browser baidu eval --tab <targetId> 'document.title'
opencli browser baidu tab select <targetId>
opencli browser baidu get title
opencli browser baidu tab close <targetId>
```

规则如下：

- `opencli browser <session> open <url>` 和 `opencli browser <session> tab new [url]` 都会返回 `targetId`。
- `opencli browser <session> tab list` 会打印当前已存在 tab 的 `targetId`。
- `browser click` 会在结果的 `opened_pages` 中返回点击新打开的页面；`--follow-new-tab` 会选择第一个新页面，并优先选择当前 active tab，同时通过 Browser Bridge 扩展 lease 更新默认 tab。
- `--tab <targetId>` 会把单条 browser 命令路由到对应 tab。
- `tab new` 只会新建 tab，不会改变默认浏览器目标。
- `tab select <targetId>` 会更新 Browser Bridge 扩展 lease 中的默认 tab，CLI 不会再写第二份默认 target 缓存。
- `tab close <targetId>` 会关闭该 tab；扩展 lease 会移除已关闭 target，并在有其他存活 session tab 时保留 session。

Direct CDP 使用命令级 target 路由，不使用 lease。`tab list` 返回 CDP 端点中的 target，`--tab <targetId>` 只对当前命令生效，`tab select` 会返回提示要求使用 `--tab`。Direct CDP 的 `--follow-new-tab` 会返回 `selected_page`，但不会为后续命令保存默认 target。

## Session 生命周期

如果你希望多条 `opencli browser` 命令持续操作同一个页面，请使用稳定的 session 名称：

```bash
opencli browser my-session open https://example.com
opencli browser my-session state
opencli browser my-session extract "main"
```

OpenCLI 拥有的 browser session 使用交互式 tab lease，默认空闲超时为 10 分钟。完成后可以显式释放：

```bash
opencli browser my-session close
```

如果要把 OpenCLI 绑定到你已经手动打开的 Chrome tab，请使用 `opencli browser <session> bind`。绑定 session 没有 owned session 的 idle close 计时器，会一直保持到 `unbind`、tab 关闭、窗口关闭或 daemon 重启。对于 OpenCLI 自己创建的 owned session，使用 `--window foreground` 可以在可见自动化窗口里观察 OpenCLI 操作；使用 `--window background` 可以让这个自动化窗口留在后台。

`OpenCLI Browser` 和 `OpenCLI Adapter` tab group 是扩展管理的自动化容器；请不要把自己的长期 tab 放进去，也不要重命名。

## Daemon 生命周期

Daemon 在首次运行浏览器命令时自动启动，之后保持常驻运行。

```bash
opencli daemon stop      # 优雅关停
```

Daemon 为常驻模式，会一直运行直到你显式停止（`opencli daemon stop`）或卸载包。
