# Design System

Use this reference when building or polishing a waterfall PPT from `assets/template.html`.

## Style Presets

Use `references/style-presets.md` to choose a palette, font stack, and layout voice before writing slides. Style presets may change slide surface color, display typography, accent color, and decorative vocabulary, but they must preserve the frame contract in this file.

## Visual Intent

The deck is a reading experience: a calm page with large, full-width slide cards, generous whitespace, and captions that support rather than compete with the slide. The first viewport should start with the first slide, not a separate page title or brand header. Match the reference image's feel: neutral page, white slide surfaces, strong typography, clear structure, and quiet controls.

For festival, lifestyle, food, retail, music, culture, and campaign decks, calm should not mean colorless. Use Waterfall's registered palette systems: distinctive dominant colors, sharp accents, expressive typography, and atmospheric slide surfaces. Use a registered bold preset from `references/style-presets.md` when the topic needs more energy. Bolder decks may use large color surfaces, split panels, color-coded ledgers, strong accent blocks, geometric motifs, or contextual CSS backgrounds inside slide cards, as long as the fixed waterfall frame and caption behavior remain intact.

## Card And Page Rules

- Use a fixed light gray page background: `#f1f1f0`.
- Start from `assets/template.html`; do not rebuild the page shell from scratch.
- Keep `.deck-shell` centered with `width: min(1920px, calc(100% - 48px))` and the template's vertical page padding.
- Keep `.waterfall` single-column with `column-count: 1`; never introduce two-column masonry.
- Default slide cards to white or near-black for reading decks. When a selected preset explicitly supports bolder color, slide cards may use full-color surfaces, split panels, or large accent blocks from that preset's registered palette.
- Avoid arbitrary bright full-page gradients. Use gradients only when the selected preset defines them; otherwise prefer solid registered colors and structured color blocks.
- Use square slide cards by default: `border-radius: 0`.
- Treat internal card radius and borders as part of the selected style preset, not a universal default. The base template uses light rounded, borderless internal panels; tune `--panel-radius`, `--panel-border`, `--media-radius`, and `--media-border` locally when the preset calls for sharper panels, visible ledgers, or stronger media frames.
- Avoid decorative shadows. If depth is needed, use the page/card contrast only.
- Do not invent new color palettes during implementation. If the deck feels too timid, switch to a stronger registered preset instead of adding one-off colors.
- Keep caption spacing visually balanced between slides: use symmetric vertical padding on the caption area rather than combining caption top margin with card bottom margin.
- Keep caption spacing symmetric: `.caption { margin: 0 16px; padding: var(--caption-gap) 0; }` with `--caption-gap: clamp(18px, 2.4vw, 32px)`.
- Keep compact mode deck-level: `.deck-shell.compact-pages`, not per-card collapse state.
- Use one column at every viewport width; wide screens should show a larger slide, not two side-by-side slides.
- Let slide cards fit the page width with a readable max-width on very large displays.
- Use `break-inside: avoid` for every card.
- Keep `.deck-card { margin: 0; break-inside: avoid; }`; page spacing belongs to `.caption`, not card margins.
- Keep `.slide-frame` as the responsive 16:9 shell and `.slide-stage` as the fixed `1600x900` canvas scaled by `ResizeObserver` and `--slide-scale`.
- Do not add visible deck headers, subtitles, navigation chrome, landing sections, or `Waterfall PPT` labels above the first slide.

## Card Contract

Use this structure for every slide:

```html
<article class="deck-card">
  <section class="slide-frame theme-paper" aria-label="Slide title">
    <div class="page-badge">1/7</div>
    <div class="slide-stage">
      <div class="slide-kicker">F&F</div>
      <div class="slide-content">
        <!-- slide content -->
      </div>
    </div>
  </section>
  <details class="caption" open>
    <summary>Slide notes</summary>
    <p>Write the visible note, source, or presenter commentary here.</p>
  </details>
</article>
```

