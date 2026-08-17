# Page Structure Reference

Use this reference when planning or modifying the page's section structure, block model, or rendered document markup.

## Reading Shape

The rendered output should read like a polished document page:

- a clear title and optional subtitle at the top;
- a short opening that explains why the page exists and what the reader should understand;
- H1/H2 headings, paragraphs, lists, callouts, flexible metric strips, figure captions, tables, single charts, image figures, and diagram figures as the visible structure;
- a side table of contents generated from headings;
- source notes that are simple and readable, not app-like.

Match the visible structure to the user's expected content genre. A weekly newsletter may read as a series of story items; a policy brief may lead with implications; a research note may build an argument; a data report may foreground metrics and charts. These are examples, not templates. Choose the shape that best serves the reader.

The title area is not a component checklist. A subtitle, callout, and metric strip are optional and should not appear together by habit. Treat `doc-subtitle`, `doc-callout`, and `doc-metrics` as individual tools for specific reader needs, not as a standard header stack. Avoid pill-shaped metadata badges in document openings; they tend to make pages feel templated and should be replaced with natural prose, captions, notes, or source entries. In many document genres, the strongest opening is simply a title followed by one or two well-written paragraphs.

Do not introduce visible dashboard, slide, gallery, CMS, or app-layout abstractions unless the user asks for that product shape. Wide evidence sections should be rare and should still read as figures inside a document, not as dashboard cards.

Favor paragraph-led sections over grids of cards when the task is synthesis, explanation, research, or strategy. Cards are appropriate for repeated evidence items, compact metrics, or framed figures; they should not become the default shape of the whole page. Tables, metrics, charts, and callouts should be chosen because they are the natural form for the content, not because they are available components.

Choose the block form by the reader's task. Use tables for exact lookup, comparable fields, and compact audit trails. Use cards for repeated objects that need scan-friendly but uneven context, such as different examples, resources, products, places, people, or options. Use charts for quantitative shape or comparison. Use diagrams for flow, structure, causality, or decision paths. Use prose when the important work is synthesis or judgment. Do not use a table merely because information can be arranged into rows and columns.

Use opening section names that fit the page. `Executive Summary` is appropriate for executive/board-style memos, but not a required first section. `导读` should not be repeated mechanically across Chinese pages; prefer a story-specific heading when the page is narrative.

## Block Model

Compose default pages directly in editable HTML. Use a small vocabulary of document components:

- `doc-block`: prose sections with headings, paragraphs, lists, and links.
- `doc-callout`: a highlighted note, thesis, warning, or recommendation.
- `doc-metrics`: flexible KPI/metric strip when exact headline values help.
- `doc-chart`: one ECharts quantitative figure with nearby JSON data, a chart canvas, optional hover chart-type menu, and a caption.
- `doc-table`: a readable table figure with caption or source note.
- `doc-image`: an image figure with caption and source context.
- `doc-diagram`: a diagram figure, usually Mermaid for editable workflow/architecture diagrams, or inline SVG/HTML/local image output when precise custom layout is needed.
- `doc-note`: caveats, definitions, assumptions, or methodology notes.
- `doc-sources`: a simple source list.

This vocabulary is a toolkit, not a required page skeleton. Do not force every page to include a callout, metric strip, chart, table, or image. If a component makes the page feel like the wrong genre, omit it or move it to a secondary/reference section.

For repeated object cards, use a small page-local class system when the entries need varied explanation, short links, images, caveats, or next-step context. Keep those classes scoped to the page unless the pattern has proven broadly reusable across documents. Product or resource entries may include a compact logo/favicon, name, URL, and use note when that improves recognition and navigation, but do not promote one specific entry-card layout into the shared document system by default.

When a component repeats information already visible in the title or opening paragraph, remove or relocate it. For example, meeting time, source scope, report type, and participant lists often read better as a compact sentence, a note, or a source entry than as prominent metadata badges. A callout should highlight a decision, risk, caveat, or recommendation that benefits from visual emphasis; it should not merely restate the first paragraph.

Every meaningful block should have a stable `id` so the TOC and links work. Keep prose, table rows, source links, and chart data easy to see and edit in `index.html`.

## Image Blocks

Use images only when they improve trust, recognition, or understanding. For news, weekly digests, and source-backed pages, images should be real and traceable: from the article's own metadata or body, an official page, a user-provided asset, or another clearly reviewed source. Prefer downloading reliable images into `images/` and referencing local files so the page works from `file://`.

If a source image cannot be downloaded, renders as an error page, is blocked by hotlinking, or is not clearly relevant, omit the image. Do not substitute logos, flags, generated placeholders, or decorative visual blocks merely to satisfy a visual slot. Add concise captions with source context for images that remain.

## Chart Blocks

Single-chart blocks should include:

- `data-chart`: chart family identifier;
- `data-data`: id of the local JSON payload block;
- `data-type`: initial chart type;
- `.chart-canvas`;
- optional hover `.chart-menu` with `data-chart-type` controls;
- a visible caption with units and source context when relevant.

Multi-chart comparison blocks should normally remain at document-column width and stack on mobile. Use wide layouts only when explicitly requested or when labels/axes would be unreadable otherwise.

## Diagram Blocks

Mermaid is the preferred default for editable workflow, architecture, sequence, state, decision, dependency, and taxonomy diagrams. A Mermaid diagram block should include:

- a `doc-diagram` section with a stable `id`;
- `.diagram-frame.table-scroll.mermaid-frame`;
- a visible `<pre class="mermaid">` containing the Mermaid source;
- a concise figure caption with the key takeaway;
- local `assets/mermaid.min.js` loaded before `assets/doc.js`.

Use inline SVG/HTML instead of Mermaid only when the visual needs precise custom composition, dense annotations, or shapes/layouts Mermaid cannot express cleanly.

## No Hidden Generation Layer

Default pages should not have generation layers. The source HTML is the document.

Use:

- `index.html` for content and simple local data;
- `assets/doc.css` for styling;
- `assets/doc.js` for TOC, Mermaid initialization, chart initialization, hover menus, and resize behavior;
- optional `assets/mermaid.min.js` for Mermaid diagram rendering;
- optional `assets/echarts.min.js` for quantitative chart rendering.

Do not create `report.py`, generator templates, runtime block schemas, or JSON-only content payloads unless the user explicitly asks for a build system.
