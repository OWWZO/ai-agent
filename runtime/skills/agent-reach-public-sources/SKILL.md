---
name: agent-reach-public-sources
description: >
  使用本机或沙箱中的公开互联网工具读取 RSS、YouTube（包括公开视频评论）、Bilibili、雪球、V2EX、
  GitHub、LinkedIn，以及 Hacker News、Stack Exchange、Mastodon 和 Reddit RSS。用户提到这些平台、URL、搜索、
  视频详情、字幕、帖子、仓库、个人主页或公司主页时使用本 skill；
  需要登录、Cookie、发帖、评论点赞等写操作、修改仓库或抓取 LinkedIn 非公开内容时不要绕过平台限制。
---

# Agent Reach Public Sources

这个 skill 把公开内容请求路由到上游命令。先用 `skill_tool` 加载本文件，再用 `bash` 在沙箱中执行命令；不要把下面的命令当作 Java、Python 或 MCP API 来调用。

## 统一入口

优先运行 `fetch_public`，它会校验公开 URL、限制响应大小，并返回统一 JSON。脚本路径是 `skills/agent-reach-public-sources/scripts/fetch_public.py`：

```bash
python skills/agent-reach-public-sources/scripts/fetch_public.py rss read --url "FEED_URL" --limit 10
python skills/agent-reach-public-sources/scripts/fetch_public.py youtube search --query "QUERY" --limit 5
python skills/agent-reach-public-sources/scripts/fetch_public.py youtube detail --url "VIDEO_URL"
python skills/agent-reach-public-sources/scripts/fetch_public.py youtube comments --url "VIDEO_URL" --limit 20
python skills/agent-reach-public-sources/scripts/fetch_public.py bilibili search --query "QUERY" --limit 5
python skills/agent-reach-public-sources/scripts/fetch_public.py bilibili detail --bvid "BVxxx"
python skills/agent-reach-public-sources/scripts/fetch_public.py bilibili comments --bvid "BVxxx" --limit 20
python skills/agent-reach-public-sources/scripts/fetch_public.py bilibili replies --bvid "BVxxx" --rpid 123456 --limit 20
python skills/agent-reach-public-sources/scripts/fetch_public.py xueqiu quote --symbol "SH600519"
python skills/agent-reach-public-sources/scripts/fetch_public.py xueqiu search --query "贵州茅台" --limit 10
python skills/agent-reach-public-sources/scripts/fetch_public.py xueqiu hot-posts --limit 20
python skills/agent-reach-public-sources/scripts/fetch_public.py xueqiu hot-stocks --limit 10
python skills/agent-reach-public-sources/scripts/fetch_public.py v2ex hot --limit 10
python skills/agent-reach-public-sources/scripts/fetch_public.py v2ex node --name "python" --limit 10
python skills/agent-reach-public-sources/scripts/fetch_public.py github repo --repo "OWNER/REPO"
python skills/agent-reach-public-sources/scripts/fetch_public.py github readme --repo "OWNER/REPO"
python skills/agent-reach-public-sources/scripts/fetch_public.py github search-repos --query "QUERY" --limit 10
python skills/agent-reach-public-sources/scripts/fetch_public.py linkedin read --url "https://www.linkedin.com/company/COMPANY"
python skills/agent-reach-public-sources/scripts/fetch_public.py hackernews top --limit 3
python skills/agent-reach-public-sources/scripts/fetch_public.py hackernews item --id 1
python skills/agent-reach-public-sources/scripts/fetch_public.py stackexchange questions --site stackoverflow --query "QUERY" --tag python --limit 10
python skills/agent-reach-public-sources/scripts/fetch_public.py stackexchange question --site stackoverflow --id 1
python skills/agent-reach-public-sources/scripts/fetch_public.py mastodon tag --instance mastodon.social --tag python --limit 10
python skills/agent-reach-public-sources/scripts/fetch_public.py mastodon timeline --instance mastodon.social --limit 10
python skills/agent-reach-public-sources/scripts/fetch_public.py reddit subreddit --name python --limit 10
python skills/agent-reach-public-sources/scripts/fetch_public.py reddit search --query "QUERY" --limit 10
```

脚本退出码为 `0` 且 `errors` 为空才算成功；`warnings` 只表示使用了 fallback、平台配额提示或结果被截断，不代表调用失败。脚本不会自动安装依赖；所有新增平台均使用标准库 HTTP/XML 和官方公开接口，不依赖第三方包、登录态、Cookie、代理池或 OAuth。

