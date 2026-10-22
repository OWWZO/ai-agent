"""Document generation subsystem.

One document model, one font pipeline, one theme system, N renderers:

- :mod:`cute_tool.docgen.fonts` — guaranteed pan-Unicode font pipeline
  (env override → managed download dir → system scan → auto-download).
- :mod:`cute_tool.docgen.model` — typed Document / Deck IR (Pydantic v2).
- :mod:`cute_tool.docgen.markdown` — markdown → IR parser (markdown-it-py:
  GFM, math, footnotes, definition lists, front matter, callouts).
- :mod:`cute_tool.docgen.mathtext` — LaTeX math layout (matplotlib mathtext):
  native vector geometry (PDF) + raster fallback + Unicode fallback.
- :mod:`cute_tool.docgen.omml` — LaTeX → OMML for native, editable Word /
  PowerPoint equations (no rasterisation).
- :mod:`cute_tool.docgen.themes` — named themes (typography, palette, page geometry).
- :mod:`cute_tool.docgen.theming` — theme generation (brand seed -> palette),
  WCAG contrast lint, custom theme store.
- :mod:`cute_tool.docgen.templates` — reusable parameterized document/deck
  templates (Jinja2 placeholders, validated against the IR).
- :mod:`cute_tool.docgen.tables` — shared table engine (normalization, column
  type inference, number polish, total-row/delta semantics, style contract).
- :mod:`cute_tool.docgen.slides` — slide composition engine (deck type scale,
  layout regions, multi-level bullet plans, text autofit).
- :mod:`cute_tool.docgen.charts` — chart blocks rendered to PNG via matplotlib.
- :mod:`cute_tool.docgen.renderers` — PDF / DOCX / PPTX / HTML / Markdown renderers.

Agent-facing tools (``document_generate`` / ``slides_generate``) drive this package.
"""
