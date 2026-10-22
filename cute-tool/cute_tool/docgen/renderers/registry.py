# -*- coding: utf-8 -*-
"""文档渲染器注册表。

四个格式 renderer 的签名本来就是统一的 ``(spec, output_path) -> dict``，
但调用方（``tool/docgen/service.py``）把"格式 -> renderer"的映射写了两遍
四分支 if 链。这里把它收敛成一张表，新增格式只需登记一行。

renderer 按需导入：某个可选渲染依赖缺失时，不影响其它格式继续工作。
"""

from pathlib import Path
from typing import Any, Callable, Dict

Renderer = Callable[[Any, Path], Dict[str, Any]]

# 格式名 -> (模块路径, 函数名)。用名字而非对象，避免导入期拉起全部渲染依赖。
_RENDERER_SPECS: Dict[str, tuple[str, str]] = {
    "pdf": ("cute_tool.docgen.renderers.pdf", "render_pdf"),
    "docx": ("cute_tool.docgen.renderers.docx", "render_docx"),
    "html": ("cute_tool.docgen.renderers.html", "render_html"),
    "markdown": ("cute_tool.docgen.renderers.markdown", "render_markdown"),
    "md": ("cute_tool.docgen.renderers.markdown", "render_markdown"),
    # pptx 吃 DeckSpec 而不是 DocumentSpec（见 generate_slides），签名形状一致
    # 但入参类型不同，所以只在需要按名字取用时登记，不参与 DocumentSpec 分派。
    "pptx": ("cute_tool.docgen.renderers.pptx", "render_pptx"),
}

# 支持 DocumentSpec 渲染的格式（供错误提示使用；md 是 markdown 的别名，不重复列出）。
SUPPORTED_FORMATS = ("pdf", "docx", "html", "markdown")


def get_renderer(fmt: str) -> Renderer:
    """按格式名取 renderer；未登记时抛 ValueError 而不是悄悄回退到 markdown。

    原先的 if 链以 ``return render_markdown(...)`` 兜底，意味着写错格式名会
    静默产出 markdown —— 调用方很难发现。这里改成显式报错。
    """
    key = (fmt or "").strip().lower()
    if key not in _RENDERER_SPECS:
        raise ValueError(
            f"Unsupported document format: {fmt!r}. Supported: {list(SUPPORTED_FORMATS)}"
        )
    module_name, func_name = _RENDERER_SPECS[key]
    module = __import__(module_name, fromlist=[func_name])
    return getattr(module, func_name)
