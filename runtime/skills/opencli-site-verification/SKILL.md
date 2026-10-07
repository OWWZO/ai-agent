---
name: opencli-site-verification
description: 用 Bash/OpenCLI 对“无需登录”网站适配器做逐功能只读验证，区分成功、空结果、登录/配置要求、导航/API/适配器错误和未执行的副作用操作。
---

# OpenCLI 站点功能验证

## 适用场景

用户给出一组声称“无需登录”的站点及功能，要求用 Bash 调用 OpenCLI 实测所有功能。目标是验证 **OpenCLI 命令实际可用性**，不是仅检查网站首页能否打开。

## 核心原则

1. 先运行 `opencli <site> --help -f yaml`，以命令清单为准，不以用户描述的功能数量为准。
2. 逐条覆盖命令；先测无需前置 ID 的搜索/列表，再用返回的 ID、URL、包名或 RFC/CVE 编号测详情/关联命令。
3. 用 `-f yaml` 获取结构化结果；保留 exit code、`code`、`message`、`help` 和关键 stderr。
4. 空数组、`EMPTY_RESULT` 和命令成功但无数据不能直接标记“成功取数”；记录为“命令执行成功但空结果”，并判断测试对象是否应有数据。
5. 报告必须把登录要求、环境配置缺失、浏览器导航失败、API 限流/挑战、适配器解析故障和参数/对象不存在分开。
6. 默认不执行不可逆或有副作用的命令：登录、提交、反馈、发帖、Jira 写入、播放/队列/音量控制等。可执行本地 `--dry-run`，但要明确它只验证输入，不验证远程链路。

## 推荐流程

### 1. 发现命令

```bash
opencli --help
opencli <site> --help -f yaml
```

记录每条命令的 `access`、`browser`、positionals、required options、domain 和 aliases。

### 2. 安全分组

- **只读、无前置参数**：直接测试，如 search/list/top/status（status 若需认证也只读）。
- **只读、依赖对象**：先用搜索结果返回的真实 ID/URL，再测试 detail/read/comments/related。
- **写操作或状态控制**：不执行；若有 `--dry-run`，只做本地校验。
- **需要环境配置**：先判断错误是否在配置检查阶段，不能把它归为站点故障。

### 3. 并行与超时

彼此独立的站点/命令可在同一 Bash 调用中并行或分组执行；单命令使用约 60–90 秒超时。浏览器命令和 API 命令分开，避免一个超时拖垮全部测试。若 Bash 沙箱返回 5xx/代理错误，标记为执行环境故障，不把站点标红；恢复后分批重试。

### 4. 用真实前置结果串联

示例：

```bash
opencli tvmaze search 'The Office' --limit 2 -f yaml
opencli tvmaze show 526 -f yaml
opencli stackoverflow search 'python list comprehension' --limit 2 -f yaml
opencli stackoverflow read 4260280 -f yaml
```

不要用明显不存在的 `TEST-1`、`invalid-token` 结果证明公开功能不可用；这类测试只能验证错误处理。Jira 还需要 `ATLASSIAN_JIRA_BASE_URL`，没有实例地址时应归类为配置阻断。

## 错误分类

| 分类 | 典型信号 | 结论写法 |
| --- | --- | --- |
| 成功取数 | exit 0 且有结构化 rows | 功能通过 |
| 成功但空结果 | `[]`、`EMPTY_RESULT` | 命令执行成功但无结果；若对象明显存在，怀疑适配器/API |
| 明确认证 | `AUTH_REQUIRED`、`Not authenticated` | 该命令实际需要认证，与站点首页公开不同 |
| 配置缺失 | `CONFIG`、`Missing ..._BASE_URL` | 环境未配置，未进入站点查询 |
| 浏览器导航 | `Navigation rejected` | 浏览器/站点导航失败，原因未明；不要自动归因登录 |
| API 限流 | HTTP 429、`rate limited` | 匿名访问被限流；可建议 API key/等待后重试 |
| API 挑战/拦截 | HTTP 403、`ChallengeRequiredError` | API challenge/反爬或访问策略，未必是登录 |
| 参数/对象错误 | `NOT_FOUND`、无效 ID、404 | 测试对象无效或不适配；不能证明整个功能坏了 |
| 沙箱故障 | Bash proxy 502/500、连接失败 | OpenCLI 未执行，结果不计入站点成功/失败 |

## 结果报告

先给总数和结论，再给每站点表：站点、功能数、成功/失败/未执行、关键原因。对失败项提供原始 `code + message`，必要时补 `help`；不要粘贴大段 YAML。最后单列：

- 未执行的副作用命令及原因；
- 需要用户提供的凭据、URL、ID 或环境变量；
- 仍无法确定的原因。

“无需登录”应解释为站点或公开数据层面的描述，不保证每一个 OpenCLI 命令、浏览器页面、API 端点或个人/控制功能都无需认证。
