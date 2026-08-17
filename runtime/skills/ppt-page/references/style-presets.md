# Waterfall Style Presets

Use this reference to choose a visual style for a waterfall PPT. These presets are Waterfall-native: they keep the vertical gallery frame quiet while giving each slide surface a distinct palette, type voice, and content rhythm.

Open `assets/style-gallery.html` when you need to visually compare presets. It shows each preset as a compact token gallery: typography, Chinese/Japanese/English type samples, color roles, radius, and border behavior.

Color is a design system decision, not decoration. Prefer the registered palettes below over ad hoc hex values. The goal is **recognizable but durable**: one dominant surface, one clear accent, and supporting colors that have assigned semantic roles. Keep text contrast accessible.

Radius is also tokenized. Use `0px`, `4px`, `8px`, `12px`, `16px`, or `24px` for internal panels, badges, media masks, and style-specific surfaces. Avoid off-scale values such as `2px`, `3px`, `6px`, `10px`, or `14px` unless a user-provided brand system requires them. The outer waterfall slide frame stays square by default.

Font loading strategy:

- `assets/style-gallery.html` may load remote web fonts to preview the intended visual voice.
- Generated decks should prefer the selected preset's remote web fonts when network loading is acceptable. Put the target web font first, then macOS/Windows/system fallbacks that preserve the preset's voice.
- For Chinese/Japanese/English decks, define Latin display/body fonts plus separate CJK stacks. Match the CJK stack to the style voice: literary serif, clean humanist sans, rounded consumer sans, or condensed technical sans.
- Add remote `@import` or `@font-face` for the selected preset unless the deliverable must work offline, avoid external requests, or embed fonts locally/base64.
- If remote fonts fail, the deck must remain readable and still feel different by preset. Avoid one shared fallback such as `system-ui, sans-serif` for every style; use serif, condensed, rounded, humanist, or CJK-specific fallbacks deliberately.

Research anchors used to reset this palette system:

- Open Color: open-source UI color scheme optimized for font, background, and border use.
- USWDS color tokens: role-based color families, lightness grades, and contrast guidance.
- ColorBrewer: cartographic palettes for sequential, diverging, and qualitative data.
- Carbon color tokens: product-grade neutral ramps and high-contrast interface colors.
- Riso and print culture: limited spot-color palettes, off-register blocks, and tactile paper surfaces.

## Non-Negotiable Frame

Style presets may change slide typography, slide surface colors, accent colors, rules, charts, and decorative vocabulary. They must not change the waterfall frame:

- Keep page background `#f1f1f0`.
- Keep `.deck-shell` width and page padding from the template.
- Keep one column only.
- Keep `.slide-frame` + fixed `.slide-stage` scaling.
- Keep caption spacing, caption type, and page-gap interaction unchanged.
- Keep slide cards square by default.
- Keep page badges lightweight and hidden until hover/focus.

## How To Apply A Preset

1. Pick exactly one preset before writing slides.
2. Copy its CSS variables into `:root` after the frame variables.
3. Use the preset's typography and layout voice inside `.slide-stage`.
4. Do not mix accents from different presets. Some registered presets define multiple semantic colors; use those colors only within that preset's stated roles.
5. Do not invent arbitrary hex colors. If a deck needs a stronger mood, choose one of the bold registered presets below instead of hand-mixing colors.
6. If a preset suggests a grid, texture, split panel, badge, or full-color surface, apply it to slide surfaces only; never change the page background.
7. Bolder presets may use full-slide color fields, large accent blocks, color-coded ledgers, paper textures, or geometric motifs. Keep the waterfall frame unchanged and keep all body text readable.
8. Avoid generic deck patterns: purple gradients on white, identical centered heroes, glassmorphism, decorative shadows, random pastel scatter, and color systems with no semantic assignment.

## Formality And Brand Selection

Use energy deliberately:

