---
name: bsk-browser
description: Use when driving the user's already-connected browser through the bsk command surface (the `browser` tool with argv arrays, or `browser.command({args})` inside code_mode). Covers reusing the existing agent window instead of spawning new ones, the command set that actually works, verified quirks/failures, and how to run a full command-surface test.
---

# bsk-browser

`bsk` drives a real browser the user already has open. The user is already connected — treat that connection as a shared, precious resource.

## Rule 0 — reuse, never re-spawn

- **Never** call `session start` repeatedly to "get a session". Every call creates a **new agent window + new tab**, and the user will notice and object.
- Call `session start` **once** at the beginning of a task if and only if no agent tab exists yet. Afterwards every command inherits the same session.
- Prefer acting on the tab you already have: `navigate` on the current tab instead of opening new tabs; `tab list` to see what exists before creating anything.
- If you must branch, use `tab create` (no URL argument — see quirks) then `navigate` inside it, and close it when done.
- Do not use `host_cli` (bsk/twitter/rdt) or `agent_browser` when the user asked for the `browser` tool.

## Command surface (verified working)

| Command | Notes |
| --- | --- |
| `session start` | once per task; returns `session_id`, `agent_window_id` |
| `navigate <url>` | returns `final_url`, `reached: "load"`, `tab_id` |
| `navigate-back` / `navigate-forward` | return `final_url` + `previous_url` |
| `reload` | same envelope as navigate |
| `observe` | accessibility/VOM tree with `@eN` refs + `ref_count` |
| `snapshot` | byte-identical output to `observe` on the same page |
| `get-html` | full DOM string + `byte_size` |
| `screenshot` | `image_base64`, `width`, `height` (PNG, device-scale ~1.5x) |
| `evaluate <expr>` | returns `{ok, value}`; read-only DOM queries only |
| `click <ref\|--selector>` | returns click `x`/`y`; does **not** prove navigation |
| `hover <ref>` | returns hover coordinates |
| `focus <ref>` / `blur <ref>` | `focused` / `was_focused` booleans |
| `fill <ref> --value <text>` | returns `value_length` |
| `select <ref> --value <v>` | returns `selected_values` + `selected_labels` |
| `press <key>` | e.g. `Tab`; returns `key`, `code`, `modifiers` |
| `scroll-to <ref>` | scroll element into view |
| `tab list` | `tab_id`, `scope` (`user`/`agent`), `active`, `window_id`, `title`, `url` |
| `tab create` / `tab close` / `tab select --tab-id <id>` | see quirks |
| `wait-for-navigation` | blocks until navigation settles |

## Verified quirks and failures

- `wait-ms <duration>` → `unknown_method: tool.wait_ms not implemented in extension`. Use `evaluate` polling or `wait-for-navigation` instead.
- `tab create <url>` → `too many arguments for 'create'. Expected 0 arguments`. Create empty, then `navigate`.
- `tab select <id>` (positional) → `requires a tab id (positional or --tab-id)` even though one was passed. Use `--tab-id <id>`.
- `navigate data:text/html,...` → `cdp_failed: Page.navigate rejected: net::ERR_ABORTED`. Use a real http(s) page for local HTML fixtures (or a file:// URL).
- `select` without `--value` → `select requires at least one --value` (expected, not a bug).
- `observe` and `snapshot` are indistinguishable — don't burn a call on both.
- `click` success only means the coordinate was clicked. Verify postcondition with `evaluate` / fresh `observe` / `final_url`.

## Orchestration

Use `code_mode` with `browser.command({ args: [...] })` — note the object wrapper; a bare array argument throws `browser.command 需要非空 args 数组`.

```js
async function main(ctx) {
  const out = {};
  const run = async (k, args) => {
    try {
      const r = await ctx.browser.command({ args });
      let s = r.stdout || "", p = null; try { p = JSON.parse(s); } catch (e) {}
      let summary = p ? { ...p } : s.slice(0, 300);
      for (const key of ["image_base64", "html", "text"])
        if (typeof summary[key] === "string") summary[key] = `<len ${summary[key].length}>`;
      out[k] = { ok: r.ok, res: summary, err: (r.stderr || "").slice(0, 200) };
    } catch (e) { out[k] = { error: String(e) }; }
  };
  await run("nav", ["navigate", "https://example.com"]);
  await run("observe", ["observe"]);
  return out;
}
```

Always truncate `html` / `text` / `image_base64` before returning — otherwise a single call floods the conversation.

## Command-surface test recipe

1. `session start` once, `tab list` to record baseline.
2. Read-only: `navigate`, `observe`, `snapshot`, `get-html`, `screenshot`, `evaluate`, `tab list`.
3. Navigation: `navigate-back`, `navigate-forward`, `reload`, `wait-for-navigation`.
4. Interaction on a form fixture (`https://www.selenium.dev/selenium/web/web-form.html` or `https://httpbin.org/forms/post`): `fill`, `click` (radio/checkbox), `select --value`, `focus`, `blur`, `hover`, `press`, `scroll-to`.
5. After each mutation verify with a read-only `evaluate` (`input.value`, `:checked`).
6. Report per command: works / fails / needs different args / unknown, with exact error text. Never report a click as "successful navigation".

## Reporting

Lead with the verdict, then a compact table of command → result → exact error. Separate "genuinely broken", "argument contract differs from docs", and "expected validation error".
