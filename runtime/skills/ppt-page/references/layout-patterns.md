# Layout Patterns

Use this file after the slide job is clear. Layout recipes are light code skeletons: they define the page slots and the flow primitives to use, but they are not locked templates. Pick a recipe first, then fit content into its slots. If the content does not fit the recipe, switch recipes or split the slide instead of shrinking everything.

## How To Choose

| Slide job | Start with |
|---|---|
| Establish a subject, section, or memorable thesis | Statement layouts |
| Show a person, product, place, screenshot, artifact, or visual proof | Evidence layouts |
| Explain metrics, rankings, tables, trends, or decisions from data | Data layouts |
| Teach, compare, diagnose, sequence, or synthesize | Reasoning layouts |

Layout classes such as `.layout-split`, `.layout-side-media`, `.layout-reading`, `.layout-data-insight`, `.layout-chart-first`, `.layout-table-first`, and `.layout-kpi-summary` live in `assets/template.html`. Flow primitives such as `.flow-stack`, `.flow-grid`, `.flow-panel`, `.content-body`, `.content-list`, `.chart-panel`, and `.kpi-strip` are lower-level building blocks inside those recipes.

## Template Gallery Map

`assets/template.html` includes a working layout gallery. When choosing a recipe, inspect or copy from the closest sample page first, then replace the sample content.

| Template page | Use as reference for |
|---|---|
| 1/15 Opening statement | logo/mark cover or opening title |
| 2/15 Editorial cover | centered serious cover for analytical, research, policy, or internal decks |
| 3/15 Section cover | dark section break or chapter reset |
| 4/15 Split text/image | balanced text plus inset image |
| 5/15 Full-bleed image cover | source-backed photo cover with overlay text |
| 6/15 Image evidence row | equal-scale image comparison |
| 7/15 Screenshot detail | image or screenshot occupying an entire side |
| 8/15 KPI summary | judgment plus 3-4 metrics |
| 9/15 Data insight | interpretation rail plus primary chart/evidence panel |
| 10/15 Table first | ledger, market comparison, risk, owner, or P&L table |
| 11/15 Dense reading | explanation-heavy content with sidebar |
| 12/15 Argument grid | claim with evidence, mechanism, implication |
| 13/15 Comparison | side-by-side alternatives or before/after |
| 14/15 Timeline / chain | sequence, causal chain, launch, incident, or history |
| 15/15 Takeaway | closing statement |

## Statement Layouts

### Cover / Brand Statement

Best for opening cards and section breaks.

Content shape: short kicker, one large statement, optional logo or geometric mark.

```html
<div class="slide-content layout-cover" data-density="airy">
  <div class="logo-row" aria-hidden="true">
    <div class="mark"></div>
    <div class="dot-ring"></div>
  </div>
  <h2 class="slide-title title-lg">Let the first slide carry the title.</h2>
</div>
```

Fit hints:

- Works best with a 1-3 line title.
- If the slide needs supporting detail, add a `.slide-lead`; if it needs evidence, switch to an evidence or data layout.

### Quote / Takeaway

Best for strong theses, section endings, and memorable conclusions.

Content shape: one short quote-scale statement plus one supporting line.

```html
<div class="slide-content layout-split" data-density="airy">
  <h2 class="quote">One card, one idea.</h2>
  <p class="slide-lead">Use the caption for sources, caveats, and presenter commentary.</p>
</div>
```

Fit hints:

- Keep the quote short enough to breathe.
- If the supporting line becomes a paragraph, use a dense reading or argument pattern instead.

### Stat Statement

Best for one number with context.

Content shape: giant number, one reading line, source/caveat in caption.

```html
<div class="slide-content layout-statement" data-density="airy">
  <div class="stat">72%</div>
  <p class="slide-lead">Readers scan the number first, then use the caption for the why.</p>
</div>
```

Fit hints:

- Use real data only; put source and definition in the caption.
- If there are multiple metrics, switch to `Data KPI Summary`.

### Text Explainer

Best for definitions, short theory, transitions, and clean teaching moments.

Content shape: title plus one concise explanatory lead.

```html
<div class="slide-content layout-statement" data-density="standard">
  <h2 class="slide-title title-md">A waterfall deck is a gallery, not a stage.</h2>
  <p class="slide-lead">Optimize for scrolling, comparison, and selective reading.</p>
</div>
```

Fit hints:

- Keep visible text short.
- If the audience must inspect causes, tradeoffs, or evidence, use a reasoning layout.

## Evidence Layouts

### Full-Bleed Photo Cover

Best for biographies, places, products, events, and section openers where the image is the anchor.