- Keep captions outside `.slide-frame`; never overlay captions on the slide.
- Use native `<details>` for each caption, opened by default. Hide the per-card summary visually in the default template so readers do not see repeated `Caption` or `讲解` buttons.
- Format every page badge as `N/TOTAL`; update all badges when adding/removing cards.
- Keep slide frames 16:9 unless the user explicitly asks for another fixed ratio.
- Put slide content in a fixed-size `.slide-stage` (`1600x900` by default) and scale the whole stage with `ResizeObserver` plus `transform: scale(...)`. Do not let slide internals reflow based on page width.
- Do not add viewport breakpoint overrides for slide-internal typography, padding, grid tracks, or layout gaps. Mobile responsiveness should come from scaling the fixed stage, with breakpoints limited to the outer page shell and caption area unless the user explicitly asks for a different mobile composition.
- For full-bleed photo slides, use the template's `.photo-slide`, `.bleed-photo`, `.photo-scrim`, and `.cover-over-photo` classes so the image sits behind the fixed slide stage and the overlay text remains readable.
- Use real text in captions, not filler. If content is unknown, write a concise placeholder naming what should be added.

## Typography

- Use the selected preset's typography by default, not one universal system sans stack. Each preset should define `--font-display`, `--font-body`, `--font-zh`, `--font-ja`, and `--font-mono` when relevant.
- Prefer online fonts for the intended visual voice. Put the web font first in the stack and include its remote `@import` or `@font-face` when the deck may depend on network fonts. Omit remote font loading only for offline/private deliverables or when fonts are embedded locally.
- Keep fallbacks style-specific. If online fonts fail, an editorial serif preset should still fall back to serif families, a civic/technical preset to clean sans or condensed sans families, a rounded consumer preset to rounded/humanist families, and a literary CJK preset to Songti/YuMincho-style fallbacks. Do not let every preset collapse to the same `system-ui` look.
- For Chinese/Japanese/English decks, set separate CJK variables instead of relying on the Latin body stack to catch all glyphs. Use `--font-zh` for Simplified Chinese text and `--font-ja` for Japanese text; keep their fallbacks visually aligned with the preset's voice.
- Keep letter spacing at `0` for normal text.
- Use very large slide titles sparingly; reduce copy before shrinking type.
- Adapt title scale through slide-local variables or the built-in title classes before letting text overflow. Use `.title-xl` for short statements, `.title-lg` for normal titles, `.title-md` for longer titles, and `.title-sm` for long or mixed-language titles. Prefer splitting a long sentence into title plus `.slide-lead` over compressing all copy into one headline.
- Keep title width intentional with `--title-width`; long analytical titles should usually use the full text column, while narrower widths are better for short editorial statements. Do not rely on viewport breakpoints for slide-internal title sizing.
- For titles that should keep their shape without rough forced wrapping, use `.fit-width` and set a deliberate `--title-width`; the template will reduce the title font size down to `--title-min-size` only when the text overflows its own width. Use this for statement titles, covers, and split layouts where the title must align to a fixed column.
- Avoid using very narrow title widths as a decoration when the text is not short. If a title wraps more than intended, first widen `--title-width` or use `.fit-width`; then step down to `.title-md`/`.title-sm`; only then rewrite the title.
- Use captions around `14px-16px`, lighter than slide text, such as `#6b6b66`. Captions should feel secondary but remain readable on laptop screens.
- Keep page badges lightweight: about `11-13px`, medium weight, white on a low-opacity translucent dark background.

## Internal Spacing And Soft Layout Budgets

Waterfall slides use a fixed 1600x900 design canvas, so internal spacing should be controlled with slide-local variables rather than viewport breakpoints. The template exposes spacing tokens for flexible layouts:

| Token | Default use |
|---|---|
| `--stage-gap` | Space between kicker, optional dividers, and content area |
| `--content-gap` | Main gap inside `.slide-content` |
| `--section-gap` | Gap between stacked sections inside flow layouts |
| `--block-gap` | Gap between repeated cards, panels, images, or chart blocks |
| `--block-pad` | Padding inside `.flow-panel` and similar content blocks |
| `--split-gap` | Gap between two-column split regions |

Use `data-density` on `.slide-content` as a soft knob, not as a rigid template:

```html
<div class="slide-content" data-density="standard">...</div>
<div class="slide-content" data-density="compact">...</div>
```

- `airy`: covers, section dividers, quote pages, single visual statements.
- `standard`: most slides with one title, one visual or 2-3 content blocks.
- `compact`: slides with 3-4 short blocks, split views with labels, or modest comparison pages.
- `dense`: evidence reading, matrices, dashboards, timelines, or tables that need the full canvas.

These are elastic defaults. A page may override variables inline when the design needs it, for example `style="--flow-cell-min: 360px; --title-width: 32ch;"`. Keep the lower comfort bounds in mind: avoid `--block-gap` below `18px`, `--block-pad` below `18px`, body line-height below `1.22`, or repeated block text below `20px` unless the slide is a deliberately compact table.