- Serious / executive / regulated / investor / legal / healthcare: choose restrained presets such as Ultramarine Ledger, Civic Slate, Cartographer Field, Carbon Night, or Copper Archive. Use bold color only as hierarchy, not decoration.
- Teaching / research / analysis / history / policy: choose Ultramarine Ledger, Civic Slate, Cartographer Field, Seagrass Lab, or Carbon Night depending on the source material.
- Fun / cultural / lifestyle / festival / campaign / consumer: choose Riso Pop, Studio Lacquer, Market Quilt, Night Arcade, or Coral Bulletin.
- Brand / product / company: first identify official brand colors from user-provided guidelines or current official sources. Use the official brand color as the primary accent or major surface, then choose the closest registered preset for typography, layout voice, and supporting colors. Do not improvise a brand palette.

If a bold preset makes the deck feel unserious for the intended audience, reduce the amount of colored surface before changing the content. A formal deck can still be distinctive through typography, spacing, and a precise accent.

Recommended template variables:

```css
--accent: #002fa7;
--accent-2: #ffcf33;
--accent-soft: rgba(0,47,167,0.14);
--font-display: "Newsreader", Georgia, Cambria, "Noto Serif SC", SimSun, serif;
--font-body: "IBM Plex Sans", "Avenir Next", "Segoe UI", Arial, "Microsoft YaHei UI", "Noto Sans SC", sans-serif;
--font-zh: "Noto Serif SC", "Songti SC", SimSun, serif;
--font-ja: "Noto Serif JP", "Hiragino Mincho ProN", YuMincho, serif;
--font-mono: "IBM Plex Mono", Menlo, Consolas, "Cascadia Mono", monospace;
```

## Preset 1 · Ultramarine Ledger

Palette basis: white and saturated ultramarine blue, with a small amber signal. White stays dominant; blue acts as the unmistakable visual anchor.

Best for: default strategy decks, product narratives, board memos, careful reading decks.

```css
--paper: #ffffff;
--ink: #101319;
--muted: #5f6673;
--accent: #002fa7;
--accent-2: #ffcf33;
--accent-soft: rgba(0,47,167,0.12);
--font-display: "Newsreader", Georgia, Cambria, "Noto Serif SC", SimSun, serif;
--font-body: "IBM Plex Sans", "Avenir Next", "Segoe UI", Arial, "Microsoft YaHei UI", "Noto Sans SC", sans-serif;
--font-zh: "Noto Serif SC", "Songti SC", SimSun, serif;
--font-ja: "Zen Old Mincho", "Sawarabi Mincho", YuMincho, serif;
--font-mono: "IBM Plex Mono", Menlo, Consolas, "Cascadia Mono", monospace;
```

Typography voice: editorial serif headlines, practical sans body, mono folios. Blue can own titles, dividers, and decisive blocks.

Layout voice: white editorial pages, ultramarine title fields, blue rules, ivory evidence panels, amber key claims, quiet section numbers.

Surface voice: editorial surfaces with square or barely softened panels, fine rules, and little shadow.

Avoid: turning every page into a blue flood, rigid grid-only pages, adding extra saturated accent colors.

## Preset 2 · Civic Slate

Palette basis: USWDS-style blue-cool, gray-cool, and gold support colors with accessible contrast-first pairing.

Best for: policy, governance, compliance, healthcare, education administration, public-facing explainers.

```css
--paper: #ffffff;
--ink: #1b1b1b;
--muted: #565c65;
--accent: #005ea8;
--accent-2: #ffbe2e;
--accent-soft: rgba(0,94,168,0.12);
--font-display: "Source Sans 3", "Helvetica Neue", "Segoe UI", Arial, "Microsoft YaHei UI", "Noto Sans SC", sans-serif;
--font-body: "Source Sans 3", "Helvetica Neue", "Segoe UI", Arial, "Microsoft YaHei UI", "Noto Sans SC", sans-serif;
--font-zh: "Noto Sans SC", "PingFang SC", "Microsoft YaHei UI", SimHei, sans-serif;
--font-ja: "Zen Kaku Gothic New", "Hiragino Sans", "Yu Gothic", Meiryo, sans-serif;
--font-mono: "Roboto Mono", Menlo, Consolas, "Cascadia Mono", monospace;
```

Typography voice: official, legible, direct. Use fewer display flourishes and clearer labels.

Layout voice: policy matrices, decision paths, status bands, risk tables, step-by-step pages.

