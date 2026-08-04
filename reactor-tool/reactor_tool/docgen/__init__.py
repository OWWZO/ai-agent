"""LeAgent document generation subsystem.

One document model, one font pipeline, one theme system, N renderers:

- :mod:`reactor_tool.docgen.fonts` — guaranteed pan-Unicode font pipeline
  (env override → managed download dir → system scan → auto-download).
- :mod:`reactor_tool.docgen.model` — typed Document / Deck IR (Pydantic v2).
- :mod:`reactor_tool.docgen.markdown` — markdown → IR parser (markdown-it-py:
  GFM, math, footnotes, definition lists, front matter, callouts).
- :mod:`reactor_tool.docgen.mathtext` — LaTeX math layout (matplotlib mathtext):
  native vector geometry (PDF) + raster fallback + Unicode fallback.
- :mod:`reactor_tool.docgen.omml` — LaTeX → OMML for native, editable Word /
  PowerPoint equations (no rasterisation).
- :mod:`reactor_tool.docgen.themes` — named themes (typography, palette, page geometry).
- :mod:`reactor_tool.docgen.theming` — theme generation (brand seed -> palette),
  WCAG contrast lint, custom theme store.
- :mod:`reactor_tool.docgen.templates` — reusable parameterized document/deck
  templates (Jinja2 placeholders, validated against the IR).
- :mod:`reactor_tool.docgen.tables` — shared table engine (normalization, column
  type inference, number polish, total-row/delta semantics, style contract).
- :mod:`reactor_tool.docgen.slides` — slide composition engine (deck type scale,
  layout regions, multi-level bullet plans, text autofit).
- :mod:`reactor_tool.docgen.charts` — chart blocks rendered to PNG via matplotlib.
- :mod:`reactor_tool.docgen.renderers` — PDF / DOCX / PPTX / HTML / Markdown renderers.

Agent-facing tools (``document_generate`` / ``slides_generate``) live in
``leagent.tools.gen`` and drive this package.
"""

# 统一中间表示连接解析、主题、字体和多格式渲染；工具入口不应绕过本包直接拼接文件。
from reactor_tool.docgen.checklist import (
    build_checklist_block,
    checklist_stats,
    checklist_to_dict,
)
from reactor_tool.docgen.fonts import FontManager, ResolvedFonts, get_font_manager
from reactor_tool.docgen.mathtext import (
    MathVector,
    latex_lines,
    latex_to_unicode,
    math_vector_path,
    render_math_png,
)
from reactor_tool.docgen.model import DeckSpec, DocumentSpec, SlideSpec
from reactor_tool.docgen.omml import latex_to_omml_element, latex_to_omml_xml
from reactor_tool.docgen.slides import (
    DeckTypography,
    SlideGeometry,
    fit_body_size,
    flatten_body,
)
from reactor_tool.docgen.tables import (
    ProcessedTable,
    TableStyleSpec,
    process_table,
    resolve_table_style,
)
from reactor_tool.docgen.templates import (
    DocTemplate,
    delete_template,
    list_templates,
    load_template,
    render_template,
    save_template,
)
from reactor_tool.docgen.themes import Theme, get_theme, list_theme_names
from reactor_tool.docgen.theming import (
    derive_theme_payload,
    lint_theme,
    save_custom_theme,
)

__all__ = [
    "FontManager",
    "ResolvedFonts",
    "get_font_manager",
    "DocumentSpec",
    "DeckSpec",
    "SlideSpec",
    "render_math_png",
    "math_vector_path",
    "MathVector",
    "latex_lines",
    "latex_to_unicode",
    "latex_to_omml_xml",
    "latex_to_omml_element",
    "DeckTypography",
    "SlideGeometry",
    "fit_body_size",
    "flatten_body",
    "ProcessedTable",
    "TableStyleSpec",
    "process_table",
    "resolve_table_style",
    "DocTemplate",
    "save_template",
    "load_template",
    "list_templates",
    "delete_template",
    "render_template",
    "Theme",
    "get_theme",
    "list_theme_names",
    "derive_theme_payload",
    "lint_theme",
    "save_custom_theme",
    "checklist_stats",
    "checklist_to_dict",
    "build_checklist_block",
]
