# -*- coding: utf-8 -*-
"""Renderer 之间共用的纯函数。

pdf / docx / html 三个 renderer 各自复制了一份完全相同的 ``_looks_chinese``，
这里收敛成单一来源。渲染主体仍留在各自模块（它们对"如何画"的假设差别很大，
不适合强行合并）；各 renderer 的颜色解析输出类型不同（str / Color / RGBColor），
也没有合并价值。
"""

from typing import Any


def looks_chinese(spec: Any) -> bool:
    """粗判文档是否以中文为主，用于选择目录标题与 lang 属性。

    只取标题 + 前 8 个带 ``text`` 的块作为样本：整篇扫描对长文档没有额外收益，
    而 CJK 字体选择只需要一个方向性判断。
    """
    blocks = getattr(spec, "blocks", []) or []
    sample = (getattr(spec, "title", "") or "") + "".join(
        getattr(block, "text", "") for block in blocks[:8] if hasattr(block, "text")
    )
    return any("一" <= ch <= "鿿" for ch in sample)
