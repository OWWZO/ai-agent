# -*- coding: utf-8 -*-
"""Per-format renderers for the document generation subsystem.

Each renderer consumes a validated :class:`~cute_tool.docgen.model.DocumentSpec`
or :class:`~cute_tool.docgen.model.DeckSpec` plus a resolved theme and writes a
file, returning a result dict with stats and font/embedding warnings.

用 :func:`cute_tool.docgen.renderers.registry.get_renderer` 按格式名取实现；
本模块的 ``__getattr__`` 只为既有 ``from ...renderers import render_pdf`` 调用方式保留。
"""

from typing import Any

__all__ = ["render_pdf", "render_docx", "render_pptx", "render_html", "render_markdown"]


def __getattr__(name: str) -> Any:
    # 按格式延迟导入，某个可选渲染依赖缺失时不影响其它格式继续启动。
    from cute_tool.docgen.renderers.registry import get_renderer

    if name in __all__:
        return get_renderer(name.removeprefix("render_"))
    raise AttributeError(name)
