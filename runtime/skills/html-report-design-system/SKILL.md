---
name: html-report-design-system
description: Create polished, interactive, mobile-friendly HTML reports using the AllyHub-style report design system. Use this skill whenever the user asks for an HTML report, interactive research/analysis page, source-backed visual summary, dashboard-like report page, or wants to apply the provided blue/sage/sand/rose/slate/moss/plum/ember/mist/clay report format. It covers visual tokens, layout, cards, charts, citations, responsive behavior, animation, and source-level validation; prefer it even when the user says only “做成 HTML 报告” without specifying a style.
compatibility: Requires a writable workspace for the final HTML. Chart.js may be loaded from cdnjs when charts are needed. Do not depend on opening the generated HTML for validation.
---

# HTML Report Design System

Use this skill to turn research, analysis, structured reference material, and data summaries into a self-contained, polished HTML report. The report should feel like a compact editorial/data product rather than a raw document: clear hierarchy, meaningful interactivity, restrained decoration, and strong mobile behavior.

## 1. Decide the report shape

Before writing HTML:

1. Identify the report's main job: explain findings, compare options, show trends, document a guide, or present a dashboard-like summary.
2. Select one theme from `blue`, `sage`, `sand`, `rose`, `slate`, `moss`, `plum`, `ember`, `mist`, or `clay` according to the subject. If the user gives no style preference, use the subject-appropriate theme rather than defaulting blindly to blue.
3. Keep the hero compact and information-dense. Use an eyebrow, headline or hero metric, subtitle, a hairline divider, and a small stat row only when useful.
4. Use the page shell, cards, metric blocks, tables, alerts, progress bars, and charts only when they help comprehension. Do not decorate for its own sake.
5. If data or research sources are involved, plan citations in the body and a reference section near the footer before writing the page.

## 2. Required visual tokens

Define CSS custom properties at `:root`, then use them consistently. The base system is:

```css
:root {
  --outer-bg:#E4E1DC; --page-bg:#FDFCF9; --surface:#FFFFFF;
  --border:#E8E6E2; --divider:#C5C0BA;
  --text-primary:#151515; --text-body:#3D3D3D; --text-secondary:#717171;
  --hero-bg:#DEE5F7; --hero-deco:#A3AEEE; --hero-deco2:#7F8FE0;
  --success-bg:#E8FBCF; --success-fg:#3D4826;
  --warning-bg:#FFF0DC; --warning-fg:#E47700;
  --info-bg:#DEE5F7; --info-fg:#3E54CE;
  --error-bg:#FDECEA; --error-fg:#C4433A;
  --neutral-bg:#FDF9F2; --neutral-fg:#7C746F;
}
```

Theme-specific hero overrides (only change the three hero variables):

- `blue`: `#DEE5F7 / #A3AEEE / #7F8FE0`
- `sage`: `#D7EDE2 / #8FC5A8 / #5FA882`
- `sand`: `#EDE8DE / #C8BB9F / #A99778`
- `rose`: `#F0E4E1 / #C4A8A2 / #A88078`
- `slate`: `#DDE3E8 / #9BADB0 / #6E9298`
- `moss`: `#DDE8D8 / #93B888 / #5E8F52`
- `plum`: `#E8DDEF / #B89AC8 / #8F6AAF`
- `ember`: `#F0E4D8 / #D4A882 / #B87D50`
- `mist`: `#E2E8E8 / #96B4B4 / #6A9898`
- `clay`: `#EDE4DC / #C4A890 / #A07858`

Typography:

- Use `Inter Tight, sans-serif` throughout; provide a sensible system fallback.
- Use `Noto Serif` only for hero metrics.
- Section title: `17px / 600`; card title: `15px / 600`; body: `14px / 400`; small body: `13px`; meta: `12px`; tag/badge: `11px / 500`.
- Hero metric: `48px / 500`, `letter-spacing:-0.02em`.

## 3. Page shell and responsive behavior

Use a centered shell with `max-width:1200px`, `background:var(--page-bg)`, `border-radius:24px`, and `overflow:hidden`, placed on `var(--outer-bg)`. Use a comfortable desktop outer padding, but collapse the frame on small screens:

```css
@media (max-width: 720px) {
  body { padding:0; }
  .page-shell { width:100%; max-width:none; border-radius:0; }
  .content { padding-left:16px; padding-right:16px; }
}
```

On mobile, the report must fill the viewport width: no outer padding, no side margins, no page corner radius. Collapse 2-column card grids to one column. Put wide tables inside a horizontally scrollable wrapper. Keep tap targets comfortable and avoid hover-only interactions.

## 4. Hero and footer

Both hero and footer use `--hero-bg`. Add no more than four irregular, organic blob shapes in each zone. Blobs are absolutely positioned, low-opacity decoration using `--hero-deco` and `--hero-deco2`; they must not affect layout height.

Avoid the specificity trap where a child wildcard changes blob positioning. Put meaningful content in a named wrapper such as `.hero-content { position:relative; z-index:1; }`; do not use a later `.hero > *` or `footer > *` rule that assigns `position` or `z-index`.