When stacking a title, lead, and note inside a split column, use a real stack with a gap such as `.flow-stack` instead of relying on default block margins. The title-to-lead gap should read as intentional breathing room, not as two text boxes colliding.

Choose the page recipe in `references/layout-patterns.md` before composing primitives. Use the Template Gallery Map there to copy from the closest `assets/template.html` sample page when possible. Basic recipe classes such as `.layout-split`, `.layout-reading`, `.layout-data-insight`, `.layout-chart-first`, `.layout-table-first`, and `.layout-kpi-summary` live in `assets/template.html`. Flow primitives are low-level building blocks; they should support the chosen layout rather than become the plan by themselves. For content-heavy slides, use `.content-body` and `.content-list` instead of tiny card-label text.

In split layouts, media should usually sit inside the slot rather than touch the outer edge of the canvas. The template caps direct `.media-frame` children of `.layout-split` with `--layout-media-max`; override that variable or add `.media-wide` only when the visual is meant to dominate the slide.

When the image or screenshot should own an entire side, use `.layout-side-media` instead of widening `.layout-split`. In that recipe, put the section label or page note inside the text column as `.content-meta` and use `.slide-stage.edge-media` so the visual can touch the slide edge and top/bottom while the text column keeps its own padding.

For repeated blocks, prefer flexible primitives before writing one-off CSS:

- `.flow-stack`: vertical sections with consistent rhythm.
- `.flow-grid`: responsive-in-canvas grid using `auto-fit` and `minmax`; tune with `--flow-cell-min`.
- `.flow-split`: two-column composition that shares the same split gap token.
- `.flow-cluster`: wrapping chips, tags, or small labels.
- `.flow-panel`: padded content panel for cards, callouts, and matrix cells.

Internal panels do not always need visible borders. Prefer fill contrast, spacing, and color bands for data cards, KPI groups, soft lab, poster, quilt, gallery, or bulletin styles. Use a border when the preset's surface voice calls for ledgers, civic tables, technical interfaces, archival cards, or export-critical shapes.

Do not force four text-heavy cards into a single row just because four items exist. Use a comfortable `--flow-cell-min`, allow the grid to become 2x2, switch to a compact ledger/table, or split the idea across slides when the content needs explanation. If the content keeps pushing against the recipe, change the layout recipe before shrinking the slide.

## Color And Palette Rules

- Choose one named preset from `references/style-presets.md` before writing slide HTML.
- Match energy to context. Serious, executive, regulated, investor, policy, legal, healthcare, or board-facing decks should usually stay restrained: use fewer large color surfaces, more white/near-black space, and color only for hierarchy. Playful, cultural, lifestyle, festival, campaign, and consumer decks may use stronger registered palettes and larger color fields.
- For consumer, lifestyle, food, coffee, city events, pop-ups, campaigns, music, and retail activations, prefer a bold registered preset such as Riso Pop, Studio Lacquer, Market Quilt, Night Arcade, or Coral Bulletin.
- Use strong colors as semantic structure: section tabs, audience groups, program tracks, timeline phases, key claims, or sponsor tiers. Do not scatter colors randomly.
- Keep contrast accessible. If a color is pale, put dark text on it; if a color is dark, put light text on it.
- The global page background remains `#f1f1f0`; bold color belongs inside `.slide-frame` or `.slide-stage`.
- Do not default to timid evenly distributed palettes. Make one color dominant, one accent sharp, and any supporting colors purposeful.
- Avoid generic deck patterns: purple gradients on white, overused display fonts, predictable centered heroes, identical card grids, decorative glassmorphism, and shadows without purpose.

## Brand Color Rules

- For brand, product, company, portfolio, venue, or sponsor-heavy decks, start from official brand colors when possible.
- If the user supplies a brand guide, logo, design system, or existing deck, use that as the source of truth.
- If no brand guide is supplied and the brand is public, look up current official sources such as the brand website, press kit, media kit, brand guidelines, design system, or official logo before selecting colors. Do not rely on memory for modern brands.
- Use brand color with restraint. Let one brand color become the dominant accent or surface, then choose a registered preset whose typography and layout voice fit the audience. Supporting colors should come from that preset or from official brand secondary colors.
- If official colors conflict with readability, keep text contrast accessible and use the brand color in blocks, rules, tabs, or image treatments rather than body text.
- If official colors cannot be verified, say so briefly and use the nearest registered preset. Avoid inventing a custom brand palette.

## Caption Behavior

