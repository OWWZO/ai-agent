# Visual Interactions Reference

Use this reference when changing the page's styling, table of contents, chart controls, or lightweight document interactions.

## Visual Feel

The default page should feel like a clean, editable document:

- centered readable text column with enough margin;
- restrained typography and spacing;
- 16px rounded containers for framed figures, tables, callouts, and compact metric groups;
- simple source notes rather than app-like inspectors;
- charts and tables rendered as document figures;
- images and diagrams sized for reading, not as decorative hero art unless the user asks for that page type;
- mobile layout that stacks cleanly and preserves text readability.

Avoid turning the page into a card feed, dashboard, slide deck, landing page, or application unless the user explicitly asks for that product shape.

## Block Behavior

`assets/doc.js` may provide:

- dynamic side table of contents from headings;
- active heading highlighting;
- Mermaid diagram initialization with the shared document theme;
- Mermaid diagram hover zoom and lightbox viewing for complex diagrams;
- chart initialization and resize handling;
- lightweight hover chart menus;
- chart-type switching when alternate chart types exist;
- small shared icon buttons for document controls such as chart menus and diagram zoom;
- small document enhancements that do not turn the page into an app.

Change `doc.js` only when the shared document interaction model changes. Keep page-specific Mermaid configuration or chart option builders in `doc.js` when generic enough to reuse, or in a tiny page-local script when the diagram/chart is specific to one page.

Do not add Data Source modals. Use visible captions, simple source lists, and nearby chart JSON instead.

## Chart Controls

Chart menus should use a quiet hover style:

- show a small three-dot button when the user hovers or focuses the chart frame;
- reveal available chart types such as `line`, `bar`, or `pie`;
- update the chart in place without shifting layout;
- omit menus when there is only one honest chart type.

Chart controls should not dominate the page. They are a convenience, not the main UI.

## Diagram Controls

Complex Mermaid diagrams should use the shared hover zoom control from `assets/doc.js` and `assets/doc.css`. The zoom trigger should stay small, use the shared icon-button style, and appear only on hover or focus. The lightbox close button should be visually light, without a heavy border or excessive padding, and should close on click, background click, or Escape. Do not add page-specific diagram zoom implementations unless the shared control cannot support the diagram.

## Visual Consistency

New charts, cards, buttons, and lightweight controls should reuse the page's existing palette, border radii, icon language, and figure treatment. Do not introduce ECharts default colors, large decorative gradients, or one-off button styles when the existing document palette can express the same meaning. Add a new color only when it encodes a real semantic distinction and remains legible with surrounding charts and captions.

## Responsive Rules

- Keep the main document column readable on desktop and mobile.
- Side navigation may hide or move on small screens.
- Chart canvases need stable dimensions, with responsive height adjustments for mobile.
- Tables should scroll horizontally only when needed.
- Mermaid diagrams should sit in a horizontally scrollable figure frame when their natural layout is wider than the document column.
- Long headings, labels, and source links should wrap without overlapping nearby content.
