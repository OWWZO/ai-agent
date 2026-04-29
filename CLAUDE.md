# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Project Overview

AI Agent 智能体工作站 - 一个支持多策略（AutoAgent、FlowAgent、ReAct）的智能 Agent 调度平台，提供对话管理、任务编排、工具调用（MCP）和知识库 RAG 能力。

**Tech Stack:**
- Backend: Java 17 + Spring Boot 3.4.3 + Spring AI 1.1.4 + MyBatis-Plus 3.5.14
- Frontend: React 19 + TypeScript 5.7 + Vite 6 + Ant Design 5.26 + Tailwind CSS 4.1
- Python Tools: FastAPI + Pydantic v2 (`reactor-tool/`, `reactor-client/`)
- Databases: MySQL 8 (主库) + PostgreSQL 15/pgvector (向量库)
- Build: Maven 3.8+

---

## Common Commands

### Backend (Java)

```bash
# Start the application
mvn -pl ai-agent-station-study-app spring-boot:run

# Run all tests (note: skipTests=true by default in root pom)
mvn test -pl ai-agent-station-study-app -DskipTests=false

# Run a single test class
mvn test -pl ai-agent-station-study-app -Dtest=ClassName -DskipTests=false

# Compile the full project
mvn clean compile

# Package
mvn clean package -DskipTests
```

### Frontend (React)

```bash
cd ui
pnpm install
pnpm dev          # Dev server at http://localhost:3000
pnpm build        # Production build
pnpm lint         # ESLint check
pnpm fix          # Auto-fix lint issues
```

### Python Tools

```bash
# reactor-tool (FastAPI server with MCP tools)
cd reactor-tool
uv sync
.\start.ps1       # Windows (recommended - isolates VIRTUAL_ENV)
./start.sh        # Linux/Mac

# reactor-client (Python MCP client)
cd reactor-client
uv run python server.py
```

### MCP Server (CSDN)

```bash
cd mcp-server-csdn/mcp-server-csdn
mvn spring-boot:run
```

---

## High-Level Architecture

### DDD Layered Modules

The project follows DDD layered architecture (inspired by 小傅哥扳手工程 `xfg-wrench-bom`):

```
                     ┌─────────────────┐
                     │  trigger        │  HTTP Controllers, Jobs, VO
                     │  (入口适配层)    │
                     └────────┬────────┘
                              │ depends on
                     ┌────────▼────────┐
                     │  api            │  DTO contracts, service interfaces
                     │  (接口契约层)    │
                     └────────┬────────┘
                              │ depends on
                     ┌────────▼────────┐
                     │  domain         │  Core business logic, entities,
                     │  (领域层)        │  value objects, domain services,
                     │                 │  reactor (Agent execution engine)
                     └────────┬────────┘
                              │ depends on
                     ┌────────▼────────┐
                     │  infrastructure │  DAO, repository impl, gateways
                     │  (基础设施层)    │  MyBatis-Plus, external APIs
                     └────────┬────────┘
                              │ depends on
                     ┌────────▼────────┐
                     │  types          │  Constants, enums, exceptions,
                     │  (基础类型层)    │  task scheduling interfaces
                     └─────────────────┘
                              ▲
                              │ (all modules depend on types)
```

**Dependency direction:** `app` -> `trigger` -> `domain` -> `infrastructure`; `api` and `types` are shared across layers. `app` is the assembly module that pulls everything together and contains the Spring Boot main class.

### Module Startup Class

- `org.wwz.ai.Application` in `ai-agent-station-study-app`

### Agent Execution Engine (Reactor)

The core execution logic lives in `domain/agent/reactor/` and supports three strategies:

1. **AutoAgent** (`auto/`): Planner -> Executor -> Summary multi-stage pipeline
2. **FlowAgent** (`flow/`): YAML-configured execution flow
3. **ReAct** (`react/`): Reasoning-Action loop with tool calling