- Use native `<details class="caption" open>`.
- Keep captions visible by default below the slide.
- Do not show repeated per-card caption toggles such as `Caption` or `讲解` in the visual UI. The summary may be visually hidden while preserving native structure.
- Use the caption area between slide cards as a document-style page-gap toggle. Hover should change the mouse pointer itself to a small Word-like page-whitespace cursor, with subtle background feedback only if needed. Clicking any caption/gap area should toggle a deck-level compact mode that hides all caption prose and tightens all page gaps. In compact mode, keep a small invisible hit area between cards so clicking the page gap restores notes and normal spacing. Do not use zoom, plus, or minus cursors, and do not add an in-page icon for this interaction; keep native `<details>` open in the DOM.
- Do not place important slide content only in captions; the slide should still make sense at a glance.

## Page Badge Behavior

- Place `.page-badge` inside `.slide-frame`, top-right.
- Hide it by default with `opacity: 0`.
- Reveal it on `.deck-card:hover .page-badge` and `.deck-card:focus-within .page-badge`.
- Use `pointer-events: none` so the badge never traps interaction.
- Ensure the badge does not cover important content. Keep the top-right corner visually quiet.

## Accessibility

- Give each `.slide-frame` an `aria-label` that names the slide.
- Use real headings inside slides. Prefer `h2` for slide titles.
- Add alt text for informative images. Use `alt=""` only for decorative placeholders.
- Keep native `<details>` behavior unless there is a strong reason to customize.
- Support `prefers-reduced-motion`.

## Export-Safe Primitives

When a deck may be exported to editable PowerPoint, build visual blocks from export-safe primitive prefixes. These prefixes are intentionally atomic: generation should compose them, and `scripts/export-waterfall-to-pptx.mjs` recognizes the same prefixes as native PowerPoint shapes or text boxes.

Use these prefixes as the primary contract:

- Any class beginning with `.export-shape-` is treated as a PowerPoint rectangle shape.
- Any class beginning with `.export-chart-` is treated as a PowerPoint rectangle shape. Use this for chart marks such as fills, line segments, dots, bands, ranges, axes, plot backgrounds, and guides.
- Any class beginning with `.export-text-` is treated as a PowerPoint text box.

Recommended naming patterns:

| Prefix pattern | Use for | PPTX export behavior |
|---|---|---|
| `.export-shape-*` | panels, cards, cells, callouts, timeline nodes, decorative blocks | rectangle shape with fill/border |
| `.export-chart-*` | any chart mark or chart scaffold | rectangle shape with fill/border |
| `.export-text-*` | labels, values, notes, source text, chart annotations | text box |

Generation rules:

- Put fills and borders on the primitive element itself, not only on a pseudo-element or parent grid.
- Avoid relying on `::before`, `::after`, `box-shadow`, masks, blend modes, CSS filters, or complex gradients for export-critical shapes.
- If a CSS grid item must become a PowerPoint shape, give that grid item an `.export-shape-*` class, for example `.export-shape-card`, `.export-shape-cell`, `.export-shape-panel`, `.export-shape-callout`, or `.export-shape-timeline-node`.
- If a chart mark must become editable in PPTX, give it an `.export-chart-*` class, for example `.export-chart-line-segment`, `.export-chart-dot`, `.export-chart-area`, `.export-chart-band`, `.export-chart-fill`, `.export-chart-axis`, or `.export-chart-plot`.
- If a label or value is a plain `div`, add an `.export-text-*` class, for example `.export-text-label`, `.export-text-value`, `.export-text-note`, `.export-text-source`, or `.export-text-annotation`; do not assume arbitrary `div` text will be exported.
- Keep text boxes roomy. PowerPoint reflows fonts differently from browsers, especially Chinese/English mixed text. Prefer slightly smaller type, explicit spacing such as `.slide-title + .slide-lead`, and shorter line lengths for editable export.
- For complex visuals where exact fidelity matters more than editability, use PPTX raster export instead of overfitting editable primitives.

## Responsive Checks

- Desktop: waterfall must remain 1 column and slides should fit width.
- Mobile: waterfall remains 1 column.
- Long captions should not create horizontal overflow.
- Slide text must not spill outside `.slide-frame`; shorten copy first.
- Avoid hard mobile breakpoints that change slide-internal font sizes, padding, grid columns, or gaps. The `.slide-stage` is a fixed 1600x900 canvas and should scale as a whole; viewport-specific CSS should normally affect only the outer shell, max widths, overflow, and caption spacing.
