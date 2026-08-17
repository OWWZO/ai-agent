---
name: report-page
description: Use when the user wants to create a source-backed report, document, memo, research note, operating update, or data interpretation page delivered as editable static HTML with a clean document reading experience and optional ECharts figures.
---

# Report Page

## Overview

Build a report or document that reads like the kind of deliverable the user actually asked for: clear narrative sections, calm typography, useful blocks, optional source-backed evidence, optional quantitative figures rendered through ECharts, tables, images, diagrams, captions, and a dynamic side table of contents. Static HTML is the delivery format, not the purpose of the skill.

This skill is for document-style outputs across many domains: research reports, strategy memos, company/market briefs, technical or product notes, operating updates, data interpretation pages, analytical readouts, and long-form source-backed writing. It is especially useful when the user needs evidence, narrative interpretation, and readable charts/tables in one self-contained document. It is not primarily a dashboard skill, slide skill, landing-page builder, marketing site builder, or app framework.

Default to a hand-authored static `index.html` with lightweight external assets: `assets/doc.css`, `assets/doc.js`, `assets/mermaid.min.js` when Mermaid diagrams are present, and `assets/echarts.min.js` only when quantitative charts are present. Do not create a Python generator. The document should be easy for an AI or human to edit directly.

Keep the mental model simple: the page is the source of truth. Use HTML document blocks, not a hidden generator, CMS schema, or app runtime.

The document components are tools, not a layout recipe. Do not default to summary -> metrics -> chart -> table, or any other fixed sequence. First infer the content genre and the reader's expected reading experience, then choose the structure that fits.

Pay special attention to the opening of the page. The shared stylesheet exposes convenient header, callout, and metric components, but those are not a default opening package. A page should not feel like it was assembled by stacking the available header widgets. Choose an opening shape deliberately from the content: a direct memo lead, a dated briefing note, a narrative scene-setter, a decision-first paragraph, a source note, a visual lead, or another genre-appropriate start. Add subtitles, thesis callouts, or metric strips only when they serve the reader's first task. Put metadata such as dates, source scope, participants, report type, and caveats into natural prose, captions, notes, or source sections rather than pill-shaped header badges.

## Workflow

1. Interpret the user's goal first. Infer the likely reader, page purpose, desired depth, tone, and the interpretation or explanation they probably want. Also infer the expected content genre, such as newsletter, weekly digest, memo, research note, case study, data report, analytical readout, operating update, technical note, or dashboard-like review. Let that genre shape the page.
2. Read `references/output-layout.md`, create one folder for the page, and keep all page-specific files inside that folder.
3. Read `references/story-planning.md`, then draft a page spine that fits the inferred genre: context, core question or promise, main thesis or editorial angle when useful, evidence path, useful visuals, and limits. Do not force a thesis/metric/chart structure when the expected page is a reader-facing digest or newsletter.
4. Choose the output path before writing. The output is a static hand-authored HTML document.
5. Inspect any provided input data, files, links, screenshots, or research sources. Confirm source URLs/files, dates, units, and material caveats when they matter.
6. Put reviewed source data in `page-slug/data/` and page-specific images in `page-slug/images/` when practical. For news, weekly digests, entertainment roundups, product briefs, source-backed pages, and repeated item lists such as watch/read/buy cards, actively look for real source-backed images instead of defaulting to text-only blocks. Use article images, official pages, Open Graph/Twitter metadata, user-provided assets, or another clearly reviewed source. Prefer downloading reliable images locally into `images/`; if no reliable image is available, omit the image rather than using logos, flags, generated placeholders, or decorative substitutes.
7. Read `references/page-structure.md` before changing page blocks or document markup.
8. Read `references/media-and-diagrams.md` before adding diagrams, workflow visuals, architecture maps, Mermaid blocks, images, or other non-chart figures.
9. Read `references/data-and-charts.md` before changing quantitative chart data, inline JSON payloads, ECharts options, or source notes.
10. Read `references/chart-selection.md` before choosing chart families, adding new ECharts option builders, or deciding that a table/prose block is clearer than a chart.
11. Read `references/visual-interactions.md` before changing CSS, table of contents behavior, chart menus, Mermaid behavior, or lightweight document interactions.
12. Make an explicit page plan before writing blocks. Decide the opening shape separately from the rest of the document: what the first screen should make the reader understand, what context truly belongs there, and what can move into the first section, notes, sources, or later evidence. Then decide each section's job, evidence, depth, and natural form. Use block types such as `text`, `callout`, `metric`, `chart`, `table`, `image`, `diagram`, `note`, or `sources` only when they are the right form for the content, not because the component exists.
13. Hand-author `index.html` from the page plan. The first line must be `<!-- Generated by Trae Work -->`. Use document components such as `doc-block`, `doc-callout`, `doc-metrics`, `doc-chart`, `doc-table`, `doc-diagram`, `doc-note`, and `doc-sources`. Keep prose, tables, sources, Mermaid source, and chart JSON directly readable in the HTML.
14. Copy `assets/doc.css` and `assets/doc.js` into `page-slug/assets/`. Copy `assets/mermaid.min.js` whenever the page contains Mermaid diagrams. Copy `assets/echarts.min.js` whenever the page contains quantitative chart figures.
15. For Mermaid diagram blocks, keep the Mermaid source in a visible `<pre class="mermaid">` block inside a `doc-diagram` figure. Use Mermaid for workflow, architecture, sequence, state, decision, taxonomy, and dependency diagrams when the source notation is clearer and easier to maintain than hand-authored SVG.
16. For quantitative chart blocks, keep chart data close to the chart as `<script type="application/json">` blocks or simple local JSON files. Add only the small page-specific chart option logic needed in `doc.js` or a tiny local page script.
17. Use ECharts for quantitative chart blocks. Do not implement analytical charts as static SVG, CSS-only bars, canvas drawings, or image snapshots unless the user explicitly asks for a static/non-interactive figure.
18. Keep the visual and interaction contract intact unless the user explicitly asks to redesign the document system.
19. Produce `page-slug/index.html`. Do not create generator files or generator runtime files. Run static sanity checks for links, local assets, headings, placeholder text, source dates, Mermaid rendering, and JSON chart payloads.

