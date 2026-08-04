"""Per-format renderers for the document generation subsystem.

Each renderer consumes a validated :class:`~reactor_tool.docgen.model.DocumentSpec`
or :class:`~reactor_tool.docgen.model.DeckSpec` plus a resolved theme and writes a
file, returning a result dict with stats and font/embedding warnings.

Imports are lazy so a missing optional dependency for one format never breaks
the others.
"""

from typing import Any

__all__ = ["render_pdf", "render_docx", "render_pptx", "render_html", "render_markdown"]


def __getattr__(name: str) -> Any:
    # 按格式延迟导入，某个可选渲染依赖缺失时不影响其它格式继续启动。
    if name == "render_pdf":
        from reactor_tool.docgen.renderers.pdf import render_pdf

        return render_pdf
    if name == "render_docx":
        from reactor_tool.docgen.renderers.docx import render_docx

        return render_docx
    if name == "render_pptx":
        from reactor_tool.docgen.renderers.pptx import render_pptx

        return render_pptx
    if name in ("render_html", "render_markdown"):
        from reactor_tool.docgen.renderers import html as _html

        return getattr(_html, name)
    raise AttributeError(name)