Strategy selection is handled by factory classes:
- `DefaultAutoAgentExecuteStrategyFactory`
- `DefaultFlowAgentExecuteStrategyFactory`

### Persistence Architecture

**Current migration (012-transcript-block-refactor):** The project is transitioning from the old message/event tables to a new flat transcript block model. Running code paths must use the new tables:

- `ai_agent_turn` - conversation turns
- `ai_agent_transcript_block` - flat message blocks
- `ai_agent_display_event` - display events
- `ai_agent_session_memory` - session memory snapshots (multi-version)

Old tables (`ai_agent_message`, `ai_agent_message_event`) are being deprecated and must not be reconnected to main paths.

### Frontend Architecture

The React frontend (`ui/`) communicates with the backend via SSE (Server-Sent Events) for streaming responses:

- `ChatView` manages SSE connection and message state
- `Dialogue` renders conversation messages
- `ActionView` shows tool execution details (files, browsing, task status)
- `ActionPanel` selects renderers based on message type (Markdown, HTML, Table, Search, File)

---

## Code Conventions

- **Package naming:** `org.wwz.ai.{module}.{feature}`
- **DDD boundaries:**
  - `domain` does NOT directly depend on Controllers, Mapper XML, or HTTP details
  - `infrastructure` handles DAO, gateways, and MCP external integrations
  - `trigger` should not accumulate business logic; delegate to `domain`
- **Database:** MyBatis-Plus with Mapper XML in `resources/mybatis/mapper/`
- **Configuration:** Environment-specific configs in `application-{profile}.yml`
- **Naming:** English semantics; complex logic and boundary conditions use Chinese comments
- **Agent/Tool extensions:** Prefer configurable capabilities via model config, tool registration, strategy factories, and metadata assembly over hardcoding

---

## Working Agreements (from AGENTS.md)

- Before implementing, identify affected modules first; default to minimal closed-loop changes, avoid spreading across modules unnecessarily.
- When working on the Java main path, read root `CLAUDE.md` and the relevant module `CLAUDE.md` first.
- For new interfaces, Agent types, MCP tools, or RAG capabilities: add specs and acceptance criteria before implementation.
- **Database changes require synchronized updates to:** PO classes, DAO interfaces, Mapper XML, `schema.sql`, test data, and related admin interfaces.
- **Streaming, message events, and task orchestration** must include persistence, event logging, error handling, and observability — no "main path only" half-implementations.
- Do not modify executable files, cookie files, or runtime artifacts in `reactor-tool/` without explicit need.
- The repo may have uncommitted changes; verify file state before modifying to avoid overwriting.

---

## Test Strategy

Tests are in `ai-agent-station-study-app/src/test/java/org/wwz/ai/test/`.

The `maven-surefire-plugin` in `ai-agent-station-study-app/pom.xml` excludes tests that depend on external models, MCP, or standalone services. These are skipped by default:
- `*Test.java` in `spring/ai/` (external AI model tests)
- `AgentTest.java`, `AutoAgentTest.java`, `FlowAgentExecuteTest.java` (domain integration tests)
- `ElkBlacklistDataTest.java`, `DynamicAutoAgentTest.java`, etc.

To run excluded tests individually, use `-Dtest=ClassName` with `-DskipTests=false`.

---

## Key Configuration Files

| File | Purpose |
|------|---------|
| `ai-agent-station-study-app/src/main/resources/application-dev.yml` | Dev env config (DB, AI models, Agent configs) |
| `ai-agent-station-study-app/src/main/resources/db/schema.sql` | Database schema |
| `ui/.env` | Frontend API base URL |
| `reactor-tool/.env` | Python tool environment variables |
| `pom.xml` | Root Maven config (note: `skipTests=true` by default) |

---

## Current Active Work

- **Branch:** `012-transcript-block-refactor`
- **Focus:** TranscriptBlock flat persistence refactoring — migrating from nested message/event tables to flat block-based storage with turn/block/display_event/session_memory tables.
- **Specs:** Located in `specs/012-transcript-block-refactor/`
