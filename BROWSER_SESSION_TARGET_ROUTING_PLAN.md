# Browser Session Target Routing Development Plan

Status: Implemented
Owner: Implementation agent
Scope: `adapter-host/` and `browser-extension/`

## 1. Background

OpenCLI currently has two browser backends:

1. Browser Bridge: the CLI sends commands through the Reactor relay to the Chrome extension.
2. Direct CDP: the CLI connects to a CDP endpoint and attaches to a Chrome/Electron target directly.

Both backends expose the same user-facing commands:

```bash
opencli browser <session> tab list
opencli browser <session> tab select <targetId>
opencli browser <session> <command> --tab <targetId>
opencli browser <session> click <target> --follow-new-tab
```

The two backends must not use the same state authority.

For Browser Bridge, the Chrome extension already owns the browser session lease and the session-to-tab relationship. The CLI must not maintain a second default-tab state for Browser Bridge.

For Direct CDP, there is no extension lease and there will be no CLI-local session-to-target mapping. Direct CDP target selection is command-scoped: callers use `--tab <targetId>` for the command they are executing, and must pass the returned target ID again on later commands.

## 2. Problem Statement

The current implementation has a local `browser-state` cache that can store a default `targetId`. If Browser Bridge reads or writes that cache, the following two state authorities can disagree:

```text
CLI local cache:       logical session -> default targetId
Browser extension:     session lease -> preferred Chrome tab
```

This is especially dangerous when:

- `tab select` changes the extension lease but the CLI cache still points at an old target.
- A target is closed or the extension service worker restarts.
- The same logical session name is reused with different Browser Bridge profiles.
- Direct CDP and Browser Bridge use the same session name but accidentally share local state.

The implementation must make the ownership boundary explicit:

```text
Browser Bridge: extension lease is authoritative.
Direct CDP:     no cross-command target state; `--tab` is command-scoped.
```

## 3. Goals

### 3.1 Browser Bridge goals

- `tab select <targetId>` updates the extension-side session lease.
- Later commands using the same logical session route to the extension-selected tab.
- `--tab <targetId>` routes one command without changing the session default.
- `click --follow-new-tab` discovers a child tab and updates the extension-side preferred tab.
- `tab list` exposes only tabs owned by or adopted by the current Browser Bridge session.
- Browser Bridge does not read or write the CLI local default-target cache.
- Owned session cleanup closes only tabs owned by that session.
- Bound sessions remain restricted to their explicitly bound user tab.

### 3.2 Direct CDP goals

- `tab list` lists inspectable targets from the configured CDP endpoint.
- `--tab <targetId>` attaches only the current command to the requested target.
- `tab select` is rejected for Direct CDP with a structured hint to use `--tab <targetId>`; it must not pretend to create a default target.
- `click --follow-new-tab` discovers a newly created target and returns its `targetId`; it does not create a future default target.
- Every command may create a fresh WebSocket and a fresh CDP `sessionId`.
- The command-level identity is the browser `targetId`; the CDP `sessionId` is temporary and never persisted.
- No target state is persisted by Direct CDP.
- Closed or stale targets are handled per command without guessing a replacement target.

### 3.3 Shared user-facing goals

The same commands should have the same semantic meaning on both backends:

| Command | Meaning |
|---|---|
| `tab list` | List the tabs/targets visible to the current backend session. |
| `tab select <targetId>` | Browser Bridge: change the extension lease default. Direct CDP: reject with a hint to use `--tab`. |
| `--tab <targetId>` | Route one command to a target without changing the default. |
| `click --follow-new-tab` | After a successful click, report the first newly opened page, preferring the active one. Browser Bridge also promotes it in the extension lease. |

## 4. Non-Goals

- Do not add a database table or Execution Ledger record for browser target routing.
- Do not add a new Reactor Java session store.
- Do not make Reactor Java infer or rewrite `targetId` values.
- Do not make Direct CDP pretend to own or close arbitrary user tabs.
- Do not make Browser Bridge expose every tab in a shared Chrome window to every session.
- Do not change the existing `BrowserTool` argv contract unless a test proves that the current CLI forwarding is insufficient.
- Do not change bound-session ownership rules without a separate requirement.

