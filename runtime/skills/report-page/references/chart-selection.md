# Chart Selection Reference

Use this reference before choosing or implementing charts in a document-style report. The goal is not to show every possible ECharts type; it is to choose the simplest visual that makes the report's evidence easier to read.

This guidance is adapted for local ECharts report output from the broader `data-analytics:visualize-data` chart-selection rules.

## Selection Workflow

1. Write the analytical question in one sentence.
2. Write the intended takeaway in one sentence.
3. Identify the comparison the reader must make: status, movement, ranking, mix, spread, relationship, benchmark, matrix pattern, or additive driver bridge.
4. Check whether the data is strong enough for the chart. If not, either query or derive a better grain, or switch to a KPI strip, table, or prose.
5. Choose a chart family first, then a concrete ECharts implementation.
6. Record the chart contract in source notes or payload comments: question, takeaway, fields, grain, chart type, allowed alternate types, units, source, and caveats.

## Chart Selection Table

| Reader needs to understand | Preferred form | ECharts implementation | Use it well |
|---|---|---|---|
| Latest status or a few exact headline metrics | KPI strip or compact table | `text` block with KPI HTML, or `table` block | Use when exact values matter more than shape. Do not force a chart for 1-3 headline numbers. |
| Movement over time or ordered periods | Line | `line`; fallback `bar` for discrete periods | Aim for at least 8-12 time points for a real trend. With only 2-4 periods, prefer grouped bars, slope-style line, KPI callout, or table. |
| Discrete period comparison | Bar | `bar` | Use when periods are few and the question is comparison, not continuous shape. |
| Category comparison | Sorted bar or dot/lollipop | `bar`; custom dot/lollipop via ECharts scatter/line if useful | Sort when there is no semantic order. Use horizontal bars for long labels. Avoid redundant legends. |
| Ranking or top-N concentration | Ranked bar, compact leaderboard, Pareto | `bar`; Pareto as `bar` plus cumulative `line` | Keep top-N compact. Use a table for long-tail lookup. Use Pareto only when cumulative share is the point. |
| Part-to-whole at one point | Stacked bar or small pie | stacked `bar`; `pie` only for few slices | Keep the denominator explicit. Use pie only for rough composition with few categories and no close comparisons. |
| Composition over time | Stacked bar or stacked area | stacked `bar`; `line`/area-style option when total shape matters | Use stacked views when parts should read as one total. Use separate lines when component trajectories matter more. |
| Distribution or spread | Histogram or box plot | `bar` histogram; custom box plot if implemented | Use bins that reveal shape. Use box plot when comparing median and spread across groups. |
| Relationship between two numeric variables | Scatter or bubble | `scatter`; bubble with `symbolSize` | Aim for at least 12-20 meaningful points. Use bubble only when the third variable materially changes interpretation. |
| Matrix, cohort, or dense two-dimensional pattern | Heatmap | `heatmap` | Use when row-column intensity is the evidence. Keep labels readable and color scale explicit. |
| Additive bridge from start to end | Waterfall | waterfall-style `bar` with invisible offsets | Use only when drivers sum cleanly to the ending value. Otherwise use ranked bar. |
| Ordered stage progression or drop-off | Stage bar or funnel | `bar`; `funnel` only when funnel geometry helps | Prefer stage bars when funnel area distorts comparison. |
| Actual vs plan, benchmark, or target | Bar with reference, line with reference, bullet-style table | `bar`/`line` plus markLine/reference series | Make units, target date, and favorable/unfavorable direction explicit. |
| Uncertainty, ranges, or confidence intervals | Dot and interval, error bar, table | custom ECharts series or table | Do not hide uncertainty in a single point estimate when interval width changes the interpretation. |
| Multi-dimensional qualitative or score profile | Radar, scorecard, or small multiple bars | `radar`; or grouped `bar` when exact comparison matters | Use radar for rough shape across a few shared dimensions, not precise ranking. Keep dimensions limited, explain scoring basis, and prefer bars/tables when readers need exact values. |

## Sufficiency Rules

- Do not use a line chart just because the story says "trend"; use it only when enough ordered points reveal movement or shape.
- For time series, first try to expose a finer grain or longer lookback if fewer than 8 time points are available.
- For scatter, use one row per meaningful observation at a consistent grain. Fewer than 8 points is usually a table, bar, dot/lollipop, or labeled comparison.
- For category bars, fewer than 4 categories may be better as KPI cards or prose unless the comparison itself is the point.
- For composition, include the denominator or total. Avoid pie charts when slices are numerous, close in size, or need exact comparison.
- For radar charts, use only when every entity shares the same small set of dimensions and the intended takeaway is profile shape. Avoid radar when dimensions are arbitrary, scoring is unexplained, or the reader needs exact category-by-category comparison.
- For waterfall, confirm the arithmetic adds from the start value to the end value. If it does not, use ranked bars for drivers.
- If the evidence is sparse, state the limitation in the report notes and choose the more honest form.

## Encoding Rules

- Use charts for shape and comparison; use tables for exact lookup.
- Use one categorical axis and one quantitative axis for a single-measure bar chart.
- Do not encode the same category as both axis and color just to create multi-colored bars; it creates a redundant legend.
- Use color or series only for a meaningful second grouping dimension such as segment, product line, region, or scenario.
- Avoid green/red by default for positive/negative values. Prefer signed labels, zero-line context, and a restrained two-tone system unless domain convention requires red/green.
- Do not rely on color alone. Use order, labels, line style, marker style, open fills, or faceting when distinction matters.
- Reuse the report's existing chart palette across chart families unless the data introduces a real new semantic grouping. Do not fall back to ECharts default colors when a shared palette is already established.
- Keep chart titles descriptive in reports. Put the takeaway in nearby narrative or caption text.
- Captions should state metric, units, time window, grain, and source context when those affect interpretation.

## Report Planning Checklist

For every chart block, decide and document:

- analytical question;
- one-sentence takeaway;
- chart family and concrete ECharts type;
- data grain and row count;
- fields used for x, y, series/color, size, label, reference, or denominator;
- initial chart type and allowed alternate types;
- fallback if the chosen chart would be underpowered;
- caption units and source context;
- Data Source transform snippet.

For reports with multiple charts, make a compact chart map before implementation. If most sections use the same chart family, check whether the report is actually asking different visual questions such as status, movement, mix, variance, relationship, or drivers.