## 执行规则

1. 只读取公开内容。禁止自动登录、读取浏览器 Cookie、绕过验证码或访问用户未授权的私有数据。
2. URL 只允许 `http://` 或 `https://`。不要把用户输入直接用于 `file://`、内网地址、云元数据地址或本地路径请求。
3. 优先使用对应平台的专用命令。命令不可用或返回空内容时，按本文件的 fallback 顺序处理，不要自行猜测另一个命令。
4. 让命令输出 JSON 或 YAML；读取结果后提取标题、作者、时间、摘要、URL 和正文，保留原始来源 URL。
5. 搜索结果只作为候选来源。需要正文时，再读取用户明确要求的具体 URL；不要批量下载视频或仓库。
6. 输出给用户时说明来源、抓取时间和失败原因。不要把命令中的 token、Cookie 或环境变量回显到结果。

## 环境检查

只在首次使用某平台或遇到失败时检查，不要每轮重复安装依赖：

```bash
agent-reach doctor --json
```

`doctor` 只报告 Agent Reach 和上游工具状态，不代表具体 URL 一定可读。实际调用必须以非空、相关内容为成功标准。

## RSS / Atom

适合读取博客、新闻源和播客订阅。需要 Python 的 `feedparser`：

```bash
python -c "import feedparser, json, sys; d=feedparser.parse(sys.argv[1]); print(json.dumps([{'title':e.get('title',''),'link':e.get('link',''),'published':e.get('published',''),'id':e.get('id',e.get('guid','')),'summary':e.get('summary','')} for e in d.entries[:20]], ensure_ascii=False))" "FEED_URL"
```

规则：

- 只传入 RSS/Atom URL，不要传入站点首页猜测地址；如果用户没有 Feed URL，先用网页搜索找官方 RSS/Atom 链接。
- 用 `id`，没有 `id` 时用 `link` 去重；不要用标题去重。
- RSS 只提供摘要时，只有在用户需要全文时才继续读取 `link`。
- `feedparser` 不可用时，报告依赖缺失并建议安装 `python -m pip install feedparser`，不要静默改用不相关的抓取器。

## 雪球公开数据

当前入口只使用雪球公开 HTTP API，不读取 Cookie、不访问 Chrome、不调用 OpenCLI，也不支持个人自选股、组合或交易记录。支持股票行情、股票搜索、热门帖子和热门股票：

```bash
python skills/agent-reach-public-sources/scripts/fetch_public.py \
  xueqiu quote --symbol "SH600519"
python skills/agent-reach-public-sources/scripts/fetch_public.py \
  xueqiu search --query "贵州茅台" --limit 10
python skills/agent-reach-public-sources/scripts/fetch_public.py \
  xueqiu hot-posts --limit 20
python skills/agent-reach-public-sources/scripts/fetch_public.py \
  xueqiu hot-stocks --limit 10 --type 10
```

行情字段包括价格、涨跌幅、开高低、成交量、成交额、市值、换手率、PE、PB 等，具体字段由雪球响应决定。雪球把接口称为公开接口不代表匿名请求一定放行；当前接口可能返回 `400016`、空响应或触发风控。失败时透传平台错误并报告公开接口不可用，不要改为读取登录态。行情可能延迟，不构成投资建议。

## YouTube

YouTube 使用 `yt-dlp`，不需要登录即可读取公开视频、字幕和公开评论。也不要使用 `-f/--format`，本 Skill 的 YouTube 操作只取元数据、评论或字幕，不下载视频格式；统一带 `--skip-download --ignore-no-formats`。

### 搜索

```bash
yt-dlp --flat-playlist --dump-json --ignore-no-formats "ytsearch5:QUERY"
```

统一入口搜索会自动补充 `--ignore-no-formats`，所以不会因为视频没有可下载格式而报 `Requested format is not available`。

逐行解析 JSON，保留 `id`、`title`、`channel`、`uploader`、`duration` 和 `webpage_url`。若只有 `id`，用 `https://www.youtube.com/watch?v=ID` 补全 URL。

### 视频详情

```bash
yt-dlp --no-playlist --dump-single-json --skip-download --ignore-no-formats "VIDEO_URL"
```

### 字幕

```bash
yt-dlp --no-playlist --write-subs --write-auto-subs --sub-langs "zh-Hans,zh,en" --sub-format vtt --skip-download --ignore-no-formats -o "youtube-%(id)s.%(ext)s" "VIDEO_URL"
```

