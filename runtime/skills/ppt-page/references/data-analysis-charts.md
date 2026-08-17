# Data Analysis Charts

Use this reference when a waterfall PPT turns spreadsheets, CSVs, databases, dashboards, KPI updates, financial reports, experiment results, cohort data, survey results, operational reviews, or analytical tables into slides.

The goal is not to add charts everywhere. The goal is to choose charts that make the analytical job legible, professional, and reproducible.

## Data Slide Density

Borrow the density discipline from `frontend-slides`: decide whether the deck is reading-first or speaker-led before laying out data.

- Reading-first data decks: the default for campaign recaps, spreadsheet analysis, board pre-reads, KPI packs, and async reviews. Slides may include a clear claim, 2-4 KPI cards, one chart or table, and a short reading guide, but the hierarchy must remain obvious.
- Speaker-led data decks: use fewer objects per slide, larger titles, one chart or one number, and move nuance into captions or follow-up slides.

Do not make a high-density slide by shrinking everything. If the slide needs more than one claim, more than two primary visuals, or more than about 6 compact evidence items, split it into another card.

## Default Chart Framework

For data analysis decks, prefer a proven charting framework over hand-drawn SVG/CSS charts.

Default choice: **Apache ECharts**.

Use ECharts when the deck contains any of these:

- More than one quantitative chart.
- Axes, scales, legends, labels, tooltips, or mixed positive/negative values.
- Line charts, bar charts, stacked bars, waterfall charts, scatter/bubble charts, heatmaps, treemaps, pies/donuts, box plots, funnels, or small multiples.
- Financial, KPI, executive, investor, board, or operational reporting where chart polish affects credibility.

Acceptable implementation modes:

- If the project can install dependencies, run `npm install echarts` only as an authoring aid. Before delivery, copy or inline the vetted ECharts bundle into the deck folder; the final HTML must not reference `node_modules`.
- For a shareable deck, inline the minified ECharts bundle or embed a local copied asset under the deck folder, then reference it with a relative path.
- Use `renderer: "svg"` for crisp slide rendering unless the chart is extremely large or animated.
- Keep the waterfall deck self-contained for Trae rendering and sharing. The final HTML must reference only relative files inside the deck folder, such as `images/...` or a copied local chart runtime, or inline the chart runtime directly.

Fallbacks:

- Use native HTML/CSS only for KPI cards, lightweight tables, simple status matrices, and diagrammatic callouts.
- Use hand-written SVG only for intentionally editorial diagrams or annotations, not for standard statistical charts.
- Avoid remote CDN dependencies unless the user explicitly accepts network-dependent decks.

## Chart Selection

Choose the chart by the analytical job, not by visual novelty.

| Analytical job | Preferred chart | Use when | Avoid |
|---|---|---|---|
| Trend over time | Line chart, small-multiple line chart | Monthly/weekly KPIs, revenue, margin, usage, conversion | Too many series on one chart; use small multiples instead |
| Actual vs budget / target | Combo line, grouped bar, bullet-style bar | Budget vs actual, forecast vs actual, target attainment | Pie charts; they hide variance direction |
| Variance contribution | Waterfall, sorted variance bars | Explaining profit, revenue, cost, or metric movement | Unsorted tables for executive audiences |
| Composition | Stacked bar, 100% stacked bar, treemap, donut | Cost mix, revenue mix, portfolio mix | Donuts with more than 5 slices or similar-sized slices |
| Ranking | Horizontal bar chart | Top channels, regions, accounts, products, cost drivers | Vertical bars with long labels |
| Correlation / efficiency | Scatter or bubble chart | Cost vs outcome, CAC vs conversion, volume vs quality | Bubble charts without clear x/y axis meaning |
| Cohort / retention | Heatmap, cohort matrix | Retention, activation, repayment, aging buckets | Line charts when cohort shape matters |
| Distribution | Histogram, box plot, violin-like density if supported | Latency, deal sizes, order values, error rates | Averages alone when variance matters |
| Funnel | Funnel chart, stepped conversion table | Lead-to-MQL-to-SQL, onboarding, checkout, pipeline stages | Funnel shapes when stage denominators are not sequential |
| Geography | Map only when location is the argument; otherwise bar ranking | Regional mix, route, spatial constraint | Decorative maps for non-spatial comparisons |
| Accountability | Heatmap table, status matrix, owner ledger | Risks, owners, confirmation status, next actions | Decorative dashboards without owners or actions |

## Finance And FP&A Defaults

For finance-grade decks, a good baseline sequence is:

1. Executive thesis: judgment card plus 3-5 KPI deltas.
2. Trend page: revenue, margin, operating profit, cash, or other core time series.
3. P&L bridge: budget to actual with variance contribution.
4. Driver diagnosis: sorted unfavorable and favorable drivers.
5. Revenue / AR / bookings: customer or segment risk table plus variance chart.
6. Cost / margin: margin trend plus major cost buckets.
7. Channel / sales efficiency: spend vs outcome scatter or ranked bars.
8. Owner ledger: action, owner, due date, risk, decision needed.

Recommended chart choices:

- Budget vs actual monthly trend: ECharts line chart with two series, sparse labels only on critical points.
- Operating profit bridge: ECharts waterfall or sorted variance bars; if a true waterfall is visually cramped, use a sorted driver list plus total.
- P&L structure: stacked bars or ledger table plus small composition chart.
- Marketing efficiency: scatter/bubble chart with x = cost/SQL or CAC, y = attributed revenue or win rate, bubble size = spend or SQL volume.
- AR aging / collection risk: heatmap or customer ledger; donut only for high-level status mix.
- Gross margin: line chart plus cost bucket bars; annotate the month where margin breaks.

## Slide Pattern Variety

Data decks should not repeat the same slide skeleton on every page. Mix analytical roles:

- Judgment page: one strong claim plus KPI evidence.
- Trend page: title, one-sentence reading guide, two related charts.
- Ledger page: table-first page for P&L, owner accountability, or risks.
- Driver page: ranked drivers with impact values and action priority.
- Workbench page: customer/channel/product cards with status and next step.
- Diagnosis page: big number plus one supporting chart.
- Decision page: action list with owners, dates, and expected impact.

Avoid making every slide `title + insight note + two chart panels`. That structure is useful once or twice, but repeated use makes the deck feel templated and weakens the story.

## Pair Analysis With Layout

Use this file to decide the analytical job and chart type. Use `layout-patterns.md` to choose the page recipe and light HTML skeleton.

Suggested pairing:

| Analytical need | Good layout recipes |
|---|---|
| Executive judgment, KPI pack, section opener | Data KPI Summary |
| Trend, ranking, variance, or efficiency proof with one main chart | Data Insight Chart or Data Chart First |
| P&L, customer/channel comparison, owner ledger, risk register | Data Table First |
| Comparable segments, cohorts, markets, or channels | Data Small Multiples |
| What changed and what to do next | Data Driver Board |

For data slides, avoid repeatedly placing a narrow title column beside a large chart. If the claim is long, use a full-width title plus a chart-first or table-first recipe.

## ECharts In Fullscreen

In fullscreen mode, non-presenting slides are hidden. When using ECharts, resize only visible chart containers after entering fullscreen, slide navigation, or window resize; avoid resizing charts while their slide is hidden. A simple pattern is to give chart containers `.chart-box`, `.echart`, or `[data-echart]` and let the deck script resize the currently visible instances.

## ECharts Styling Rules

Match ECharts to the selected waterfall style preset.

- For general data analysis, KPI, spreadsheet, and campaign recap decks, prefer a cool analytical accent such as Civic Slate, Seagrass Lab, Ultramarine Ledger, or Carbon Night. Use Docket Gold mainly for operational risk, incident review, hard decisions, or case-file narratives.
- Data cards and chart containers usually read better as lightly tinted, rounded, borderless panels. A good default is `--panel-radius: 8px; --panel-border: 0;` with separation coming from fill contrast, spacing, and chart gridlines rather than box outlines.
- Use the preset accent as the main positive or primary series color.
- Use a consistent unfavorable color such as red only for negative variance, risk, or underperformance.
- Use muted gray for budget, target, or benchmark series.
- Keep chart backgrounds transparent so the slide surface controls the visual system.
- Use sparse labels. Label inflection points, final values, peaks, troughs, and highlighted outliers; do not label every point unless the chart is tiny.
- Use legends only when they reduce cognitive load. Direct labels are often better for 1-2 series.
- Use tooltips only as progressive detail; the static slide must still make sense without hover.
- Prefer horizontal bars for long labels.
- Sort ranked bars by value unless preserving process order matters.
- For scatter and bubble charts, make the axes reader-facing and explicit. The title or subtitle must state what x, y, and bubble size mean.
- Avoid 3D charts, excessive animation, glossy gradients, and decorative shadows.

## Data Handling Rules

- Treat source tables as the analytical source of truth. Do not hard-code chart values until the relevant calculations have been reviewed.
- For small decks, inline reviewed data as JSON in the HTML and generate ECharts from that JSON.
- For generated one-off decks, hard-coded chart arrays are acceptable only when values are directly derived from the source and captions name the workbook/sheet/source.
- Keep units visible in chart titles, axis names, captions, or labels.
- Use consistent favorable/unfavorable logic across slides.
- For financial charts, state whether values are actuals, budget, forecast, current view, preliminary close, or final close.
- Do not imply forecast or preliminary data is closed actual data.

## QA Checklist

Before delivery of a data analysis waterfall deck:

- Confirm chart framework scripts load locally and do not require network unless approved.
- Confirm every `.echart` or chart container renders nonblank in a browser.
- Confirm axes, legends, labels, units, and colors are readable at the scaled slide size.
- Confirm critical values match the source workbook/table.
- Confirm negative values and favorable/unfavorable colors are not reversed.
- Confirm charts do not overflow the fixed `.slide-stage`.
- Confirm the deck still works if captions are hidden through compact page mode.
- Do not deliver HTML that references `node_modules` paths. Inline or copy the chart bundle into the deck folder before validation and handoff.
