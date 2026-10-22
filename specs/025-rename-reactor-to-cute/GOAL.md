# GOAL: 项目改名 Reactor → Cute（全量硬切）

> 状态：待执行
> 决策：覆盖 L0–L3 全部层级；**不做旧名兼容**（硬切）
> 分支：`025-rename-reactor-to-cute`（从 `master` 切出）

---

## 1. 目标与成功标准

把本仓库的项目标识从 `Reactor` 全量改为 `Cute`：目录名、Maven 坐标、Java 包名、配置前缀、环境变量、容器契约、前端包名、文档品牌。

**成功标准（全部满足才算完成）：**

1. `mvn clean compile` 通过
2. `mvn -pl Cute-agent-app test -Dtest='*BoundaryTest' -DskipTests=false` 全绿
3. `cd ui && pnpm install && pnpm build` 通过
4. `docker compose config` 无报错
5. 仓库内 `Reactor` / `reactor` 词边界命中清零，**含归档文档与历史文件名**（L4 库引用除外，见 §3）
6. 无 `Cute` 误伤：`execute` 等词未被破坏

---

## 2. 命名映射表（唯一权威）

| 层 | 旧 | 新 |
|---|---|---|
| L0 目录/坐标 | `Reactor-agent` | `Cute-agent` |
| L0 目录 | `reactor-tool/` | `cute-tool/` |
| L0 目录 | `reactor-tool/reactor_tool/` | `cute-tool/cute_tool/` |
| L0 前端 | `"name": "reactor-ui"` | `"name": "cute-ui"` |
| L1 Java 包 | `org.wwz.ai.*.reactor.*` | `org.wwz.ai.*.cute.*` |
| L1 类名 | `ReactorConfig` | `CuteConfig` |
| L1 配置前缀 | `reactor:` / `${reactor.*}` | `cute:` / `${cute.*}` |
| L1 mapper namespace | `org.wwz.ai.infrastructure.dao.reactor.*` | `org.wwz.ai.infrastructure.dao.cute.*` |
| L2 环境变量 | `REACTOR_*`（31 个，见 §6） | `CUTE_*` |
| L2 容器 | service/volume `reactor-*`、`reactor-data` | `cute-*`、`cute-data` |
| L2 JWT | `issuer: reactor` | `issuer: cute` |
| L2 DB 用户 | `MYSQL_USER: reactor` | `MYSQL_USER: cute` |
| L2 扩展协议 | `source: "reactor"` / `"reactor-bsk-extension"` | `"cute"` / `"cute-bsk-extension"` |
| L3 品牌 | README / UI 标题 / banner 中的 Reactor | Cute |

> `groupId=cn.bugstack.ai` 不含 reactor，**保持不动**。数据库名 `ai-agent-station` **保持不动**。

---

## 3. 绝对禁区（替换前必读）

### 3.1 Project Reactor 库 —— 禁止替换

以下 6 处是与项目名无关的第三方库，改一个字都会让 Spring AI 流式链路编译失败：

```
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.Disposable;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
```

已知文件：`SpringAiLlmCompletionAdapter.java`、`McpStreamableHttpSupportTest.java`、`OpenAiTest.java`、`StreamResponseHandlerTest.java`、`DddArchitectureGuardTest.java`。

**规则：任何替换命令必须排除 `import reactor.` / `reactor.core` / `reactor.netty` 开头的行。**

### 3.2 构建产物与临时目录 —— 不参与替换

- `**/target/**`、`**/dist/**`、`**/node_modules/**`、`**/__pycache__/**`
- `docs/dev-ops/ubuntu/server-bundle/payload/**`（含 `.jar` 与二进制）
- `.claude/worktrees/**`（未跟踪的 worktree 副本）
- `uv.lock`、`pnpm-lock.yaml`、`package-lock.json`（**不得手改**，由包管理器重新生成）

### 3.3 大小写陷阱

`cute` 是 `exe**cute**` 的子串。**禁止用 `grep -i cute` 做验证**——会命中 1106 个 `execute` 假阳性。验证必须用词边界：

```bash
grep -rnE '\bCute\b|cute[-_.]|\bCUTE_' <path>
```

---

## 4. 执行顺序（必须按序，每阶段独立 commit）

### Phase 0 — 回滚锚点

- [ ] `git tag pre-rename-reactor` （或记录当前 commit SHA）
- [ ] 切分支 `025-rename-reactor-to-cute`
- [ ] 确认工作区干净

### Phase 1 — L1 代码标识（Java 包 / 类名 / 配置前缀）

- [ ] `org.wwz.ai.*.reactor.*` 包目录改名（4 处：`domain.agent.reactor`、`infrastructure.dao.reactor`、`trigger.http.reactor`、`config.reactor`）
- [ ] 同步所有 `package` / `import` 语句
- [ ] 同步 mapper XML 的 `namespace`（34 个 xml 文件）
- [ ] `ReactorConfig` → `CuteConfig`，同步所有引用与 `@Value("${reactor.*}")`
- [ ] yml 中 `reactor:` 前缀 → `cute:`（`application.yml`、`application-prod.yml`）
- [ ] **排除** `import reactor.` 行

**验证：** `mvn clean compile`

### Phase 2 — L0 工程标识（目录 / Maven 坐标 / 前端包）

