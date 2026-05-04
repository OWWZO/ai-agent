## Context

The Reactor domain currently contains a large technical data-engine kernel:

- `reactor/data/jdbc/**` owns connection config, connection pools, dialect loaders, catalog loaders, and vendor-specific JDBC factories.
- `reactor/data/provider/jdbc/**` owns JDBC query and metadata execution concerns.
- `reactor/data/sql/**` owns SQL parsing, dialect helpers, and parser utilities.
- `JdbcUtils`, `ESUtil`, and `HttpUtils` still exist as shared technical helpers in the domain module.
- Services such as `DataAgentService`, `ChatModelInfoService`, and `ColumnValueSyncService` directly collaborate with these technical types.

This structure means the domain knows too much about connection pools, dialects, metadata retrieval, SQL parsing mechanics, and index-side-effect infrastructure. The intended DDD boundary is that the domain keeps business query semantics and capability contracts, while the actual query execution engine lives in infrastructure.

This change covers the "technical data engine" extraction portion of the broader Phase 2 plan. It complements the remote-port extraction change, which handles generic outbound transport ownership, and the persistence extraction change, which handles repository seams.

## Goals / Non-Goals

**Goals:**

- Keep only business query semantics and explicit port contracts in `domain`.
- Move JDBC connection pools, connection factories, catalog loaders, dialect resolution, provider executors, and technical SQL parser implementations into `infrastructure`.
- Relocate or replace `JdbcUtils`, `ESUtil`, and `HttpUtils` so technical helper ownership no longer lives in `domain`.
- Refactor data-engine-facing domain services to depend on domain ports rather than technical kernel classes.
- Add structural and regression verification that the technical data engine has left the domain module.

**Non-Goals:**

- Do not move persistence repositories or execution-ledger DAO ownership; that belongs to the persistence extraction change.
- Do not redesign Spring runtime assembly; that belongs to the Spring runtime decoupling change.
- Do not keep raw technical execution classes in `domain` just because they are currently shared; if they are technical, they belong in `infrastructure`.
- Do not remove semantic query models that genuinely express business meaning, such as `SqlModel` or `WhereCondition`, unless a clearer domain model replaces them.

## Decisions

### 1. Separate semantic query models from technical execution kernel types

**Decision**

The change SHALL distinguish between:

- semantic domain models that express query meaning, such as `SqlModel`, `WhereCondition`, and related business query structures;
- technical execution kernel types such as connection factories, pooled data sources, dialect factories, catalog loaders, parser implementations, and JDBC provider executors.

Only the semantic models and port contracts may remain in `domain`.

**Rationale**

- This preserves useful business language without keeping low-level execution mechanics in the same module.
- It provides a stable foundation for services that reason about queries without caring how the query executes.

**Alternatives considered**

- Move everything including semantic models to infrastructure: too much domain meaning would be lost.
- Keep parser and dialect code in domain because it is "query related": this still leaves technical engine code in the wrong layer.

### 2. Infrastructure owns JDBC connection, catalog, and dialect mechanics

**Decision**

`JdbcConnectionFactory`, `JdbcConnectionPoolFactory`, `JdbcCatalogLoader`, dialect factories, catalog factories, provider executors, and related vendor-specific technical classes SHALL move into infrastructure-owned data-engine packages.

**Rationale**

- These classes are pure execution mechanics and integration details.
- They also benefit from infrastructure-local tests for vendor behavior and connection configuration.

**Alternatives considered**

- Leave them in domain and hide them with comments: this has already proven ineffective.
- Move them into `app`: they are runtime implementations, not application assembly.

### 3. SQL parsing implementation moves behind explicit ports, while semantic output stays in domain

**Decision**

Technical SQL parsing and dialect formatting logic SHALL move behind infrastructure-owned parser or query-analysis adapters. If domain services need parsed semantic output, they SHALL receive domain semantic models through an explicit port rather than calling parser utilities directly.

