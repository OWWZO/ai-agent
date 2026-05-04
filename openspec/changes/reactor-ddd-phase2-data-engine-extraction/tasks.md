## 1. Inventory semantic versus technical ownership

- [ ] 1.1 Map every class under `reactor/data/jdbc/**`, `reactor/data/provider/jdbc/**`, `reactor/data/sql/**`, and the `JdbcUtils` / `ESUtil` / `HttpUtils` helper set
- [ ] 1.2 Classify each mapped type as domain semantic model, domain port contract, or infrastructure technical implementation

## 2. Define domain data-engine seams

- [ ] 2.1 Introduce explicit domain ports for query execution, metadata retrieval, catalog access, and index-side effects used by in-scope services
- [ ] 2.2 Move or reshape semantic query models so `domain` keeps only business query meaning and not parser or execution mechanics
- [ ] 2.3 Define the target infrastructure package structure for JDBC kernel, catalog, dialect, parser, and helper implementations

## 3. Migrate the technical kernel to infrastructure

- [ ] 3.1 Move JDBC connection factories, pools, dialect loaders, catalog loaders, and provider executors into infrastructure-owned packages
- [ ] 3.2 Move technical SQL parser and dialect-formatting implementations behind infrastructure adapters while preserving domain semantic outputs
- [ ] 3.3 Relocate or delete `JdbcUtils`, `ESUtil`, and `HttpUtils` so their ownership no longer lives in `domain`

## 4. Refactor domain services to ports

- [ ] 4.1 Refactor `DataAgentService`, `ChatModelInfoService`, `ColumnValueSyncService`, and other in-scope services to use the new data-engine ports
- [ ] 4.2 Remove direct dependencies on connection configs, provider executors, parser utilities, and technical helper classes from `domain`
- [ ] 4.3 Add adapter-level tests for vendor-specific query, catalog, and metadata behavior

## 5. Verify and document the boundary

- [ ] 5.1 Add structural checks that fail if connection pools, data sources, catalog loaders, dialect factories, or shared technical helpers return to `domain`
- [ ] 5.2 Run focused regressions for semantic query handling, metadata retrieval, and supported vendor execution paths through the new port-and-adapter seam
- [ ] 5.3 Update root and module `CLAUDE.md` files to document the Phase 2D data-engine boundary and the allowed semantic-model residue in `domain`