Surface voice: civic UI surfaces with clean panels, cool hairlines, restrained corners, and clear hierarchy.

Avoid: theatrical launch language, neon accents, full-slide color fields unless needed for status.

## Preset 3 · Cartographer Field

Palette basis: ColorBrewer-inspired green-blue map colors, off-white land surfaces, and field-note neutrals.

Best for: geography, market maps, travel, history, environmental topics, evidence-based spatial explanation.

```css
--paper: #fbfcf7;
--ink: #24352f;
--muted: #66736b;
--accent: #2f6f73;
--accent-2: #b8d6c2;
--accent-3: #e6d8a8;
--accent-soft: rgba(47,111,115,0.14);
--font-display: "Literata", Baskerville, Georgia, Cambria, "Noto Serif SC", SimSun, serif;
--font-body: "IBM Plex Sans", "Avenir Next", "Segoe UI", Arial, "Microsoft YaHei UI", "Noto Sans SC", sans-serif;
--font-zh: "Noto Serif SC", "Songti SC", SimSun, serif;
--font-ja: "Zen Old Mincho", "Sawarabi Mincho", YuMincho, serif;
--font-mono: "IBM Plex Mono", Menlo, Consolas, "Cascadia Mono", monospace;
```

Typography voice: field guide headings, precise captioning, measured body copy.

Layout voice: maps, routes, stacked evidence, small multiples, annotated image rows.

Surface voice: field-note panels with subtle paper contrast, measured borders, and map-like structure.

Avoid: glossy SaaS cards, generic green sustainability styling, unsupported map decoration.

## Preset 4 · Carbon Night

Palette basis: Carbon-like charcoal neutrals, cyan signal lines, and product-interface restraint.

Best for: engineering reviews, AI systems, security, developer tools, technical architecture.

```css
--paper: #161616;
--ink: #f4f4f4;
--muted: #c6c6c6;
--accent: #33b1ff;
--accent-2: #8a3ffc;
--accent-soft: rgba(51,177,255,0.16);
--font-display: "IBM Plex Sans Condensed", "Avenir Next Condensed", "Arial Narrow", Bahnschrift, "Segoe UI", Arial, "Microsoft YaHei UI", "Noto Sans SC", sans-serif;
--font-body: "IBM Plex Sans", "Avenir Next", "Segoe UI", Arial, "Microsoft YaHei UI", "Noto Sans SC", sans-serif;
--font-zh: "ZCOOL QingKe HuangYou", "Microsoft YaHei UI", SimHei, sans-serif;
--font-ja: "M PLUS 1p", "Hiragino Sans", "Yu Gothic", Meiryo, sans-serif;
--font-mono: "IBM Plex Mono", Menlo, Consolas, "Cascadia Mono", monospace;
```

Typography voice: compact technical sans, mono diagnostics, high-contrast headings.

Layout voice: architecture panels, console-like tables, dependency diagrams, failure/recovery flows.

Surface voice: technical interface panels with sharp geometry, thin signal lines, and controlled contrast.

Avoid: sci-fi glow everywhere, decorative cyber grids, tiny gray text on black.

## Preset 5 · Copper Archive

Palette basis: archival copper, bone paper, and dark umber. Built for warmth without copying magazine templates.

Best for: historical explainers, founder stories, craft, culture, long-form narrative, retrospectives.

```css
--paper: #fbf7ef;
--ink: #2a241e;
--muted: #6f6458;
--accent: #a85f2a;
--accent-2: #d5b58a;
--accent-soft: rgba(168,95,42,0.16);
--font-display: "Fraunces", Didot, Georgia, Cambria, "Noto Serif SC", SimSun, serif;
--font-body: "Source Sans 3", "Avenir Next", "Segoe UI", Arial, "Microsoft YaHei UI", "Noto Sans SC", sans-serif;
--font-zh: "Noto Serif SC", "Songti SC", SimSun, serif;
--font-ja: "Zen Old Mincho", "Sawarabi Mincho", YuMincho, serif;
--font-mono: "IBM Plex Mono", Menlo, Consolas, "Cascadia Mono", monospace;
```

Typography voice: sturdy serif display, documentary sans body, small archival labels.