Content shape: source-backed or generated raster image, subtle scrim, short title, optional lead.

```html
<section class="slide-frame photo-slide" aria-label="Slide title">
  <div class="bleed-photo" aria-hidden="true"><img src="images/01-cover.jpg" alt=""></div>
  <div class="photo-scrim"></div>
  <div class="slide-stage">
    <div class="slide-kicker">Subject</div>
    <div class="slide-content cover-over-photo">
      <h2 class="slide-title title-lg">Let the image establish the subject.</h2>
      <p class="slide-lead">Keep overlay text short and high contrast.</p>
    </div>
  </div>
</section>
```

Fit hints:

- Use only images with enough quiet area for text or add a strong scrim.
- Move long context and citations to the caption.

### Split Text / Image

Best for explaining a moment while keeping the subject visible.

Content shape: claim + lead on one side, one inspectable image on the other.

```html
<div class="slide-content layout-split" data-density="standard">
  <div class="flow-stack">
    <h2 class="slide-title title-md">A clear claim plus a concrete image.</h2>
    <p class="slide-lead">Use this for people, products, places, launch moments, or artifacts.</p>
  </div>
  <figure class="media-frame r-4x3 fit-cover">
    <img src="images/03-subject.jpg" alt="Describe the subject">
  </figure>
</div>
```

Fit hints:

- Use `.fit-contain` for screenshots, charts, maps, or text-heavy images.
- Direct `.media-frame` children are inset by default with `--layout-media-max`; use `media-wide` on `.layout-split` only when the visual should intentionally own the full slot.
- If the image needs callouts, use Evidence Reading from `content-patterns.md`.

### Side Media / Screenshot Detail

Best for screenshots, product states, artifacts, and image evidence that needs inspection room.

Content shape: one side is owned by the visual; the other side carries eyebrow, claim, reading guide, and optional bullets.

```html
<div class="slide-stage edge-media">
  <div class="slide-content layout-side-media" data-density="standard">
    <figure class="media-frame fit-contain">
      <img src="images/04-screenshot.png" alt="Describe the screenshot">
    </figure>
    <div class="flow-stack">
      <p class="content-meta">Screenshot detail</p>
      <h2 class="slide-title title-md">Screenshots need room for inspection.</h2>
      <p class="slide-lead">Use this when UI details, labels, or product states are the evidence.</p>
      <ul class="content-list">
        <li>Keep source notes and caveats in the caption.</li>
        <li>Use `.media-right` when the image should occupy the right side.</li>
      </ul>
    </div>
  </div>
</div>
```

Fit hints:

- Use this instead of `.layout-split media-wide` when the image should own an entire side.
- Put the page label or section name inside the text column as `.content-meta`; use `.slide-stage.edge-media` so the image can touch the slide edge and top/bottom.

### Image Evidence Row

Best for screenshots, product states, visual references, or before/after evidence.

Content shape: 2-4 same-ratio images plus one interpretation line.

```html
<div class="slide-content layout-evidence-row" data-density="compact">
  <div class="image-grid three">
    <figure class="media-frame r-16x10 fit-cover"><img src="images/02-a.jpg" alt="Describe image A"></figure>
    <figure class="media-frame r-16x10 fit-cover"><img src="images/02-b.jpg" alt="Describe image B"></figure>
    <figure class="media-frame r-16x10 fit-cover"><img src="images/02-c.jpg" alt="Describe image C"></figure>
  </div>
  <p class="slide-lead">A concise interpretation belongs here.</p>
</div>
```

Fit hints:

- Keep all images in a row at the same ratio and visual scale.
- If each image needs explanation, split into multiple cards or use a matrix.

## Data Layouts

Use `data-analysis-charts.md` first to decide the analytical job and chart type. Then choose a layout recipe here.

### Data KPI Summary

Best for executive summaries, KPI packs, campaign recaps, and section openers.

Content shape: one judgment, optional reading line, 3-4 KPI cards.

```html
<div class="slide-content layout-kpi-summary" data-density="compact">
  <div class="flow-stack">
    <h2 class="slide-title title-sm">Efficiency improved, but the gain is concentrated in two channels.</h2>
    <p class="slide-lead">Use the visible slide for the judgment and the caption for source caveats.</p>
    <div class="kpi-strip" style="--kpi-min: 240px;">
      <div class="export-shape-metric kpi-card">
        <span class="export-text-label">Revenue</span>
        <strong class="export-text-value">776.8k</strong>
        <em class="export-text-note">+14% vs prior period</em>
      </div>
      <div class="export-shape-metric kpi-card">
        <span class="export-text-label">ROAS</span>
        <strong class="export-text-value">1.88x</strong>
        <em class="export-text-note">June recovered to 2.01x</em>
      </div>
      <div class="export-shape-metric kpi-card">
        <span class="export-text-label">Spend</span>
        <strong class="export-text-value">412.2k</strong>
        <em class="export-text-note">May carried the scale-up</em>
      </div>
    </div>
  </div>
</div>
```