## Page Contract

Read `references/page-structure.md` when planning sections, composing blocks, or changing the rendered document DOM.

## Story Contract

Read `references/story-planning.md` before deciding page depth, story spine, section order, or evidence mix.

## Block Contract

The canonical document components are `text`, `callout`, `metric`, `chart`, `table`, `image`, `diagram`, `note`, and `sources`. They are available building blocks, not required sections. In static pages, express selected components directly in HTML using stable classes such as `doc-block`, `doc-callout`, `doc-metrics`, `doc-chart`, `doc-table`, `doc-diagram`, `doc-note`, and `doc-sources`. See `references/page-structure.md` for the full contract.

## Template Architecture

The default architecture is static:

- `index.html`: the editable page body, including prose, tables, sources, and nearby chart JSON when charts exist.
- `assets/doc.css`: shared document typography, spacing, Notion-like block styling, chart frames, tables, notes, and responsive behavior.
- `assets/doc.js`: shared table-of-contents behavior, Mermaid initialization, ECharts initialization, hover chart menus, resize handling, and small document enhancements.
- `assets/mermaid.min.js`: required local Mermaid runtime when Mermaid diagrams are present.
- `assets/echarts.min.js`: required local ECharts runtime when quantitative charts are present.

Avoid hiding page prose inside Python strings, JSON-only block payloads, or generated templates. The source should look like a document.

Keep `assets/echarts.min.js` bundled or otherwise provide a vetted local ECharts build. Prefer external local asset files over inlining ECharts into `index.html`, so the document remains readable and editable.

Keep `assets/mermaid.min.js` bundled when Mermaid diagrams are used. Prefer local Mermaid over remote CDN scripts so the page still works from `file://` and remains a self-contained deliverable folder. Do not hide Mermaid source in a generator or external-only file; the visible page source should stay easy to edit.

Quantitative chart blocks must render through ECharts by default. Static SVG/HTML/CSS charts are not the default page path; reserve them for explicit user requests, non-quantitative diagrams, or emergency fallback when ECharts cannot run and the fallback is clearly documented.

## Output Layout

Read `references/output-layout.md` before creating files. Each page should have its own folder and should always use `index.html` as the main deliverable. `assets/doc.css` and `assets/doc.js` are the default shared assets. `assets/mermaid.min.js` appears when Mermaid diagrams are used. `assets/echarts.min.js` appears when quantitative charts are used.

## Visual Contract

Read `references/visual-interactions.md` before changing CSS, table of contents behavior, hover controls, chart menus, or mobile layout.

## Data And Chart Contract

Read `references/data-and-charts.md` before changing chart data, inline JSON payloads, chart option builders, source notes, or current-information assumptions.

## Chart Selection Contract