Layout voice: timelines, artifact panels, quote/evidence pairing, chapter folios.

Surface voice: archival panels with warm paper contrast, firm edges, and documentary restraint.

Avoid: old-paper clichés, random sepia filters, fake stamps unless the content asks for them.

## Preset 6 · Docket Gold

Palette basis: dark ink, docket-paper gray, and one amber-gold signal color.

Best for: operations, incident review, risk, legal/business decisions, executive urgency.

```css
--paper: #fbfbf8;
--ink: #171717;
--muted: #66645e;
--accent: #b86b00;
--accent-2: #f5c542;
--accent-soft: rgba(184,107,0,0.14);
--font-display: "Archivo", "Arial Black", "Avenir Next", "Segoe UI Black", Arial, "Microsoft YaHei UI", "Noto Sans SC", sans-serif;
--font-body: "IBM Plex Sans", "Avenir Next", "Segoe UI", Arial, "Microsoft YaHei UI", "Noto Sans SC", sans-serif;
--font-zh: "ZCOOL QingKe HuangYou", "Microsoft YaHei UI", SimHei, sans-serif;
--font-ja: "M PLUS 1p", "Hiragino Sans", "Yu Gothic", Meiryo, sans-serif;
--font-mono: "IBM Plex Mono", Menlo, Consolas, "Cascadia Mono", monospace;
```

Typography voice: hard-working sans hierarchy, strong numbers, clear labels.

Layout voice: case files, ledgers, priority lanes, before/after evidence, severity bands.

Surface voice: ledger-like blocks with sturdy dividers, compact panels, and operational clarity.

Avoid: road-sign orange, hazard-strip overload, emotional alarmism.

## Preset 7 · Seagrass Lab

Palette basis: teal, mint, and cool gray from UI and environmental data palettes.

Best for: research notebooks, workshops, product discovery, science education, calm explainers.

```css
--paper: #f7fbf8;
--ink: #18312f;
--muted: #5f706d;
--accent: #0ca678;
--accent-2: #96f2d7;
--accent-3: #ced4da;
--accent-soft: rgba(12,166,120,0.14);
--font-display: "Alegreya Sans", "Avenir Next", "Segoe UI", Arial, "Microsoft YaHei UI", "Noto Sans SC", sans-serif;
--font-body: "IBM Plex Sans", "Avenir Next", "Segoe UI", Arial, "Microsoft YaHei UI", "Noto Sans SC", sans-serif;
--font-zh: "Noto Sans SC", "PingFang SC", "Microsoft YaHei UI", sans-serif;
--font-ja: "Zen Kaku Gothic New", "Hiragino Sans", "Yu Gothic", sans-serif;
--font-mono: "IBM Plex Mono", Menlo, Consolas, "Cascadia Mono", monospace;
```

Typography voice: approachable research sans, generous leading, compact labels.

Layout voice: experiment cards, method/results splits, process flows, gentle diagrams.

Surface voice: soft lab panels with light tinting, gentle separation, and approachable structure.

Avoid: wellness pastel mush, low-contrast mint text, decorative leaf motifs.

## Preset 8 · Riso Pop

Palette basis: limited spot-color print language: fluorescent pink, soy black, sky blue, and warm paper.

Best for: youth events, culture, zines, creator campaigns, community programs, music and pop-up retail.

```css
--paper: #fff6e8;
--ink: #1b1b1b;
--muted: #5e5a52;
--accent: #ff4fa3;
--accent-2: #2d7ff9;
--accent-3: #f7d046;
--accent-soft: rgba(255,79,163,0.18);
--font-display: "Bricolage Grotesque", Futura, "Arial Rounded MT Bold", "Segoe UI", Arial, "Microsoft YaHei UI", "Noto Sans SC", sans-serif;
--font-body: "Space Grotesk", "Avenir Next", "Segoe UI", Arial, "Microsoft YaHei UI", "Noto Sans SC", sans-serif;
--font-zh: "Noto Sans SC", "PingFang SC", "Microsoft YaHei UI", sans-serif;
--font-ja: "M PLUS Rounded 1c", "Hiragino Maru Gothic ProN", "Yu Gothic", sans-serif;
--font-mono: "Space Mono", Menlo, Consolas, "Cascadia Mono", monospace;
```