读取生成的 `.vtt` 文件并去掉时间轴、重复行和空行。字幕命令没有生成非空文件时，报告“该视频没有可用字幕或请求被平台拦截”，不要把退出码当作有字幕。

### 评论

使用统一入口抓取公开视频评论：

```bash
python skills/agent-reach-public-sources/scripts/fetch_public.py \
  youtube comments --url "VIDEO_URL" --limit 20
```

脚本调用 `yt-dlp --skip-download --ignore-no-formats --dump-single-json --write-comments`，返回视频信息和 `items[].items[]` 评论列表。评论不可见、评论区关闭、YouTube 要求验证或请求被拦截时，会返回结构化错误；这不表示视频格式有问题。

等价的底层命令（仅用于排查，不要改成下载命令）：

```bash
yt-dlp --no-playlist --skip-download --ignore-no-formats \
  --dump-single-json --write-comments \
  --extractor-args "youtube:comment_sort=top;max_comments=20" "VIDEO_URL"
```

### YouTube fallback

1. 具体视频字幕或评论失败时，重试同一 `yt-dlp` 命令一次。
2. 字幕失败且环境有 OpenCLI，再尝试 `opencli youtube transcript "VIDEO_URL" -f yaml`。
3. 字幕仍然失败时，只在用户明确需要转录且已配置转写服务时使用 `agent-reach transcribe "VIDEO_URL"`。
4. 评论失败时明确报告 YouTube 评论接口受限或评论不可见。

## Bilibili

Bilibili 基础搜索和视频详情优先使用 `bili-cli`，无需登录：

Bilibili 也提供公开评论 API。优先使用评论 API，而不是依赖页面 DOM：先通过 `x/web-interface/view?bvid=...` 将 BV 号转换为 `aid`，再调用 `x/v2/reply/main` 获取一级评论；使用返回的 `cursor.next` 翻页，`cursor.all_count` 表示公开一级评论总数。对某条评论，用 `x/v2/reply/reply` 读取楼中楼。接口可能受 IP、频率、风控和评论可见性限制；只在 `code=0` 且返回非空 `replies` 时认定成功。一级评论与楼中楼必须分开统计，不使用 Cookie、登录态或绕过风控。

```bash
bili search "QUERY" --type video -n 5
bili video BVxxx
bili hot -n 10
bili rank -n 10
```

从用户 URL 或搜索结果提取 `BV` 号后再读取详情。不要对 Bilibili 使用 `yt-dlp`，该路径容易触发 412 风控。

### Bilibili 评论

先抓一级评论，响应中的 `items[].id` 是评论 `rpid`，`next` 用于下一页：

```bash
python skills/agent-reach-public-sources/scripts/fetch_public.py \
  bilibili comments --bvid "BVxxx" --limit 20 --next 0
```

再按某条一级评论的 `rpid` 抓楼中楼：

```bash
python skills/agent-reach-public-sources/scripts/fetch_public.py \
  bilibili replies --bvid "BVxxx" --rpid 123456 --limit 20 --page 1
```

脚本会先用视频详情接口把 `BV` 号转换成 `aid`，再调用公开评论接口。评论接口可能受风控、频率限制或评论区关闭影响；不需要登录时只读取公开评论，不自动注入 Cookie。

### Bilibili fallback

`bili` 不可用时只做基础搜索 API 兜底：

```bash
curl -sS -A "agent-reach/1.0" "https://api.bilibili.com/x/web-interface/search/all/v2?keyword=QUERY&page=1"
```

如果需要字幕，且本机有用户明确控制的 Chrome/OpenCLI 会话，才使用：

```bash
opencli bilibili subtitle BVxxx
```

没有会话时报告字幕不可用，不要自动登录或读取 Cookie。

## Hacker News

使用官方 Firebase API `hacker-news.firebaseio.com/v0`，匿名只读。榜单先获取 ID，再按 `--limit` 读取 item；`item` 只读取一个 item，保留 score、kids、type、text、dead、deleted 等字段，不递归评论树。

## Stack Exchange / Stack Overflow

使用官方 API v2.3 `api.stackexchange.com`，匿名调用，不使用 key 或登录态。问题搜索使用 `questions` 或 `search/advanced`，支持 `--query`、`--tag`；问题、答案和用户按 limit 返回摘要。响应中的 `quota_remaining` 和 `backoff` 会进入 `warnings`，不得忽略 backoff。

## Mastodon