Fit hints:

- Works best with 3-4 metrics and short notes.
- If one chart is needed to prove the claim, switch to `Data Insight Chart`.

### Data Insight Chart

Best for trends, rankings, efficiency comparisons, and ECharts-based analysis.

Content shape: interpretation rail plus one hero chart.

```html
<div class="slide-content layout-data-insight" data-density="compact" style="--split-gap: 44px;">
  <div class="flow-stack">
    <h2 class="slide-title title-sm">Pinterest is the cleanest scale candidate.</h2>
    <p class="slide-lead">The channel pairs the highest ROAS with the largest reported revenue pool.</p>
    <div class="kpi-strip" style="--kpi-cols: 1; --kpi-min: 100%;">
      <div class="export-shape-metric kpi-card">
        <span class="export-text-label">ROAS</span>
        <strong class="export-text-value">2.42x</strong>
        <em class="export-text-note">Highest among paid platforms</em>
      </div>
    </div>
  </div>
  <div class="chart-panel">
    <h3 class="chart-title">Revenue and efficiency by platform</h3>
    <div id="platformChart" class="chart-box" data-echart style="height: 100%;"></div>
  </div>
</div>
```

Fit hints:

- Let the chart own the page; the rail should interpret, not become a second report.
- If the table is the proof, switch to `Data Table First`.

### Data Chart First

Best for one chart that is the argument.

Content shape: compact title/lead, one large chart, optional source note in caption.

```html
<div class="slide-content layout-chart-first" data-density="dense">
  <div class="flow-stack" style="--section-gap: 14px;">
    <h2 class="slide-title title-sm">June recovered efficiency after May's spend scale-up.</h2>
    <p class="slide-lead">Read the slope and endpoint labels before comparing absolute volume.</p>
  </div>
  <div class="chart-panel">
    <h3 class="chart-title">Monthly spend, revenue, and ROAS</h3>
    <div id="trendChart" class="chart-box" data-echart style="height: 100%;"></div>
  </div>
</div>
```

Fit hints:

- Use when the chart needs vertical room or has annotations.
- If the chart needs many series, consider small multiples.

### Data Table First

Best for ledgers, owner lists, P&L lines, risks, customer/channel comparisons, and accountability pages.

Content shape: compact title plus one table or heatmap that owns the canvas.

```html
<div class="slide-content layout-table-first" data-density="dense">
  <div class="flow-stack" style="--section-gap: 14px;">
    <h2 class="slide-title title-sm">The strongest markets combine revenue scale and efficient CPA.</h2>
    <p class="slide-lead">Sort the table by the decision metric, not by original source order.</p>
  </div>
  <div class="table-panel">
    <table class="data-table">
      <thead><tr><th>Market</th><th>Revenue</th><th>ROAS</th><th>CPA</th></tr></thead>
      <tbody>
        <tr><td>Canada</td><td>214.2k</td><td>2.31x</td><td>12.4</td></tr>
        <tr><td>United States</td><td>198.7k</td><td>1.92x</td><td>15.1</td></tr>
      </tbody>
    </table>
  </div>
</div>
```

Fit hints:

- Works best when rows are directly comparable.
- If the table exceeds comfortable reading, split by segment or move detail to caption/source appendix.

### Data Small Multiples

Best when one busy chart would require legend-hunting.

Content shape: 2-4 same-scale charts with direct labels.

```html
<div class="slide-content layout-small-multiples" data-density="dense">
  <h2 class="slide-title title-sm">The same efficiency story appears at different channel scales.</h2>
  <div class="flow-grid two" style="--flow-cell-min: 520px;">
    <div class="chart-panel">
      <h3 class="chart-title">Paid media</h3>
      <div id="paidChart" class="chart-box" data-echart></div>
    </div>
    <div class="chart-panel">
      <h3 class="chart-title">CRM</h3>
      <div id="crmChart" class="chart-box" data-echart></div>
    </div>
  </div>
</div>
```

Fit hints:

- Keep scales, labels, and chart types comparable.
- If each chart needs a different reading guide, split into separate cards.

### Data Driver Board

Best for explaining what changed and what to do next.

Content shape: 3-5 driver cards with impact, interpretation, and next step.