Typography voice: chunky, poster-like, energetic; keep labels short.

Layout voice: overprint blocks, split-color titles, sticker-like evidence labels, image-heavy spreads.

Surface voice: print-like blocks with tactile color fields, playful labels, and purposeful roughness.

Avoid: rainbow scatter, glossy gradients, making every slide equally loud.

## Preset 9 · Studio Lacquer

Palette basis: lacquer black, warm ivory, vermilion, and muted jade.

Best for: premium hospitality, fashion, gallery, craft, evening events, high-touch commercial proposals.

```css
--paper: #11100e;
--ink: #f7efe2;
--muted: #b9ad9b;
--accent: #d64224;
--accent-2: #6f8f72;
--accent-3: #c9a35a;
--accent-soft: rgba(214,66,36,0.18);
--font-display: "Cormorant Garamond", Didot, Baskerville, Georgia, Cambria, "Noto Serif SC", SimSun, serif;
--font-body: "IBM Plex Sans", "Avenir Next", "Segoe UI", Arial, "Microsoft YaHei UI", "Noto Sans SC", sans-serif;
--font-zh: "Noto Serif SC", "Songti SC", SimSun, serif;
--font-ja: "Noto Serif JP", "Hiragino Mincho ProN", YuMincho, serif;
--font-mono: "IBM Plex Mono", Menlo, Consolas, "Cascadia Mono", monospace;
```

Typography voice: refined serif titles, quiet sans body, precise captions.

Layout voice: dark gallery surfaces, vertical rules, full-bleed image panels, restrained accent seals.

Surface voice: polished gallery panels with elegant edges, minimal ornament, and high material contrast.

Avoid: botanical motifs by default, neon nightlife treatment, gold overload.

## Preset 10 · Market Quilt

Palette basis: produce-market colors, textile blocks, and friendly high-contrast neutrals.

Best for: food, beverage, family activities, neighborhood festivals, local business, approachable lifestyle decks.

```css
--paper: #fffaf1;
--ink: #1f1b16;
--muted: #625a50;
--accent: #e85d3f;
--accent-2: #2a9d8f;
--accent-3: #f4a261;
--accent-4: #e9c46a;
--accent-soft: rgba(232,93,63,0.16);
--font-display: "Nunito Sans", "Arial Rounded MT Bold", "Cooper Black", Georgia, Cambria, "Noto Serif SC", SimSun, serif;
--font-body: "Nunito Sans", "Avenir Next", "Segoe UI", Arial, "Microsoft YaHei UI", "Noto Sans SC", sans-serif;
--font-zh: "Noto Sans SC", "PingFang SC", "Microsoft YaHei UI", sans-serif;
--font-ja: "M PLUS Rounded 1c", "Hiragino Maru Gothic ProN", "Yu Gothic", sans-serif;
--font-mono: "IBM Plex Mono", Menlo, Consolas, "Cascadia Mono", monospace;
```

Typography voice: friendly display type with clear body copy.

Layout voice: quilt-like blocks, schedule tiles, audience cards, image/contact-sheet sections.

Surface voice: friendly block surfaces with warmer edges, approachable tiles, and handmade order.

Avoid: childish icons, low-contrast pastel-on-pastel, rounded app chrome as the main style.

## Preset 11 · Night Arcade

Palette basis: midnight navy, arcade cyan, magenta, and acid yellow with restrained glow.

Best for: electronic music, gaming, digital art, future-facing consumer tech, late-night event decks.

```css
--paper: #080b18;
--ink: #f5fbff;
--muted: rgba(245,251,255,0.74);
--accent: #00d9ff;
--accent-2: #ff3d8b;
--accent-3: #d9ff00;
--accent-soft: rgba(0,217,255,0.18);
--font-display: "Rajdhani", "Avenir Next Condensed", "Arial Narrow", Bahnschrift, "Segoe UI", Arial, "Microsoft YaHei UI", "Noto Sans SC", sans-serif;
--font-body: "Sora", "Avenir Next", "Segoe UI", Arial, "Microsoft YaHei UI", "Noto Sans SC", sans-serif;
--font-zh: "ZCOOL QingKe HuangYou", "Microsoft YaHei UI", SimHei, sans-serif;
--font-ja: "M PLUS 1p", "Hiragino Sans", "Yu Gothic", Meiryo, sans-serif;
--font-mono: "Space Mono", Menlo, Consolas, "Cascadia Mono", monospace;
```