Read `references/chart-selection.md` before choosing chart families or adding new ECharts chart builders. Prefer the simplest form that serves the reader's comparison task; use prose or tables when a chart would be decorative or underpowered.

## Output Standard

The default deliverables are:

- one local HTML page opened from `index.html`;
- optional local assets in `images/` or `data/`;

All page files should live inside the page's own folder, not in the workspace root.
In the final response, include the page `index.html` path with a `computer://` prefix so Trae can render it.

Do not create alternate export formats, dashboard artifacts, hosted pages, or app scaffolds unless the user explicitly asks for them.

## Assets

- `assets/doc.css`: default static document styling for editable HTML pages.
- `assets/doc.js`: default static document behavior for TOC, Mermaid initialization, chart menus, ECharts initialization, and resize.
- `assets/mermaid.min.js`: bundled Mermaid runtime for local/offline workflow and architecture diagram rendering.
- `assets/echarts.min.js`: bundled ECharts runtime for local/offline quantitative chart rendering.
- `references/output-layout.md`: one-page-one-folder deliverable layout and naming rules.
- `references/story-planning.md`: reader intent, depth mode, story spine, and evidence mix rules.
- `references/page-structure.md`: document structure, block model, and template layer contract.
- `references/media-and-diagrams.md`: image, diagram, Mermaid, and visual evidence rules.
- `references/data-and-charts.md`: source discipline, payload requirements, and chart authoring rules.
- `references/chart-selection.md`: chart family choice, sufficiency rules, and encoding guidance.
- `references/visual-interactions.md`: styling, TOC, chart controls, and lightweight document behavior.
- `agents/openai.yaml`: optional model/runtime configuration for this skill.

## Final Checks

Before responding to the user:

- Confirm the chosen output path matches the task: hand-authored static HTML.
- Confirm the page output lives in its own folder, with `index.html` as the main page file.
- Confirm `index.html` starts with `<!-- Generated by Trae Work -->` on line 1.
- Confirm the final response includes the page `index.html` path with a `computer://` prefix.
- Check that `assets/doc.css` and `assets/doc.js` exist, `assets/mermaid.min.js` exists when Mermaid diagrams are present, `assets/echarts.min.js` exists when quantitative charts are present, chart JSON parses, local links/images resolve, headings populate the TOC, and no placeholder/stale sample text remains.
- Confirm visible output uses document structure, not card-feed, dashboard, slide, or app abstractions.
- Search for placeholder text, stale sample titles, stale dates, and visible debug labels.
- Confirm the page has a clear story spine tailored to the user's request and expected depth, not just formatted sections.
- Confirm the page's structure matches the inferred content genre and reader expectation. A newsletter, weekly digest, research memo, technical note, and data report should not all use the same opening pattern or evidence layout.
- Confirm the first screen feels authored for this document, not assembled from a reusable header package. If it uses a subtitle, callout, or metric strip near the top, each one should have a specific reader-facing job that could not be handled better as prose, a source note, or a later section. Confirm metadata is not presented as decorative header badges.
- Confirm the opening section is not mechanically named `Executive Summary` or `导读` unless that label is genuinely right for the reader and page type.
- Confirm the side table of contents is generated from document headings, not hard-coded titles.
- Confirm KPI strips are sized to the evidence rather than forced into four fixed cards.
- Confirm metrics, charts, tables, and callouts are omitted when they make the page feel like the wrong genre.
- Confirm every quantitative chart container has a registered chart factory and initial chart type.
- Confirm chart menus use the lightweight hover style and chart type controls work when alternate chart types exist.
- Confirm analytical chart blocks use ECharts, not static SVG/HTML/CSS/image snapshots, unless the user explicitly requested that exception.
- Do not add Data Source modals; use concise visible source notes or simple source lists instead.
- Confirm charts and tables have captions with units and source context when relevant.
- Confirm image and diagram blocks, when used, have captions, source context, and readable mobile layout.
- Confirm news digests, entertainment roundups, and repeated item cards that readers expect to scan visually use source-backed local images when reliable images are available; do not leave them as text-only merely because images require extra source work.
- Confirm Mermaid diagrams, when used, keep their source readable in `index.html`, render through local `assets/mermaid.min.js`, and do not rely on remote CDN scripts unless the user explicitly accepts a network dependency.
- Confirm news images, when used, are source-backed, locally available when possible, and not decorative stand-ins. If reliable images are unavailable, confirm the page intentionally omits them.
- For current topics, confirm relevant dates and source dates are explicit and current.
- Confirm mobile and desktop layouts do not create text overlap or broken chart sizing when browser inspection is available.