**Rationale**

- SQL parser configuration and dialect-specific formatting are technical concerns.
- Keeping the output as domain models still allows business services to reason about query shape.

**Alternatives considered**

- Leave `SqlParserUtils` in domain because it returns domain models: the implementation still depends on technical parser and dialect libraries.

### 4. Shared technical helpers are either deleted or relocated behind infrastructure ports

**Decision**

`JdbcUtils`, `ESUtil`, and `HttpUtils` SHALL no longer be domain-owned helper classes. Each helper SHALL either:

- move into infrastructure as an internal implementation detail; or
- disappear because its remaining consumers now call explicit domain ports.

**Rationale**

- Shared technical helpers are one of the easiest ways for infrastructure concerns to leak back into domain code.
- Removing domain ownership also clarifies which change owns the remaining cleanup when helpers overlap with remote integrations.

**Alternatives considered**

- Keep helper classes in domain as "utility only": this still violates the intended boundary and encourages future backsliding.

### 5. Domain services consume query, catalog, and index-side-effect ports

**Decision**

Data-engine-facing domain services SHALL depend on explicit ports for query execution, metadata retrieval, catalog lookup, and index-side effects instead of directly using JDBC providers, connection configs, or technical helper classes.

**Rationale**

- This expresses the domain capability clearly and isolates vendor-specific behavior in infrastructure.
- It also enables fake adapters for deterministic tests.

**Alternatives considered**

- Move those services to infrastructure: that would also move business use cases and orchestration out of the domain.

### 6. Verification must combine structural boundary checks with vendor-focused regressions

**Decision**

Phase 2D verification SHALL include:

- structural scans ensuring `domain` no longer owns connection pools, data sources, catalog loaders, or dialect factories;
- focused regressions for query metadata retrieval, SQL semantic handling, and vendor-specific execution paths;
- contract coverage for the new infrastructure data-engine adapters.

**Rationale**

- The change is not complete if the technical kernel still exists under a different package inside `domain`.
- Vendor-specific regressions are required because the moved code includes dialect and metadata behavior.

**Alternatives considered**

- Compile-only verification: too weak for vendor behavior and port coverage.

## Risks / Trade-offs

- [Semantic versus technical ownership gets blurred during package moves] -> Classify types up front and document the keep-versus-move decision for each package family.
- [Vendor-specific SQL or metadata behavior regresses after relocation] -> Add focused regressions for MySQL, ClickHouse, H2, or any currently supported in-scope dialect paths.
- [Shared helpers reappear because multiple services still need them] -> Replace helper reuse with infrastructure-local adapters and explicit domain ports.
- [This change overlaps with remote transport cleanup] -> Let remote callers migrate first where necessary, then remove or relocate the shared helper ownership here.

## Migration Plan

1. Inventory all classes under `reactor/data/jdbc/**`, `reactor/data/provider/jdbc/**`, `reactor/data/sql/**`, and the `JdbcUtils` / `ESUtil` / `HttpUtils` helper set.
2. Classify each type as domain semantic model, domain port contract, or infrastructure technical implementation.
3. Define explicit domain ports for query execution, metadata retrieval, catalog access, and index-side effects.
4. Move the technical kernel into infrastructure packages and refactor data-engine-facing services to use the new ports.
5. Remove remaining domain-owned technical helpers, add structural checks, and run focused vendor regressions.

**Rollback**

- If vendor-specific regressions appear, roll back the affected adapter migration while preserving any already-clean semantic model moves where possible.
- Because this phase changes code ownership rather than schema, rollback remains code-only.

## Open Questions

- Which current `reactor/data/sql/**` types truly represent domain semantics, and which should move with the parser implementation?
- Should query execution and metadata retrieval be split into separate ports, or is one cohesive data-engine port clearer for current services?
- After Phase 2C extracts direct remote callers, will any valid consumer remain for `HttpUtils`, or should it be deleted rather than relocated?