```html
<div class="slide-content layout-driver-board" data-density="compact">
  <h2 class="slide-title title-sm">Reallocate toward proven demand, then repair the weak pockets.</h2>
  <div class="flow-grid three" style="--flow-cell-min: 360px;">
    <div class="export-shape-card flow-panel">
      <div class="export-text-label">RETAIN</div>
      <h3>Welcome + Price Drop</h3>
      <p>Use CRM flows to capture warm demand and support retargeting efficiency.</p>
    </div>
    <div class="export-shape-card flow-panel">
      <div class="export-text-label">SCALE</div>
      <h3>Pinterest</h3>
      <p>Increase spend only while CPA and ROAS remain inside the tested band.</p>
    </div>
    <div class="export-shape-card flow-panel">
      <div class="export-text-label">FIX</div>
      <h3>Low-performing coupons</h3>
      <p>Retire broad discounts that do not create incremental order value.</p>
    </div>
  </div>
</div>
```

Fit hints:

- Use when the slide should create action, not only describe data.
- If the decision depends on a table, pair this with a table-first slide.

## Reasoning Layouts

Use `content-patterns.md` first to choose the reasoning pattern, then choose one of these page recipes.

### Dense Reading

Best for teaching, research synthesis, policy context, and explanation-heavy slides.

```html
<div class="slide-content layout-reading" data-density="dense">
  <div class="flow-stack">
    <h2 class="slide-title title-sm">Use a reading layout when the explanation itself is the artifact.</h2>
    <p class="content-body">A reading page gives the main explanation a comfortable line length while keeping the slide readable without presenter narration.</p>
    <p class="content-body">Use it when the audience needs context, definitions, rationale, or synthesis on the slide canvas.</p>
  </div>
  <aside class="export-shape-card flow-panel">
    <h3>Sidebar</h3>
    <ul class="content-list">
      <li>Definitions or terms.</li>
      <li>Dates or constraints.</li>
      <li>Decision criteria.</li>
    </ul>
  </aside>
</div>
```

Fit hints:

- Keep one explanation, not a full memo.
- Use captions for citations, caveats, and supporting detail.

### Argument Grid

Best for claims with 2-3 proof points.

```html
<div class="slide-content layout-argument-grid" data-density="compact">
  <h2 class="slide-title title-sm">A stronger claim belongs above the proof, not beside every detail.</h2>
  <div class="flow-grid three">
    <div class="export-shape-card flow-panel"><h3>Evidence</h3><p>One compact proof point.</p></div>
    <div class="export-shape-card flow-panel"><h3>Mechanism</h3><p>Why the proof matters.</p></div>
    <div class="export-shape-card flow-panel"><h3>Implication</h3><p>What changes next.</p></div>
  </div>
</div>
```

Fit hints:

- Use for `Argument Card`, `Policy Matrix`, or short research synthesis.
- If the proof is visual, use Evidence Reading instead.

### Comparison / Matrix

Best for before/after, alternatives, policies, scenarios, or issue matrices.

```html
<div class="slide-content layout-comparison" data-density="dense">
  <h2 class="slide-title title-sm">Compare alternatives on the same dimensions.</h2>
  <div class="flow-grid two" style="--flow-cell-min: 540px;">
    <div class="export-shape-card flow-panel"><h3>Option A</h3><p>Goal, tool, cost, risk, and result.</p></div>
    <div class="export-shape-card flow-panel"><h3>Option B</h3><p>Use matching wording so the comparison scans cleanly.</p></div>
  </div>
</div>
```

Fit hints:

- Align rows by comparable dimensions.
- For 5+ rows, use a compact table rather than cards.

### Timeline / Chain

Best for sequences, causal chains, retrospectives, launches, and incident narratives.

```html
<div class="slide-content layout-timeline" data-density="compact">
  <h2 class="slide-title title-sm">A sequence should show consequence, not only order.</h2>
  <div class="flow-grid four" style="--flow-cell-min: 300px;">
    <div class="export-shape-timeline-node flow-panel"><h3>1. Trigger</h3><p>What changed.</p></div>
    <div class="export-shape-timeline-node flow-panel"><h3>2. Decision</h3><p>What actors did.</p></div>
    <div class="export-shape-timeline-node flow-panel"><h3>3. Effect</h3><p>What shifted.</p></div>
    <div class="export-shape-timeline-node flow-panel"><h3>4. Residue</h3><p>What remained.</p></div>
  </div>
</div>
```

Fit hints:

- Use for `Causal Chain`, `Event Anatomy`, or `Timeline With Consequences`.
- If there are more than 5 meaningful moments, split across cards.
