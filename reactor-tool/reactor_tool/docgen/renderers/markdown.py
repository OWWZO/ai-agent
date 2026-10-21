# -*- coding: utf-8 -*-
"""Markdown renderer.

原先放在 ``renderers/html.py`` 里，但除"输出文件是文本"外与 HTML 渲染毫无关系。
独立成模块后，``renderers/registry.py`` 可以把 markdown 当作一等格式登记。
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

from reactor_tool.docgen.model import (
    Block,
    CalloutBlock,
    ChartBlock,
    ChecklistBlock,
    ChecklistItem,
    CodeBlock,
    ColumnsBlock,
    DefinitionListBlock,
    DividerBlock,
    DocumentSpec,
    FootnotesBlock,
    HeadingBlock,
    ImageBlock,
    ListBlock,
    ListItem,
    MathBlock,
    MetricsBlock,
    PageBreakBlock,
    ParagraphBlock,
    QuoteBlock,
    TableBlock,
    TocBlock,
)
from reactor_tool.docgen.tables import process_table


_GFM_STATUS = {"completed": "x", "in_progress": "~", "blocked": "!", "skipped": "-"}


def _checklist_markdown(block: ChecklistBlock) -> list[str]:
    """Emit a checklist as GFM task lists (round-trippable)."""
    lines: list[str] = []
    if block.title:
        lines += [f"**{block.title}**", ""]
    if block.description:
        lines += [block.description, ""]

    def _emit(item: ChecklistItem, depth: int) -> None:
        mark = _GFM_STATUS.get(item.status, " ")
        pad = "  " * depth
        extra = []
        if item.priority:
            extra.append(f"[{item.priority.upper()}]")
        if item.assignee:
            extra.append(f"@{item.assignee}")
        if item.due_date:
            extra.append(f"({item.due_date})")
        suffix = (" " + " ".join(extra)) if extra else ""
        lines.append(f"{pad}- [{mark}] {item.text}{suffix}")
        if item.notes:
            lines.append(f"{pad}  - _{item.notes}_")
        for sub in item.sub_items:
            _emit(sub, depth + 1)

    for group in block.normalized_groups():
        if group.name:
            lines += [f"**{group.name}**", ""]
        for item in group.items:
            _emit(item, 0)
        lines.append("")
    return lines


def _iter_flat_blocks(blocks: list[Block]) -> Any:
    """Yield blocks with column layouts flattened to sequential content."""
    for block in blocks:
        if isinstance(block, ColumnsBlock):
            for col in block.columns:
                yield from _iter_flat_blocks(col)
        else:
            yield block


def render_markdown(spec: DocumentSpec, output_path: Path) -> dict[str, Any]:
    """Render a document spec back to a clean markdown file."""
    lines: list[str] = []
    stats = {"headings": 0, "paragraphs": 0, "tables": 0}

    if spec.title:
        lines += [f"# {spec.title}", ""]
        if spec.subtitle:
            lines += [f"*{spec.subtitle}*", ""]
        meta_bits = "  ·  ".join(b for b in (spec.author, spec.date) if b)
        if meta_bits:
            lines += [meta_bits, ""]

    for block in _iter_flat_blocks(spec.blocks):
        if isinstance(block, HeadingBlock):
            stats["headings"] += 1
            lines += [f"{'#' * min(block.level + 1, 6)} {block.text}", ""]
        elif isinstance(block, ParagraphBlock):
            stats["paragraphs"] += 1
            lines += [block.text, ""]
        elif isinstance(block, ListBlock):
            lines += _markdown_list_lines(block) + [""]
        elif isinstance(block, TableBlock):
            stats["tables"] += 1
            pt = process_table(block)
            markers = {"left": " --- ", "center": " :---: ", "right": " ---: "}
            if pt.header:
                lines.append("| " + " | ".join(cl.text for cl in pt.header) + " |")
                lines.append(
                    "|" + "|".join(markers[col.align] for col in pt.columns) + "|"
                )
            for row in pt.body:
                lines.append("| " + " | ".join(cl.text for cl in row) + " |")
            lines.append("")
        elif isinstance(block, CodeBlock):
            lines += [f"```{block.language or ''}", block.code, "```", ""]
        elif isinstance(block, QuoteBlock):
            lines += [f"> {block.text}", ""]
        elif isinstance(block, CalloutBlock):
            title = f" {block.title}" if block.title else ""
            lines += [f"::: {block.variant}{title}", block.text, ":::", ""]
        elif isinstance(block, ChartBlock):
            payload = block.model_dump_json(exclude_none=True, exclude_defaults=True)
            lines += ["```chart", payload, "```", ""]
        elif isinstance(block, MetricsBlock):
            payload = block.model_dump_json(exclude_none=True)
            lines += ["```metrics", payload, "```", ""]
        elif isinstance(block, ImageBlock):
            src = block.path or block.url or ""
            lines += [f"![{block.caption or ''}]({src})", ""]
        elif isinstance(block, ChecklistBlock):
            lines += _checklist_markdown(block)
        elif isinstance(block, MathBlock):
            lines += ["$$", block.latex, "$$", ""]
        elif isinstance(block, DefinitionListBlock):
            for item in block.items:
                lines.append(item.term)
                lines.extend(f": {d}" for d in item.definitions)
                lines.append("")
        elif isinstance(block, FootnotesBlock):
            lines += [f"[^{item.label}]: {item.text}" for item in block.items] + [""]
        elif isinstance(block, DividerBlock):
            lines += ["---", ""]
        elif isinstance(block, PageBreakBlock):
            lines += ["\\newpage", ""]
        elif isinstance(block, TocBlock):
            lines += ["[TOC]", ""]

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")
    return {
        "success": True,
        "output_path": str(output_path),
        "format": "markdown",
        "file_size_bytes": output_path.stat().st_size,
        "content_stats": stats,
        "warnings": [],
    }


def _markdown_list_lines(block: ListBlock) -> list[str]:
    lines: list[str] = []

    def _walk(items: list[ListItem], depth: int) -> None:
        for idx, item in enumerate(items):
            indent = "  " * depth
            marker = f"{idx + 1}." if block.ordered and depth == 0 else "-"
            check = ""
            if item.checked is True:
                check = "[x] "
            elif item.checked is False:
                check = "[ ] "
            lines.append(f"{indent}{marker} {check}{item.text}")
            if item.children:
                _walk(item.children, depth + 1)

    _walk(block.items, 0)
    return lines
