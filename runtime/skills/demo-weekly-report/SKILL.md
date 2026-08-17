---
name: demo-weekly-report
description: >
  Turn messy weekly work notes into a structured, interactive weekly report HTML page
  with progress, blockers, decisions, metrics, and next-week plan. Use this skill whenever
  the user asks for a 周报, weekly report, week summary, standup rollup, or wants to
  organize “本周做了什么 / 阻塞 / 下周计划” into a shareable interactive page — even if they
  only paste bullet notes and do not say “skill”. Prefer this over plain Markdown when they
  want a page they can filter, tab, or present.
---

# Demo Weekly Report

Convert raw weekly notes into a single-file interactive HTML weekly report.

## When this skill applies

- User pastes messy bullets about the week and wants a 周报 / weekly report
- User wants progress / blockers / next week structured for sharing
- User asks for an interactive page (tabs, filters, status chips), not only chat text

## Workflow

1. **Normalize inputs**
   - Infer period (e.g. `2026-W33` or `8/11–8/17`) from user text; if missing, use the current week and state the assumption in the page header.
   - Split notes into: `done` / `in_progress` / `blocked` / `decisions` / `risks` / `next_week` / `metrics`.
   - Keep original facts; do not invent completed work. If status is unclear, mark `in_progress` or `needs_confirm` and show it on the page.

2. **Structure the data model** (internal; then render)
   Each item should carry:
   - `title` (short)
   - `detail` (optional)
   - `status`: `done` | `in_progress` | `blocked` | `planned`
   - `owner` (optional)
   - `priority`: `P0` | `P1` | `P2` (default P1)
   - `tags` (optional, e.g. backend, agent, ops)

3. **Deliver a single interactive HTML file**
   - Default filename: `weekly-report.html` (or user-specified name).
   - Prefer writing a self-contained HTML file (inline CSS/JS). CDN is OK when the environment allows it; still keep a no-CDN usable layout.
   - After writing, publish/preview if the host supports canvas/HTML preview.

4. **Chat reply stays short**
   - 3–6 bullet summary: period, done count, blocked count, top risk, next-week focus.
   - Point the user to open the HTML file. Do not dump the full report into chat.

## Required page sections

Always include these sections (empty sections show an empty state, do not omit):

| Section id | Purpose |
| --- | --- |
| `hero` | Title, period, author/team if known, generated time |
| `kpis` | Counts: done / in_progress / blocked / next_week (+ optional metric chips) |
| `progress` | Done + in-progress items as cards or rows |
| `blockers` | Blocked items with owner/need if known |
| `decisions` | Decisions made this week |
| `risks` | Risks / watch items |
| `next` | Next-week plan with priority |
| `raw` | Collapsible “原始笔记” so nothing is lost |

## Interaction requirements

Build real UI, not a static wall of text:

- **Filter chips**: All / Done / In progress / Blocked / Next week
- **Search box**: filter cards by title/detail/tag (client-side)
- **Priority badges** and **status chips** with distinct colors
- **Keyboard-friendly** focus styles; respect `prefers-reduced-motion`
- Mobile-friendly: single column under ~720px

## Visual style (default)

Professional data-report look unless the user asks for another style:

- Light background, centered max-width content (~1080px)
- White rounded cards, soft border/shadow
- Blue–purple accent for numbers and active filters
- Clear hierarchy: hero → KPIs → main grid → next week → raw notes

If the user asks for Apple style or another skill-backed style, load that design skill first and adapt — keep the same information architecture.

## HTML skeleton (follow closely)

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>周报 · {period}</title>
  <style>/* layout + chips + cards + empty states */</style>
</head>
<body>
  <main class="wrap">
    <header class="hero">...</header>
    <section class="kpis" id="kpis">...</section>
    <div class="toolbar">
      <input type="search" id="q" placeholder="搜索事项…" />
      <div class="filters" role="group">...</div>
    </div>
    <section id="progress">...</section>
    <section id="blockers">...</section>
    <section id="decisions">...</section>
    <section id="risks">...</section>
    <section id="next">...</section>
    <details id="raw"><summary>原始笔记</summary><pre>...</pre></details>
  </main>
  <script>
    // filter + search only; data can be in DOM or a small JS array
  </script>
</body>
</html>
```

## Quality bar

- Every user-mentioned concrete task appears somewhere (progress, blockers, or next).
- Blockers are never buried only inside long paragraphs — they get their own section.
- Next-week items are actionable titles, not vague “继续推进”.
- Chinese UI labels by default when the user writes in Chinese.
- Valid HTML; no broken tags; search/filter actually works.

## Edge cases

- **Only wins, no blockers**: still render blockers empty state (“本周无明确阻塞”).
- **Only plans**: KPIs can be zero done; emphasize next week.
- **Mixed languages**: keep item titles as provided; chrome in the user’s language.
- **Huge paste**: cap visible detail length in cards; full text stays in raw notes / expand.

## Anti-patterns

- Chat-only wall of Markdown when the user asked for 周报页 / 交互页
- Inventing metrics or “100% complete” without evidence
- Screenshot-like static HTML with no filter/search
- Omitting raw notes so source material disappears

## Example trigger phrases

- “帮我写这周周报，笔记如下…”
- “把这些整理成 weekly report 交互页”
- “本周工作汇总，要可筛选的 HTML”
---

## Eval-oriented checklist (for authors/tests)

A good run produces:

1. An `.html` file in the workspace
2. Visible sections for progress, blockers, next week
3. Working filter or search controls in the HTML source
4. KPI numbers consistent with listed items
5. Short chat summary + file pointer
