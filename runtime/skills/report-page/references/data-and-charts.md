# Data And Charts Reference

Use this reference only when the page includes quantitative data, charts, tables with computed values, or source-sensitive claims.

## Source Discipline

Every material claim should be traceable to reviewed data or named external sources when the page depends on evidence. Keep source values used by charts and tables visible in `index.html` as simple HTML tables, nearby JSON blocks, or small local files. When calculations are simple, state the calculation in prose.

Confirm before writing analysis or quantified claims:

- source files, sheet names, and source URLs;
- date ranges and freshness;
- units and currencies;
- metric definitions;
- variance conventions;
- market close dates when using stock or market data.

For market, stock, legal, regulatory, current-news, or source-freshness-sensitive pages, verify current information before finalizing and include concrete dates in the page text.

## Static Chart Data Requirements

For each quantitative chart in the default static HTML path, include:

- the data rows used for the visual, preferably near the chart as `<script type="application/json" id="...">`;
- visible units and clear labels in captions or axes;
- an initial chart type and any allowed alternate chart types;
- a concise source note in the caption or nearby source section.

Do not add Data Source modals. They make document-style pages feel like tooling rather than readable documents. Prefer visible captions, simple source lists, and auditable local JSON/table rows.

## Chart Authoring

Use ECharts for standard statistical and analytical charts. Keep chart option builders in `assets/doc.js` when they are generic, or in a small page-local script when the chart is specific to one page. Do not introduce Python just to create ECharts options.

Do not implement quantitative charts as static SVG, CSS-only bars, HTML div charts, canvas drawings, or exported image snapshots by default. Those are allowed only when the user explicitly asks for a static/non-interactive figure, when the visual is a non-quantitative diagram, or when ECharts cannot run and the fallback is clearly labeled as a fallback.

Use chart types for analytical fit:

- trends: line or bar;
- variance bridges: bar or waterfall-style bar;
- composition: stacked bar or pie only when the number of categories is small;
- ranking: sorted bar;
- relationship or efficiency: scatter or bubble;
- status exposure: bar or compact composition chart.

Do not use decorative charts when a table or short prose claim is clearer.