## 5. Source Of Truth And State Model

### 5.1 Browser Bridge

The extension is the only authoritative owner of Browser Bridge tab routing.

Runtime state:

```text
automationSessions[leaseKey]
  session
  surface
  preferredTabId
  tabIds
  windowId
  owned / bound
  idle lifecycle
```

The extension converts between Chrome's internal `tabId` and the cross-layer `targetId`:

```text
targetId <-> tabId
```

The mapping is held in the extension identity module and refreshed from Chrome debugger targets when necessary.

The lease registry is persisted in `chrome.storage.session` so it can survive an MV3 service-worker restart during the same browser session. Chrome tab and target identifiers remain runtime-scoped and must be treated as stale after the tab is closed or the browser/extension runtime is restarted.

The CLI must not use `~/.opencli/cache/browser-state` for Browser Bridge routing.

### 5.2 Direct CDP

Direct CDP has no extension lease and no persistent target registry.

```text
Each command + optional --tab targetId -> one temporary CDP attachment
```

The following values are intentionally not persisted:

- logical browser session -> `targetId`
- `tab select` -> `targetId`
- `--tab` -> `targetId`
- `--follow-new-tab` -> newly discovered `targetId`
- CDP `sessionId`

The caller must pass the target ID again on a later command:

```bash
opencli browser work tab list
opencli browser work click 12 --follow-new-tab
opencli browser work state --tab <selected_page>
```

If no target is supplied, Direct CDP uses its existing endpoint-level automatic target scoring for that command only.

## 6. Implementation Plan

### Phase 0: Baseline And Scope Guard

1. Inspect the current worktree before editing.
2. Do not revert unrelated user changes.
3. Confirm the implementation only touches:
   - `adapter-host/`
   - `browser-extension/`
   - relevant OpenCLI documentation/tests
4. Confirm no Java, MySQL, Docker, or deployment configuration change is needed.
5. Record the current behavior with focused tests before changing it.

Verification:

- Existing adapter-host unit tests pass or their pre-existing failures are recorded.
- Both TypeScript projects typecheck before the functional change.

### Phase 1: Split CLI Target State By Backend

Primary file:

- `adapter-host/src/cli.ts`

Tasks:

1. Add an explicit backend check based on `OPENCLI_CDP_ENDPOINT` or the selected browser factory.
2. Remove cross-command target loading for Direct CDP. `getBrowserPage()` must never load a saved target for either backend.
3. Keep explicit `--tab <targetId>` validation for both backends.
4. For Browser Bridge:
   - explicit `--tab` calls the page's local target binding only for that command;
   - `tab select` calls the extension tabs-select operation;
   - do not write the CLI browser-state file.
5. For Direct CDP:
   - explicit `--tab` validates and attaches to the target for the current command;
   - do not persist `tab select`, `--tab`, or `--follow-new-tab` mappings;
   - reject `tab select` with a structured hint to use `--tab`;
   - do not read or clear a saved target because no Direct CDP target state exists.
6. Update close and follow-new-tab paths so only Browser Bridge changes a persistent default, through the extension lease.
7. Ensure old unscoped Browser Bridge cache files are ignored. Do not use them as a fallback.

Verification:

- A Browser Bridge command with the same session does not create or update `browser-state`.
- A Direct CDP command does not read or write a target state file.
- Two CDP endpoints using the same logical session have no shared target state.

### Phase 2: Make The Extension Lease The Browser Bridge Authority

Primary file:

- `browser-extension/src/background.ts`

Supporting file if needed:

- `browser-extension/src/identity.ts`

Tasks:

1. Keep `preferredTabId` as the default tab for an owned session.
2. Maintain a session-scoped `tabIds` set/list for tabs created by or opened from the session.
3. When listing tabs for an owned session:
   - include the preferred tab;
   - include known session tabs;
   - discover child tabs through `openerTabId` chains;
   - exclude unrelated tabs in the same window or tab group;
   - resolve each visible tab to its `targetId`.
4. When `tabs/select` receives a target:
   - resolve `targetId -> tabId`;
   - validate that the tab belongs to the current owned session;
   - activate the Chrome tab where supported;
   - update `preferredTabId`;
   - persist the lease registry.