Typography voice: angular display headings, crisp body, mono technical labels.

Layout voice: scoreboards, signal bars, event grids, sharp neon rules, controlled dark surfaces.

Surface voice: signal panels with hard edges, luminous rules, and dashboard-like contrast.

Avoid: purple-gradient-on-white, illegible glow text, fake dashboard clutter.

## Preset 12 · Coral Bulletin

Palette basis: coral, slate, off-white, and sea-glass blue for cheerful but readable announcement decks.

Best for: launches, newsletters, internal campaigns, community updates, optimistic retrospectives.

```css
--paper: #fff9f5;
--ink: #263238;
--muted: #66757b;
--accent: #ff6f61;
--accent-2: #4fb3bf;
--accent-3: #ffd166;
--accent-soft: rgba(255,111,97,0.16);
--font-display: "DM Serif Display", Georgia, Cambria, "Noto Serif SC", SimSun, serif;
--font-body: "DM Sans", "Avenir Next", "Segoe UI", Arial, "Microsoft YaHei UI", "Noto Sans SC", sans-serif;
--font-zh: "Noto Serif SC", "Songti SC", SimSun, serif;
--font-ja: "Noto Serif JP", "Hiragino Mincho ProN", YuMincho, serif;
--font-mono: "IBM Plex Mono", Menlo, Consolas, "Cascadia Mono", monospace;
```

Typography voice: editorial announcement title, crisp sans support, cheerful labels.

Layout voice: bulletin boards, launch checklists, announcement strips, simple comparison cards.

Surface voice: bulletin panels with light structure, friendly cards, and crisp readable edges.

Avoid: candy-store saturation, using all support colors on every slide, decorative confetti.

## Selection Guide

| Content / mood | Recommended preset |
|---|---|
| Default strategy, culture, narrative | Ultramarine Ledger |
| Policy, governance, healthcare, education admin | Civic Slate |
| Geography, travel, history, environmental topics | Cartographer Field |
| Engineering, AI systems, security, developer tools | Carbon Night |
| Historical narrative, craft, retrospective | Copper Archive |
| Operations, risk, incident review, hard decisions | Docket Gold |
| General KPI, spreadsheet, campaign, or business performance analysis | Civic Slate, Seagrass Lab, Ultramarine Ledger |
| Research notebook, workshop, science education | Seagrass Lab |
| Youth culture, music, zines, creator campaign | Riso Pop |
| Premium hospitality, fashion, gallery, evening event | Studio Lacquer |
| Food, local business, family activity, neighborhood festival | Market Quilt |
| Gaming, digital art, electronic music, future tech | Night Arcade |
| Launch, newsletter, internal campaign, optimistic update | Coral Bulletin |
| Brand, product, company, sponsor-heavy deck | Official brand color + closest registered preset |

## Implementation Notes

- Preserve the waterfall frame variables even when a preset uses a strong slide color.
- For dark presets, set `.slide-frame.theme-ink` or selected slide surfaces to the preset's dark `--paper`; do not change the global page background.
- For grid presets, put grids inside `.slide-frame` or `.slide-stage`, not on `body`.
- For bold presets, use registered colors as semantic roles. For example, tabs may represent sections, signal colors may mark one call to action, and gold may mark key claims. Avoid arbitrary decorative color scatter.
- When using remote fonts, include graceful fallbacks. Do not make the deck unreadable if fonts fail to load.
- If the user does not pick a style, choose Ultramarine Ledger for general topics, Civic Slate for policy/public-sector topics, Cartographer Field for geography/history/environment, Carbon Night for engineering/AI/security, Docket Gold for operational risk, and Market Quilt for food/community/lifestyle proposals. For high-energy cultural events, choose Riso Pop or Night Arcade; for premium evening culture, choose Studio Lacquer; for cheerful launches or internal campaigns, choose Coral Bulletin.
