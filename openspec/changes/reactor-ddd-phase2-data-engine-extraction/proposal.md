## Why

The Reactor domain still owns JDBC connection pools, dialect and catalog loaders, provider executors, SQL parser helpers, and shared technical utilities such as `JdbcUtils`, `ESUtil`, and `HttpUtils`. That mixes business query semantics with a technical data engine, making domain services depend on low-level execution mechanics rather than stable ports and semantic models.

## What Changes

- Separate domain query semantics from technical data-engine execution, preserving only business query models and port contracts in `domain`.
- Move `reactor/data/jdbc/**`, `reactor/data/provider/jdbc/**`, and technical `reactor/data/sql/**` implementation concerns out of `domain` into infrastructure-owned data-engine packages.
- Retain semantic query models such as `SqlModel` and `WhereCondition` in `domain` only where they represent business query meaning, and move parser, dialect, catalog, and execution mechanics behind ports.
- Relocate or replace `JdbcUtils`, `ESUtil`, and `HttpUtils` so `domain` no longer owns shared JDBC / SQL / catalog / low-level HTTP helpers.
- Refactor data-engine-facing domain services to use explicit query-execution, catalog, and indexing ports rather than connection factories, provider executors, or technical utility classes.

## Capabilities

### New Capabilities
- `reactor-ddd-data-engine-boundary`: Define how Reactor query semantics remain in `domain` while JDBC, SQL parser, catalog, and related technical kernels move behind infrastructure-owned adapters.

### Modified Capabilities
None.

## Impact

- Affects `ai-agent-station-study-domain` and `ai-agent-station-study-infrastructure`, especially `reactor/data/jdbc/**`, `reactor/data/sql/**`, `reactor/data/provider/jdbc/**`, `JdbcUtils`, `ESUtil`, `HttpUtils`, and data-engine-facing services such as `DataAgentService`, `ChatModelInfoService`, and `ColumnValueSyncService`.
- Changes the ownership of query execution, catalog loading, dialect resolution, and index-side-effect helpers, but does not change database schema or public API endpoints.
- Requires focused regression coverage across supported data-source paths because package relocation and port extraction can affect vendor-specific SQL and metadata behavior.