5. When a child tab closes:
   - remove it from `tabIds`;
   - preserve the parent session if the preferred tab remains alive;
   - persist the cleaned registry.
6. When an owned lease is released:
   - clean up child tabs owned by that lease;
   - never remove tabs claimed by another lease.
7. Preserve bound-session restrictions:
   - bound sessions remain attached to exactly one user tab;
   - `tab new`, `tab select`, and `tab close` remain blocked for bound sessions;
   - switching a manually controlled user tab requires a new `bind` operation.

Verification:

- Two owned sessions in the same window cannot see or select each other's tabs.
- A child tab opened with `target=_blank` or `window.open()` is discoverable when Chrome reports `openerTabId`.
- A closed child tab disappears from `tab list`.
- Service-worker recovery restores the preferred tab and session tab set when the tabs are still alive.

### Phase 3: Implement `--follow-new-tab` Without Duplicating Browser Bridge State

Primary files:

- `adapter-host/src/cli.ts`
- `adapter-host/src/browser/page.ts` or the shared page abstraction if the orchestration belongs there
- `adapter-host/src/types.ts` if the click result/options contract needs extension

Preferred design:

1. Keep the click execution itself in the existing page abstraction.
2. Before the click, collect the current backend-visible page list.
3. Execute the click once.
4. Poll for newly visible pages for a bounded short interval. Do not retry the click.
5. Identify new pages by `targetId/page`, never by array index alone.
6. Prefer a newly opened page marked active; otherwise use the first new page in stable list order.
7. Call `selectTab()` on the chosen page.
8. For Browser Bridge, call `selectTab()` so the extension updates its lease.
9. For Direct CDP, return the new target ID but do not persist or promote it for later commands.
10. Return structured output:

```json
{
  "clicked": true,
  "target": "12",
  "matches_n": 1,
  "opened_pages": [
    {
      "page": "target-2",
      "url": "https://example.com/detail",
      "title": "Detail",
      "active": true
    }
  ],
  "selected_page": "target-2"
}
```

If no new page appears, the click remains successful and `selected_page` is omitted. The command must not fail merely because the clicked element did not open a new tab.

Do not add a new relay action unless the existing `tabs/list` and `tabs/select` operations cannot satisfy the race/ownership requirements. The existing protocol is sufficient for the first implementation and avoids Java-side changes.

Verification:

- A click that opens a new page selects it in Browser Bridge by changing the extension's `preferredTabId`.
- A click that opens no page remains successful.
- A click that navigates the current page does not mistake the navigation for a new tab.
- A source page that closes immediately does not cause a second click or an unsafe fallback.
- A newly opened unrelated tab is not selected if it lacks a valid session ownership relationship.

### Phase 4: Direct CDP Command-Scoped Target Attach

Primary files:

- `adapter-host/src/browser/cdp.ts`
- `adapter-host/src/cli.ts`
- `adapter-host/src/browser/cdp.test.ts`

Tasks:

1. Keep browser-level target discovery through `Target.getTargets`.
2. Keep `targetId` as the target identity returned by `tab list` and accepted by an explicit `--tab` command.
3. Ensure the current-command attach path used by `--tab` performs:
   - `Target.detachFromTarget` for the current attached target when needed;
   - `Target.attachToTarget({ targetId, flatten: true })`;
   - update of the in-memory target/session fields.
4. Ensure page-scoped CDP commands carry the current temporary CDP `sessionId`.
5. Ensure browser-scoped CDP commands such as `Target.*` do not carry the page session ID.
6. Do not add a Direct CDP target state file or session-to-target registry.
7. If a caller supplies a stale `--tab`, return a clear stale-target error for that command.

Verification:

- `--tab target-2` attaches the current command to `target-2`.
- A later command without `--tab` does not reuse `target-2` through CLI state.
- `tab select` returns a structured unsupported/usage response with a hint to use `--tab`.
- The temporary CDP `sessionId` is never written to disk.
- A target close produces a clear stale-target error for explicit selection.

### Phase 5: Tests And Documentation

Update or add:

- `adapter-host/src/cli.test.ts`
- `adapter-host/src/browser/cdp.test.ts`
- `adapter-host/src/browser/page.test.ts`
- `adapter-host/tests/e2e/browser-tabs.test.ts`
- Extension tests or test seams for `background.ts` if the current extension test setup supports them
- `adapter-host/skills/opencli-browser/SKILL.md`
- `adapter-host/docs/guide/browser-bridge.md`
- `adapter-host/docs/zh/guide/browser-bridge.md`
- `adapter-host/docs/advanced/cdp.md`
- `adapter-host/README.md`
- `adapter-host/README.zh-CN.md`

Documentation must state explicitly:

- Browser Bridge default target state lives in the extension lease.
- Browser Bridge does not use the CLI local target cache.
- Direct CDP does not persist a default target; use `--tab <targetId>` on each command.
- CDP `targetId` survives a new connection when the browser tab survives; CDP `sessionId` does not.
- `--tab` is one-shot; only Browser Bridge `tab select` and `--follow-new-tab` update a persistent backend default.

## 7. Test Matrix

### Browser Bridge

- `tab list` returns the current session's target IDs.
- `tab select` updates extension `preferredTabId`.
- A second CLI process with the same session uses the extension-selected tab.
- No Browser Bridge command creates or updates `browser-state` files.
- `--tab` does not update `preferredTabId`.
- `--follow-new-tab` updates `preferredTabId` through `tabs/select`.
- Unrelated tabs in the same window are excluded.
- Bound sessions reject tab mutation and remain single-tab.
- Closed targets are removed from the extension lease.

### Direct CDP

- `tab list` returns all inspectable page targets from the endpoint.
- `tab select` does not persist a target mapping; use `--tab` for command-scoped Direct CDP routing.
- `--tab` attaches only for the current command.
- `--follow-new-tab` reports the new target without persisting it.
- No endpoint/profile/session target state is written.
- Temporary CDP session IDs are not persisted.

### Regression

- Existing browser click/type/state tests pass.
- Existing tab E2E tests pass.
- `adapter-host` typecheck passes.
- `browser-extension` typecheck passes.
- `browser-extension` production build passes.
- `adapter-host` production build and manifest generation pass.

## 8. Verification Commands

Run from `adapter-host/`:

```bash
npm run typecheck
npx vitest run --project unit src/cli.test.ts src/browser/cdp.test.ts src/browser/page.test.ts
npx vitest run --project e2e-fixed-port
npm run build
```

Run from `browser-extension/`:

```bash
npm run typecheck
npm run build
```

If the adapter-host incremental TypeScript build leaves `dist/` empty after `clean-dist`, use the repository-safe forced build sequence:

```bash
npx tsc --build --force
node -e "require('fs').chmodSync('dist/src/main.js', 0o755)"
npx tsx src/build-manifest.ts
```

## 9. Acceptance Criteria

The work is complete only when all of the following are true:

1. Browser Bridge has one authoritative default-tab state: the extension lease.
2. Browser Bridge never reads or writes the CLI local default-target cache.
3. Direct CDP has no cross-command target mapping or target state file.
4. Each backend has explicit, documented semantics for `tab select`, `--tab`, and `--follow-new-tab`.
5. `--tab` does not unexpectedly change the backend default.
6. Browser Bridge `tab select` and `--follow-new-tab` change the extension default; Direct CDP does not persist either operation.
7. Browser Bridge session isolation is preserved across owned sessions and bound sessions.
8. No Reactor Java, database, deployment, or relay persistence change is required.
9. Focused tests and build/typecheck commands pass.
10. Documentation explains where the authoritative state lives for each backend.

## 10. Handoff Notes For The Implementing Agent

- Inspect the current worktree before editing; unrelated changes are user work.
- Reuse the existing `tabs` protocol operations before adding new protocol actions.
- Do not store Browser Bridge or Direct CDP target state in a second CLI cache.
- Do not persist CDP `sessionId`.
- Do not infer a target by array index after a page change; use the returned `targetId/page` identity.
- Do not retry a click merely because popup discovery timed out; click outcome and popup discovery are separate results.
- Keep the Reactor Java `BrowserTool` argv forwarding contract unchanged unless a focused test proves otherwise.