Mastodon 是实例化的公开 REST API：必须指定实例域名，脚本严格校验公网主机并只访问该实例；不做全网实例搜索。支持状态 URL、账号 lookup、公开标签时间线和公开时间线，返回账号、内容、时间、URL/ID 及 favourites/reblogs/replies。

## Reddit RSS

仅使用官方 Reddit RSS（`reddit.com`、`www.reddit.com`、`old.reddit.com`），支持 subreddit、搜索和单帖 `.rss`。不实现匿名 `.json`，不使用 OAuth；RSS 字段有限，403/429 会以统一结构化错误返回。

## V2EX

V2EX 公开 API 不需要认证：

```bash
# 热门主题
curl -sS -A "agent-reach/1.0" "https://www.v2ex.com/api/topics/hot.json"

# 节点主题，例如 python、tech、jobs、qna
curl -sS -A "agent-reach/1.0" "https://www.v2ex.com/api/topics/show.json?node_name=NODE&page=1"

# 主题详情
curl -sS -A "agent-reach/1.0" "https://www.v2ex.com/api/topics/show.json?id=TOPIC_ID"

# 主题回复
curl -sS -A "agent-reach/1.0" "https://www.v2ex.com/api/replies/show.json?topic_id=TOPIC_ID&page=1"

# 用户信息
curl -sS -A "agent-reach/1.0" "https://www.v2ex.com/api/members/show.json?username=USERNAME"
```

V2EX 公开 API 没有可靠的全文搜索端点。用户要求搜索时，优先使用已有网页搜索能力限定 `site:v2ex.com`，然后读取具体帖子；不要伪造 `/api/search.json`。

## GitHub 公开仓库

优先使用 GitHub 官方 `gh` CLI。公开仓库通常不需要登录，但未认证时有速率限制：

统一入口脚本默认使用未认证的 GitHub 公共 API，因此不会因为本机存在 `GH_TOKEN` 而读取私有仓库；API 限流时会回退到 raw README 或公开仓库页面，并在 `warnings` 中标明。

```bash
gh search repos "QUERY" --sort stars --limit 10
gh search code "QUERY" --language python --limit 10
gh repo view OWNER/REPO --json name,description,url,defaultBranchRef,stargazerCount,licenseInfo
gh api repos/OWNER/REPO/readme -H "Accept: application/vnd.github.raw+json"
gh issue list --repo OWNER/REPO --state open --limit 20 --json number,title,url
gh release list --repo OWNER/REPO --limit 10
```

只读公开内容时，`gh auth login` 不是前置条件。若 `gh` 缺失或因未认证限流，使用公开 REST API 读取单个仓库：

```bash
curl -sS -H "Accept: application/vnd.github+json" "https://api.github.com/repos/OWNER/REPO"
curl -sS -H "Accept: application/vnd.github+json" "https://api.github.com/repos/OWNER/REPO/contents/README.md"
```

不要克隆整个仓库来回答简单的元数据或 README 问题；只有用户明确要求代码分析且工具允许时才读取必要文件。

## LinkedIn 公开页面

公开个人主页、公司主页或职位页面可以用 Jina Reader 读取，通常不需要登录：

```bash
curl -L -sS "https://r.jina.ai/https://www.linkedin.com/in/USERNAME"
curl -L -sS "https://r.jina.ai/https://www.linkedin.com/company/COMPANY"
```

用户给出其他公开 LinkedIn URL 时，把完整 `https://www.linkedin.com/...` URL 放在 `https://r.jina.ai/` 后面。

边界：

- Jina 只读取公开页面；登录墙、验证码或空结果必须如实报告。
- 不要要求 Cookie，不要启动浏览器登录，不要绕过 LinkedIn 的访问限制。
- 人才搜索、职位搜索、完整 Profile 字段和登录后内容不属于本 Skill 的公开能力；需要这些内容时明确说明需要用户授权的专用 API/MCP。

## 统一结果格式

完成一次调用后，按下面结构整理给上层 Agent，再进行摘要或比较：

```json
{
  "source": "rss|youtube|bilibili|xueqiu|v2ex|github|linkedin|hackernews|stackexchange|mastodon|reddit",
  "operation": "search|read|detail|transcript",
  "query": "用户原始查询或 URL",
  "retrieved_at": "ISO-8601 time",
  "items": [
    {
      "title": "...",
      "author": "...",
      "published_at": "...",
      "url": "...",
      "summary": "..."
    }
  ],
  "errors": [],
  "warnings": []
}
```

字段缺失时使用空字符串，不要编造；原始命令输出过长时只保留与用户请求相关的条目和来源链接。
