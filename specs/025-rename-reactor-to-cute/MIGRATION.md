# MIGRATION：Reactor → Cute 割接清单

> 本改动为**硬切**，旧名不留兼容层。部署方必须在同一次发布窗口内完成以下全部动作。

---

## ⚠️ 割接影响（先读）

由于不做兼容，以下动作**必须同时完成**，否则服务会不可用：

| 影响面 | 现象 | 后果 |
|---|---|---|
| JWT | `issuer` 由 `reactor` 改为 `cute` | **所有已签发 token 立即失效**，用户需重新登录 |
| Docker 卷 | `reactor-data` → `cute-data` | 新卷为空，**旧卷数据不会自动迁移** |
| 浏览器扩展 | `source` 协议字段 `"reactor"` → `"cute"` | **旧扩展无法配对**，必须同步升级 |
| 环境变量 | `REACTOR_*` → `CUTE_*`（31 个） | 未改的环境变量会退回默认值 |
| MySQL 用户 | 默认 `reactor` → `cute` | 沿用旧用户需显式指定 `MYSQL_USER` |

---

## 1. 环境变量（31 个）

逐一重命名，值保持不变：

```
REACTOR_AUTH_JWT_SECRET            → CUTE_AUTH_JWT_SECRET
REACTOR_AUTH_REFRESH_COOKIE_SECURE → CUTE_AUTH_REFRESH_COOKIE_SECURE
REACTOR_CLI_TEST                   → CUTE_CLI_TEST
REACTOR_DIFF_TRUNCATED__           → CUTE_DIFF_TRUNCATED__
REACTOR_DOCGEN_HOME                → CUTE_DOCGEN_HOME
REACTOR_DOCGEN_KNOWLEDGE_DIR       → CUTE_DOCGEN_KNOWLEDGE_DIR
REACTOR_DOCGEN_OUTPUT_DIR          → CUTE_DOCGEN_OUTPUT_DIR
REACTOR_DOCGEN_UPLOAD_DIR          → CUTE_DOCGEN_UPLOAD_DIR
REACTOR_FILE_ARTIFACT_LOCAL_FILE_MODE → CUTE_FILE_ARTIFACT_LOCAL_FILE_MODE
REACTOR_LEGACY                     → CUTE_LEGACY
REACTOR_LLM_EMPTY_COMPLETION_RETRY_ENABLED → CUTE_LLM_EMPTY_COMPLETION_RETRY_ENABLED
REACTOR_LLM_RETRY_BACKOFF_FACTOR   → CUTE_LLM_RETRY_BACKOFF_FACTOR
REACTOR_LLM_RETRY_BASE_DELAY_MS    → CUTE_LLM_RETRY_BASE_DELAY_MS
REACTOR_LLM_RETRY_JITTER           → CUTE_LLM_RETRY_JITTER
REACTOR_LLM_RETRY_MAX_DELAY_MS     → CUTE_LLM_RETRY_MAX_DELAY_MS
REACTOR_LLM_RETRY_MAX_RETRIES      → CUTE_LLM_RETRY_MAX_RETRIES
REACTOR_LOG_PATH                   → CUTE_LOG_PATH
REACTOR_OPENCLI_COMMAND            → CUTE_OPENCLI_COMMAND
REACTOR_OPENCLI_MAIN               → CUTE_OPENCLI_MAIN
REACTOR_SANDBOX_URL                → CUTE_SANDBOX_URL
REACTOR_SKILL_OUTPUT_ROOT          → CUTE_SKILL_OUTPUT_ROOT
REACTOR_TOOL                       → CUTE_TOOL
REACTOR_TOOL_API_PORT              → CUTE_TOOL_API_PORT
REACTOR_TOOL_BASE_URL              → CUTE_TOOL_BASE_URL
REACTOR_TOOL_HOST                  → CUTE_TOOL_HOST
REACTOR_TOOL_PORT                  → CUTE_TOOL_PORT
REACTOR_TOOL_ROLE                  → CUTE_TOOL_ROLE
REACTOR_TOOL_ROOT                  → CUTE_TOOL_ROOT
REACTOR_TOOL_SANDBOX_PORT          → CUTE_TOOL_SANDBOX_PORT
REACTOR_TOOL_WORKERS               → CUTE_TOOL_WORKERS
```

来源文件：`cute-tool/.env`、`application-prod.yml`、`Dockerfile`、`docker-compose.yml`

---

## 2. Docker Compose

### 2.1 卷迁移（数据保全）

`cute-data` 是新卷，**不会**继承 `reactor-data` 的内容。若需保留会话工作区与文件产物：

```bash
# 1. 停掉旧栈
docker compose down

# 2. 把旧卷内容复制到新卷（用一个临时容器搬运）
docker volume create cute-data
docker run --rm \
  -v reactor-data:/from \
  -v cute-data:/to \
  alpine sh -c "cd /from && cp -av . /to/"

# 3. 确认后再删旧卷
docker volume rm reactor-data
```