Hero pattern:

1. Optional eyebrow label.
2. Headline or hero metric.
3. Optional subtitle.
4. Optional white hairline divider.
5. Compact stat row of value + label pairs.

Footer pattern:

- Brand title and sub-label on the left.
- Two-column metadata on the right.
- White hairline divider.
- Bottom bar with document info on the left and copyright on the right.
- Include `Generated by <a href="https://allyhub.com" target="_blank" rel="noopener noreferrer">AllyHub AI</a>` when the generated-by line is present.

## 5. Core components

### Section heading

Use a 17px semibold title, optional 12px secondary label, and a full-width `0.5px` border rule below.

### Cards

White surface, `0.5px var(--border)`, `12px` radius, and a restrained shadow if needed. Use a 2-column grid that collapses to one column. Card headers pair a title with small metadata. Quote or insight blocks are separated with `0.5px` rules; omit the last divider.

### Metrics and KPI blocks

For metric cards, use an auto-fit grid with `minmax(140px,1fr)`. Sequence: label, value, delta, optional sparkline. Use a larger stat card for a single headline number. Use success/error colors only when the direction is meaningful; do not imply a positive or negative judgment without data.

### Pills and alerts

Pills use `11px / 500`, `3px 9px` padding, and `20px` radius. Alerts use `10px` radius and `14px 16px` padding. Match background and foreground pairs from the status tokens.

### Tables

Always wrap tables in an overflow container. Header text is 12px/500 in `--text-secondary`; body cells are 13px. Use horizontal row dividers, no divider after the last row, and a status/sentiment pill where useful. Keep tables to six columns or fewer where possible.

## 6. Charts and interaction

Prefer interactive elements when they improve exploration: tabs, expandable evidence blocks, filter chips, hover details, lightweight accordions, and responsive chart tooltips. Keep interaction discoverable and keyboard accessible.

When charts are needed, use Chart.js from cdnjs:

```html
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
```

For every chart:

1. Give it an independent, descriptive title.
2. Wrap the canvas in a fixed-height container, normally `position:relative;height:280px`.
3. Set `responsive:true` and `maintainAspectRatio:false`.
4. Disable the built-in legend and create a custom HTML legend with colored squares and label/value text.
5. Hardcode chart colors matching the CSS tokens because canvas cannot read CSS variables.
6. Choose the chart type based on the question: line/area for trends, bar for category comparison, pie for composition with no more than eight categories, scatter for relationships.
7. Add axis titles and units where they reduce ambiguity.
8. Do not create placeholder charts when real data is absent.

Use subtle motion only where it clarifies hierarchy: a gentle hero background drift, card entrance, or hover lift. Respect reduced motion:

```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation-duration:.01ms !important; transition-duration:.01ms !important; }
}
```

## 7. Sources and citation rules

For source-backed reports:

- Cite sources in the body near the relevant claim, using compact markers such as `[1]`, `[2]`, or an evidence label.
- Add a `References` / `参考来源` section near the footer containing every external URL used, including product pages, posts, author pages, articles, datasets, and CDN links that are meaningful to the report.
- Put actual links in the reference section; do not add internal section-anchor links or a table of contents that jumps to internal anchors unless the user explicitly requests it.
- Every external reference link must open in a new tab and use safe attributes:
  `target="_blank" rel="noopener noreferrer"`.
- Keep the reference list readable: source title, publisher/author, date if known, and URL.
- Never invent a URL. If a source cannot be linked reliably, state that it is unlinked rather than fabricating one.

## 8. Content and accessibility

Use semantic HTML (`main`, `section`, `header`, `footer`, `nav`, headings in order). Provide visible focus styles, sufficient contrast, descriptive button labels, `aria-expanded` for accordions, and `aria-label` or nearby text for charts. Do not rely on color alone for status. Keep paragraphs short and use cards, lists, or tables for scannability.

Use realistic data and labels. Clearly separate verified facts, recommendations, estimates, and uncertainty. If the report is a research synthesis, preserve source nuance instead of turning every statement into a definitive claim.

## 9. Validation before delivery

Do not attempt to open, render, screenshot, or visually validate HTML files written under Ally VFS paths. Validate the source directly:

- Confirm the file exists and contains the intended page shell, theme variables, hero, content sections, references, and footer.
- Check that all external `<a>` elements have `target="_blank"` and `rel="noopener noreferrer"`.
- Check that there are no internal section links such as `href="#section-name"` unless explicitly requested.
- Check that Chart.js canvases have fixed-height wrappers, `responsive:true`, and `maintainAspectRatio:false`.
- Check for responsive CSS, reduced-motion handling, semantic heading order, and non-empty alt/ARIA text where applicable.
- Check that no blob is accidentally in normal flow and that mobile CSS removes outer frame padding/radius.
- Inspect the HTML source only; do not claim visual validation that was not performed.

## 10. Delivery

Save the final report to a stable, descriptive workspace path such as `reports/<topic>-report.html`. In the final response, summarize what was created and the most important findings or metrics, then list only the final user-facing HTML path in the required `$$$` block.
