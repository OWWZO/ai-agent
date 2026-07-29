# XHS AI Toolkit

<p align="center">
  <b>让 AI 读懂你的小红书</b>
</p>

<p align="center">
  简体中文 | <a href="README.md">English</a>
</p>

<p align="center">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-blue.svg">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Linux%20%7C%20macOS-lightgrey.svg">
  <img alt="Python" src="https://img.shields.io/badge/python-3.10+-green.svg">
  <img alt="MCP" src="https://img.shields.io/badge/MCP-compatible-purple.svg">
</p>

---

小红书 AI 工具包 — 把你的收藏变成 AI 的记忆。

- **MCP 集成** — 通过 AI 助手搜索、浏览、评论小红书
- **热点跟踪** — 自动生成话题报告，含互动数据分析
- **记忆导出** — 将收藏/点赞笔记转为 AI 可搜索的知识库

基于 [xiaohongshu-mcp](https://github.com/xpzouying/xiaohongshu-mcp) 和 [XHS-Downloader](https://github.com/JoeanAmier/XHS-Downloader) 构建。

## 功能特性

| 功能 | 说明 |
|------|------|
| 搜索内容 | 按关键词搜索小红书笔记 |
| 首页推荐 | 获取首页推荐列表 |
| 帖子详情 | 获取笔记内容、评论、互动数据 |
| 发表评论 | 在笔记下发表评论 |
| 用户主页 | 获取用户资料和笔记列表 |
| 热点跟踪 | 自动生成话题分析报告 |
| 长图导出 | 将帖子导出为带注释的 JPG 长图 |
| 记忆导出 | 导出收藏/点赞为 Markdown 记忆库 |

## 快速开始

### 1. 安装 xiaohongshu-mcp

从 [GitHub Releases](https://github.com/xpzouying/xiaohongshu-mcp/releases) 下载：

```bash
# Linux x64
wget https://github.com/xpzouying/xiaohongshu-mcp/releases/latest/download/xiaohongshu-mcp-linux-amd64.tar.gz
wget https://github.com/xpzouying/xiaohongshu-mcp/releases/latest/download/xiaohongshu-login-linux-amd64.tar.gz

# macOS ARM
wget https://github.com/xpzouying/xiaohongshu-mcp/releases/latest/download/xiaohongshu-mcp-darwin-arm64.tar.gz
wget https://github.com/xpzouying/xiaohongshu-mcp/releases/latest/download/xiaohongshu-login-darwin-arm64.tar.gz
```

解压安装：

```bash
mkdir -p ~/.local/bin
tar -xzf xiaohongshu-mcp-*.tar.gz -C ~/.local/bin/
tar -xzf xiaohongshu-login-*.tar.gz -C ~/.local/bin/

cd ~/.local/bin
mv xiaohongshu-mcp-* xiaohongshu-mcp
mv xiaohongshu-login-* xiaohongshu-login
chmod +x xiaohongshu-mcp xiaohongshu-login
```

### 2. 安装本工具包

```bash
# 克隆到 OpenClaw workspace
git clone https://github.com/zhjiang22/openclaw-xhs.git
cp -r openclaw-xhs ~/.openclaw/workspace/skills/xiaohongshu

# 或使用软链接
ln -s /path/to/openclaw-xhs ~/.openclaw/workspace/skills/xiaohongshu

# 验证安装
cd ~/.openclaw/workspace/skills/xiaohongshu/scripts
./install-check.sh
```

### 3. 登录获取 Cookies

**方式一：本地桌面环境**

```bash
./login.sh  # 打开浏览器，用小红书 App 扫码登录
```

**方式二：Linux 服务器（无桌面）**

在本地电脑获取 cookies 后复制到服务器：

```bash
# 本地电脑（有 GUI）
./xiaohongshu-login
# Cookies 保存在 /tmp/cookies.json

# 复制到服务器
scp /tmp/cookies.json user@server:~/.xiaohongshu/cookies.json
```

服务启动时会自动检查以下位置的 cookies（按优先级）：

1. 环境变量 `XHS_COOKIES_SRC` 指定的路径
2. `~/cookies.json`
3. `~/.xiaohongshu/cookies.json`

### 4. 启动服务

```bash
./start-mcp.sh              # headless 模式
./start-mcp.sh --headless=false  # 显示浏览器（调试用）
```

服务监听 `http://localhost:18060/mcp`。

停止服务：`./stop-mcp.sh`

#### 服务器部署（无桌面环境）

在没有图形界面的 Linux 服务器上，`xiaohongshu-mcp` 底层的浏览器需要虚拟显示器才能正常工作。
`start-mcp.sh` 会**自动检测**是否有桌面环境，如果没有则自动启动 Xvfb，你只需提前安装：

```bash
# Debian/Ubuntu
sudo apt-get install -y xvfb

# CentOS/RHEL
sudo yum install -y xorg-x11-server-Xvfb
```

安装后无需额外配置，`start-mcp.sh` 会自动处理：
- 检测 `DISPLAY` 环境变量
- 没有显示器时自动启动 `Xvfb :99`
- `stop-mcp.sh` 停止服务时会一并清理 Xvfb 进程

> **提示**：如果不安装 Xvfb，登录和搜索功能会失败。参见 [Issue #3](https://github.com/zhjiang22/openclaw-xhs/issues/3)。

## 使用方法

### 基础命令

```bash
./status.sh                    # 检查登录状态
./search.sh "咖啡"              # 搜索内容
./recommend.sh                 # 获取推荐
./post-detail.sh <id> <token>  # 获取帖子详情
./comment.sh <id> <token> "写得真好！"  # 发表评论
./user-profile.sh <user_id> <xsec_token>  # 获取用户主页
```

### 热点跟踪

自动搜索热帖并生成分析报告：

```bash
./track-topic.sh "DeepSeek" --limit 10
./track-topic.sh "春节旅游" --limit 5 --output report.md
./track-topic.sh "iPhone 16" --limit 5 --feishu  # 导出到飞书
```

报告包含：
- 📊 概览统计（帖子数、点赞数、评论数）
- 📝 热帖详情（标题、作者、正文、热门评论）
- 💬 评论区热点关键词
- 📈 趋势分析

### MCP 工具清单

| 工具名 | 描述 |
|--------|------|
| `check_login_status` | 检查登录状态 |
| `search_feeds` | 搜索内容 |
| `list_feeds` | 获取首页推荐 |
| `get_feed_detail` | 获取帖子详情和评论 |
| `post_comment_to_feed` | 发表评论 |
| `user_profile` | 获取用户主页 |
| `like_feed` | 点赞/取消 |
| `favorite_feed` | 收藏/取消 |
| `publish_content` | 发布图文笔记 |
| `publish_with_video` | 发布视频笔记 |

### 通用 MCP 调用

```bash
./mcp-call.sh                  # 查看可用工具
./mcp-call.sh search_feeds '{"keyword": "咖啡"}'
./mcp-call.sh like_feed '{"feed_id": "xxx", "xsec_token": "xxx", "like": true}'
```

### 长图导出

将搜索结果或帖子详情导出为带文字注释的 JPG 长图：

```bash
# 准备 posts.json（搜索+拉详情后整理）
cat > posts.json << 'EOF'
[
  {
    "title": "帖子标题",
    "author": "作者名",
    "stats": "1.3万赞 100收藏",
    "desc": "正文摘要",
    "images": ["https://...webp"],
    "per_image_text": {"1": "第2张图的专属说明"}
  }
]
EOF

./export-long-image.sh --posts-file posts.json -o output.jpg
```

依赖：Python 3.10+、Pillow（`pip install Pillow`）

## 记忆导出：把收藏变成 AI 的记忆

将你的收藏/点赞笔记导出为 AI 可搜索的知识库，让 AI 更懂你。

### 1. 安装 XHS-Downloader

```bash
git clone https://github.com/JoeanAmier/XHS-Downloader.git
cd XHS-Downloader
pip install -r requirements.txt
```

### 2. 获取收藏/点赞链接（油猴脚本）

手动复制链接效率太低，推荐使用油猴脚本批量提取：

**安装脚本：**

1. 安装 [Tampermonkey](https://www.tampermonkey.net/) 浏览器扩展
2. 安装用户脚本：[XHS-Downloader.js](https://raw.githubusercontent.com/JoeanAmier/XHS-Downloader/refs/heads/master/static/XHS-Downloader.js)

**提取链接：**

1. 打开 [小红书网页版](https://www.xiaohongshu.com) 并登录
2. 进入个人主页 → **收藏** 或 **点赞** 页面
3. 点击 Tampermonkey 图标，选择：
   - `提取收藏作品链接`
   - `提取点赞作品链接`
4. 脚本会自动滚动页面加载全部内容
5. 提取完成后链接自动复制到剪贴板
6. 粘贴到 `links.md` 文件

> **注意**：自动滚动功能默认关闭，需在脚本设置中手动开启。开启后可能触发风控，建议适度使用。

### 3. 批量下载并导出

```bash
# 复制工具脚本到 XHS-Downloader 目录
cp tools/xhs-downloader/*.py /path/to/XHS-Downloader/

# 批量下载
cd /path/to/XHS-Downloader
python batch_download.py links.md

# 导出为多文件（推荐）
python export_to_workspace.py
# 输出到 ~/.openclaw/workspace/xhs-memory/

# 或导出为单文件
python export_memory.py
# 生成 xhs_memory.md
```

### 4. 配置 OpenClaw 记忆搜索

编辑 `~/.openclaw/openclaw.json`：

```json
{
  "memorySearch": {
    "extraPaths": [
      "~/.openclaw/workspace/xhs-memory"
    ]
  }
}
```

现在你的 AI 助手可以搜索你的小红书收藏了！

## 安全说明

本项目在脚本安全方面采取了以下措施：

- **Cookies 保护**：cookies 文件复制时自动设置 `600` 权限（仅当前用户可读写）
- **防注入**：所有 shell 脚本使用 `jq` 构建 JSON payload，不通过字符串拼接，防止 shell 注入攻击
- **工具名校验**：MCP 工具名限制为字母数字和下划线，拒绝非法字符
- **路径校验**：跨 skill 调用时校验目标路径在允许的目录范围内
- **第三方内容**：从小红书获取的内容为用户生成内容（UGC），请注意甄别


## 注意事项

1. **发布限制**
   - 标题最多 20 个字符
   - 正文最多 1000 个字符
   - 每日发布上限约 50 条

2. **账号安全**
   - 避免多设备同时登录同一账号
   - 手机 App 仅用于查看，不要同时操作

3. **首次运行**
   - 会自动下载 headless 浏览器（约 150MB）
   - 请确保网络畅通

4. **Cookies 有效期**
   - 通常有效期约 30 天
   - 失效后需重新获取

## 项目结构

```
openclaw-xhs/
├── README.md             # 英文文档
├── README_CN.md          # 中文文档
├── LICENSE
├── SKILL.md              # Skill 描述文件
├── scripts/              # MCP 调用脚本
│   ├── install-check.sh
│   ├── start-mcp.sh
│   ├── stop-mcp.sh
│   ├── login.sh
│   ├── mcp-call.sh
│   ├── status.sh
│   ├── search.sh
│   ├── recommend.sh
│   ├── post-detail.sh
│   ├── comment.sh
│   ├── user-profile.sh
│   ├── track-topic.sh
│   ├── track-topic.py
│   ├── export-long-image.sh
│   └── export-long-image.py
└── tools/
    └── xhs-downloader/   # 记忆导出工具
        ├── README.md
        ├── batch_download.py
        ├── export_memory.py
        └── export_to_workspace.py
```

## 声明

本项目是 [xiaohongshu-mcp](https://github.com/xpzouying/xiaohongshu-mcp) 的**调用封装层**。

- **不包含** xiaohongshu-mcp 的任何源代码
- **用户需自行下载** xiaohongshu-mcp 二进制文件
- 脚本仅通过 HTTP 协议与 MCP 服务通信

## 致谢

- [@xpzouying](https://github.com/xpzouying) — [xiaohongshu-mcp](https://github.com/xpzouying/xiaohongshu-mcp)
- [@JoeanAmier](https://github.com/JoeanAmier) — [XHS-Downloader](https://github.com/JoeanAmier/XHS-Downloader) (GPL-3.0)

## License

MIT License（仅限本项目脚本）

**注意：** xiaohongshu-mcp 项目未声明开源许可证，请遵守其作者的使用条款。

---

<p align="center">
  <b>觉得有用？给个 ⭐ 支持一下！</b>
</p>