> 卷内存放 Python 文件服务的 `autobots.db`、MRAG SQLite 元数据、会话 `skilloutput`。
> 若不迁移，历史会话工作区与已上传文件将不可访问。

### 2.2 服务名变更

| 旧 | 新 |
|---|---|
| `reactor-backend` | `cute-backend` |
| `reactor-tool` | `cute-tool` |
| `reactor-sandbox` | `cute-sandbox` |
| `reactor-data` | `cute-data` |

内网地址同步变更：`http://reactor-tool:1601` → `http://cute-tool:1601`（见 `docker/nginx.conf` 的 upstream）。

---

## 3. JWT（用户侧可见）

`auth.jwt.issuer` 由 `reactor` 改为 `cute`。

- **所有在线用户会被强制登出**
- Refresh token 需要重新签发
- Cookie 名 `reactor_refresh_token` → `cute_refresh_token`，旧 cookie 不再被识别

如希望降低影响，可在业务低峰期发布。

---

## 4. MySQL

`docker-compose.yml` 中 `MYSQL_USER` 默认值由 `reactor` 改为 `cute`。

**数据库名 `ai-agent-station` 未变更**，表结构未变更。

如果你的部署显式配置了 `MYSQL_USER=reactor`（旧值），需确认该用户在 MySQL 中存在且权限正确；若依赖默认值，则新用户 `cute` 需要有对应库权限——**首次部署需检查 `db/schema.sql` 或迁移脚本中的授权语句是否覆盖新用户**。

---

## 5. 浏览器扩展（必须同步升级）

协议字段变更，旧扩展与新前端**无法握手**：

| 位置 | 旧 | 新 |
|---|---|---|
| `content-pair.js` / `bsk-pair.content.ts` | `PAIR_SOURCE = 'reactor'` | `'cute'` |
| | `EXTENSION_SOURCE = 'reactor-extension'` / `'reactor-bsk-extension'` | `'cute-extension'` / `'cute-bsk-extension'` |
| `popup.js` | `reactor_api_origin` | `cute_api_origin` |
| `background.ts` | `reactor_pairing_token` / `reactor_relay_url` | `cute_pairing_token` / `cute_relay_url` |

**扩展必须与后端同一发布窗口升级**，否则用户点击配对按钮会无响应。

---

## 6. 前端（用户侧可见）

以下浏览器本地存储键变更，用户侧旧数据不会迁移，属可接受损失：

| 旧键 | 新键 |
|---|---|
| `reactor:selected-model:{sessionId}` | `cute:selected-model:{sessionId}` |
| `reactor:code-preview-wrap` | `cute:code-preview-wrap` |
| `reactor.activeRun` | `cute.activeRun` |
| `reactor.sessionCursor.*` | `cute.sessionCursor.*` |
| `reactor.sessionId` | `cute.sessionId` |

影响：用户上次选择的模型、代码预览换行偏好会回到默认值。

---

## 7. 运行时目录与文件

| 旧 | 新 | 说明 |
|---|---|---|
| `.reactor/plan.md` | `.cute/plan.md` | Plan mode 计划文件 |
| `.reactor/read-state.json` | `.cute/read-state.json` | 工作区读状态 |
| `.reactor-artifacts/` | `.cute-artifacts/` | CLI 产物捕获根目录 |
| `reactor-tool/skilloutput/` | `cute-tool/skilloutput/` | 会话工作区 |

> 若磁盘上存在旧的 `.reactor/` 目录，需要按 §2.1 的方式一并迁移或接受丢失。

---

## 8. Python 工具

- 目录 `reactor-tool/` → `cute-tool/`，包名 `reactor_tool` → `cute_tool`
- `pyproject.toml` 描述改为 `Cute Tools`
- E2B 模板别名 `reactor-code-playwright-desktop` → `cute-code-playwright-desktop`（E2B 侧需重建或沿用旧名并显式指定）
- 集合名：`reactor_model_schema` → `cute_model_schema`、`t_reactor_mrag_*` → `t_cute_mrag_*`
  → **向量库存量集合需重命名或重新灌库**

---

## 9. 部署检查清单

发布前逐项确认：

- [ ] 全部 31 个 `CUTE_*` 环境变量已注入
- [ ] `cute-data` 卷已创建，旧数据已迁移（或确认可丢弃）
- [ ] MySQL 用户 `cute` 已授权 `ai-agent-station`
- [ ] 浏览器扩展新版本已发布到商店/内部分发
- [ ] 已通知用户会强制登出
- [ ] `docker compose config` 无报错
- [ ] README/文档中的新名称已同步对外说明

---

## 10. 回滚

回滚需整体回退到改名前的 commit（`pre-rename-reactor` 标签），并反向执行本文档全部动作。

由于 JWT/卷/扩展三处均不兼容，**不支持新旧版本并存灰度**。