- [ ] 7 个模块目录 `Reactor-agent-*` → `Cute-agent-*`
- [ ] 根 `pom.xml` artifactId + 7 个子 pom 的 `<parent>` / `<artifactId>` / `<finalName>`
- [ ] `reactor-tool/` → `cute-tool/`，`reactor_tool/` → `cute_tool/`，同步 `pyproject.toml` / `start.sh` / `start.ps1` / Dockerfile
- [ ] `ui/package.json` name → `cute-ui`，重新 `pnpm install` 生成 lock
- [ ] **`BoundaryTestPaths.java:8` 的 `"Reactor-agent-app"` 等硬编码路径常量**（否则全部边界测试失败）
- [ ] `DddArchitectureGuardTest` / `BrowserRelayBoundaryTest` / `AuthApplicationBoundaryTest` / `DataQueryLegacyBoundaryTest` 中的路径常量
- [ ] `docs/dev-ops/**` 下的 systemd / nginx / tmpl 文件名与内容

**验证：**
```bash
mvn clean compile
mvn -pl Cute-agent-app test -Dtest='*BoundaryTest' -DskipTests=false
cd ui && pnpm install && pnpm build
```

### Phase 3 — L2 部署契约（硬切，需割接）

- [ ] 31 个 `REACTOR_*` 环境变量 → `CUTE_*`（Java `@Value`、yml、`.env_template`、shell 脚本、Python）
- [ ] `docker-compose.yml`：service 名、volume `reactor-data` → `cute-data`、镜像 target
- [ ] `JWT issuer: reactor` → `cute`
- [ ] `MYSQL_USER` 默认值 `reactor` → `cute`
- [ ] `BrowserSkill/.../bsk-pair.content.ts` 的 `PAIR_SOURCE` / `EXTENSION_SOURCE`
- [ ] **同时**修改 `ui/src/components/GeneralInput/BrowserRelayChip.tsx` 的 `source: "reactor"`（跨端协议，必须同 commit）
- [ ] `docker-compose.yml` 内网地址 `http://reactor-tool:1601` → `http://cute-tool:1601`

**验证：** `docker compose config`；`git grep -n 'REACTOR_'` 清零

> ⚠️ 本阶段完成后旧部署不可用：JWT 全部失效、`cute-data` 是新卷、浏览器扩展必须同步升级。

> ⚠️ 归档文档不豁免：`openspec/changes/archive/` 下的历史目录名与文件名也要改。目录改名用 `git mv` 保留历史；`openspec/changes/archive/` 内的 change 名若被 `.openspec` 索引引用，需同步索引。

### Phase 4 — L3 文档与品牌

- [ ] README、`CLAUDE.md`（根 + 各模块）、`docs/**` 中作为**项目名**出现的 Reactor
- [ ] UI 标题 / `index.html` / banner / 产品名
- [ ] **归档文档同样改名**：`docs/superpowers/plans/`、`docs/superpowers/specs/`、`openspec/changes/**`（含 `archive/`）、`specs/0xx-*/`
- [ ] 归档目录与文件名中的 `reactor` 也改（如 `2026-05-04-reactor-ddd-phase1-boundary-convergence.md` → `...-cute-ddd-phase1-...`）
- [ ] 归档目录名改（如 `openspec/changes/reactor-ddd-phase1-boundary-convergence/` → `cute-ddd-phase1-boundary-convergence/`）

**验证：** 词边界 grep 全仓清零，无归档例外

### Phase 5 — 全量验证

- [ ] 四条成功标准全跑一遍
- [ ] `git grep -nE '\breactor\b' -i` 逐条人工确认剩余命中均为 L4 库引用或归档文档

---

## 5. 交付物

1. 每个 Phase 一个 commit，message 格式 `refactor(rename): Phase N - <范围>`
2. 一份 `MIGRATION.md`：列出部署方必须执行的割接动作（env 改名、卷迁移、扩展升级、token 重签）
3. 最终验收报告：四条成功标准的实际输出

---

## 6. 环境变量清单（31 个）

```
REACTOR_AUTH_JWT_SECRET            REACTOR_LLM_RETRY_MAX_RETRIES
REACTOR_AUTH_REFRESH_COOKIE_SECURE REACTOR_LOG_PATH
REACTOR_CLI_TEST                   REACTOR_OPENCLI_COMMAND
REACTOR_DIFF_TRUNCATED__           REACTOR_OPENCLI_MAIN
REACTOR_DOCGEN_HOME                REACTOR_SANDBOX_URL
REACTOR_DOCGEN_KNOWLEDGE_DIR       REACTOR_SKILL_OUTPUT_ROOT
REACTOR_DOCGEN_OUTPUT_DIR          REACTOR_TOOL
REACTOR_DOCGEN_UPLOAD_DIR          REACTOR_TOOL_API_PORT
REACTOR_FILE_ARTIFACT_LOCAL_FILE_MODE REACTOR_TOOL_BASE_URL
REACTOR_LEGACY                     REACTOR_TOOL_HOST
REACTOR_LLM_EMPTY_COMPLETION_RETRY_ENABLED REACTOR_TOOL_PORT
REACTOR_LLM_RETRY_BACKOFF_FACTOR   REACTOR_TOOL_ROLE
REACTOR_LLM_RETRY_BASE_DELAY_MS    REACTOR_TOOL_ROOT
REACTOR_LLM_RETRY_JITTER           REACTOR_TOOL_SANDBOX_PORT
REACTOR_LLM_RETRY_MAX_DELAY_MS     REACTOR_TOOL_WORKERS
```

> `REACTOR_LEGACY` / `REACTOR_CLI_TEST` 等如属测试专用开关，一并改名，不做例外。

---

## 7. Agent 执行须知

- **禁止一次性全局 sed**。按 Phase 分文件类型处理，每步 `git diff --stat` 自查。
- **每次替换后立即编译**，不要攒到最后。
- 遇到本文档未覆盖的 `reactor` 命中，**停下来报告**，不要自行判断。
- 目录改名用 `git mv`，保留历史。
- 不要修改 `reactor-tool/` 下的运行时产物（`cookies.json`、`autobots.db*`、`logs/`、`skilloutput/`）。
