# -*- coding: utf-8 -*-
"""Docread service: run LeAgent-ported document tools and normalize payloads."""
from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from loguru import logger

from reactor_tool.tool.docread._compat import ToolContext, ToolResult
from reactor_tool.tool.docread.paths import resolve_input_path, resolve_output_path
from reactor_tool.util.file_util import upload_file_by_path

_MAX_JSON_CHARS = 200_000


def _ctx(request_id: str, workspace_root: str | None = None) -> ToolContext:
    return ToolContext(
        session_id=request_id,
        request_id=request_id,
        extra={"request_id": request_id, "workspace_root": workspace_root or ""},
    )


def _truncate(data: Any, limit: int = _MAX_JSON_CHARS) -> Any:
    try:
        raw = json.dumps(data, ensure_ascii=False, default=str)
    except (TypeError, ValueError):
        raw = str(data)
    if len(raw) <= limit:
        return data
    return {
        "truncated": True,
        "original_chars": len(raw),
        "preview": raw[:limit] + f"...[truncated {len(raw) - limit} chars]",
    }


def _workspace(params: dict[str, Any]) -> str | None:
    wr = params.get("workspace_root") or params.get("workspaceRoot")
    return str(wr).strip() if wr else None


def _strip_internal(params: dict[str, Any]) -> dict[str, Any]:
    out = dict(params)
    for k in ("request_id", "requestId", "workspace_root", "workspaceRoot"):
        out.pop(k, None)
    return out


async def _maybe_upload_produced_files(
    request_id: str,
    result: dict[str, Any],
) -> dict[str, Any]:
    """If result mentions output files on disk, upload them for Java clients."""
    file_info: list[dict[str, Any]] = []
    candidates: list[str] = []
    for key in ("output_path", "outputPath", "file_path", "filePath"):
        v = result.get(key)
        if isinstance(v, str) and v and Path(v).is_file():
            candidates.append(v)
    for key in ("output_files", "outputFiles", "files", "produced_files"):
        v = result.get(key)
        if isinstance(v, list):
            for item in v:
                if isinstance(item, str) and Path(item).is_file():
                    candidates.append(item)
                elif isinstance(item, dict):
                    p = item.get("path") or item.get("file_path") or item.get("filePath")
                    if isinstance(p, str) and Path(p).is_file():
                        candidates.append(p)
    seen: set[str] = set()
    for path in candidates:
        if path in seen:
            continue
        seen.add(path)
        try:
            info = await upload_file_by_path(path, request_id=request_id)
            if info:
                file_info.append(info)
        except Exception as exc:  # noqa: BLE001
            logger.warning(f"docread upload failed path={path} err={exc}")
    if file_info:
        result = dict(result)
        result["fileInfo"] = file_info
    return result


def _ok(data: Any) -> dict[str, Any]:
    return {"success": True, "message": "ok", "data": _truncate(data)}


def _fail(message: str) -> dict[str, Any]:
    return {"success": False, "message": message, "data": None}


_WRITE_OPS = frozenset({
    "write", "create", "append", "prepend", "insert", "insert_section",
    "replace", "replace_section", "convert", "split", "merge",
    "extract_pages", "convert_to_images", "extract_images", "template",
    "format", "query",  # query may write output_path
})


def _resolve_existing(val: str, request_id: str, workspace_root: str | None) -> str:
    try:
        return str(resolve_input_path(val, request_id=request_id, workspace_root=workspace_root))
    except FileNotFoundError:
        return val


def _run_sync_tool(tool: Any, params: dict[str, Any], request_id: str) -> dict[str, Any]:
    workspace_root = _workspace(params)
    clean = _strip_internal(params)
    path_keys = set(getattr(tool, "path_params", ()) or ())
    out_keys = set(getattr(tool, "output_path_params", ()) or ())
    op = str(clean.get("operation") or "read").strip().lower()
    is_write = op in _WRITE_OPS

    # Always try to resolve readable file_path when present (even if also output_path_params)
    for key in ("file_path", "file_path_2", *path_keys):
        if key in out_keys and is_write and key == "file_path":
            # write/create: ensure parent under session root
            val = clean.get(key)
            if isinstance(val, str) and val.strip():
                clean[key] = str(
                    resolve_output_path(
                        val, request_id=request_id, workspace_root=workspace_root, default_name=Path(val).name
                    )
                )
            continue
        val = clean.get(key)
        if isinstance(val, str) and val.strip():
            clean[key] = _resolve_existing(val, request_id, workspace_root)
        elif key == "source_files" and isinstance(clean.get(key), list):
            clean[key] = [
                _resolve_existing(item, request_id, workspace_root) if isinstance(item, str) else item
                for item in clean[key]
            ]

    if isinstance(clean.get("merge_files"), list):
        clean["merge_files"] = [
            _resolve_existing(item, request_id, workspace_root) if isinstance(item, str) else item
            for item in clean["merge_files"]
        ]

    for key in out_keys:
        if key == "file_path" and is_write:
            continue  # already handled
        val = clean.get(key)
        if isinstance(val, str) and val.strip():
            clean[key] = str(
                resolve_output_path(
                    val, request_id=request_id, workspace_root=workspace_root, default_name=Path(val).name
                )
            )

    context = _ctx(request_id, workspace_root)
    try:
        data = tool.execute_sync(clean, context)
    except Exception as exc:  # noqa: BLE001
        logger.warning(f"docread tool={getattr(tool, 'name', type(tool).__name__)} failed: {exc}")
        return _fail(str(exc))
    if isinstance(data, ToolResult):
        if not data.success:
            return _fail(data.error or "tool failed")
        return _ok(data.data)
    return _ok(data)


async def run_csv_processor(request_id: str, params: dict[str, Any]) -> dict[str, Any]:
    from reactor_tool.tool.docread.csv_processor import CSVProcessorTool

    result = _run_sync_tool(CSVProcessorTool(), params, request_id)
    if result.get("success") and isinstance(result.get("data"), dict):
        result["data"] = await _maybe_upload_produced_files(request_id, result["data"])
        if "fileInfo" in result["data"]:
            result["fileInfo"] = result["data"].pop("fileInfo")
    return result


async def run_excel_reader(request_id: str, params: dict[str, Any]) -> dict[str, Any]:
    from reactor_tool.tool.docread.excel_reader import ExcelReaderTool

    return _run_sync_tool(ExcelReaderTool(), params, request_id)


async def run_html_processor(request_id: str, params: dict[str, Any]) -> dict[str, Any]:
    from reactor_tool.tool.docread.html_processor import HTMLProcessorTool

    result = _run_sync_tool(HTMLProcessorTool(), params, request_id)
    if result.get("success") and isinstance(result.get("data"), dict):
        result["data"] = await _maybe_upload_produced_files(request_id, result["data"])
        if "fileInfo" in result["data"]:
            result["fileInfo"] = result["data"].pop("fileInfo")
    return result


async def run_markdown_processor(request_id: str, params: dict[str, Any]) -> dict[str, Any]:
    from reactor_tool.tool.docread.markdown_processor import MarkdownProcessorTool

    result = _run_sync_tool(MarkdownProcessorTool(), params, request_id)
    if result.get("success") and isinstance(result.get("data"), dict):
        result["data"] = await _maybe_upload_produced_files(request_id, result["data"])
        if "fileInfo" in result["data"]:
            result["fileInfo"] = result["data"].pop("fileInfo")
    return result


async def run_text_processor(request_id: str, params: dict[str, Any]) -> dict[str, Any]:
    from reactor_tool.tool.docread.text_processor import TextFileProcessorTool

    result = _run_sync_tool(TextFileProcessorTool(), params, request_id)
    if result.get("success") and isinstance(result.get("data"), dict):
        result["data"] = await _maybe_upload_produced_files(request_id, result["data"])
        if "fileInfo" in result["data"]:
            result["fileInfo"] = result["data"].pop("fileInfo")
    return result


async def run_word_reader(request_id: str, params: dict[str, Any]) -> dict[str, Any]:
    from reactor_tool.tool.docread.word_reader import WordReaderTool

    return _run_sync_tool(WordReaderTool(), params, request_id)


async def run_pdf_reader(request_id: str, params: dict[str, Any]) -> dict[str, Any]:
    from reactor_tool.tool.docread.pdf_reader import PDFReaderTool, normalize_pdf_reader_params

    tool = PDFReaderTool()
    clean = normalize_pdf_reader_params(_strip_internal(params))
    clean["workspace_root"] = _workspace(params)
    # re-inject for path resolver
    if _workspace(params):
        clean["workspace_root"] = _workspace(params)
    result = _run_sync_tool(tool, {**params, **clean}, request_id)
    if result.get("success") and isinstance(result.get("data"), dict):
        result["data"] = await _maybe_upload_produced_files(request_id, result["data"])
        if "fileInfo" in result["data"]:
            result["fileInfo"] = result["data"].pop("fileInfo")
    return result


async def run_pdf_structure(request_id: str, params: dict[str, Any]) -> dict[str, Any]:
    from reactor_tool.tool.docread.pdf_research_core import extract_structure

    workspace_root = _workspace(params)
    clean = _strip_internal(params)
    fp = clean.get("file_path")
    if not fp:
        return _fail("file_path is required")
    try:
        path = resolve_input_path(str(fp), request_id=request_id, workspace_root=workspace_root)
        data = extract_structure(str(path))
        return _ok(data)
    except Exception as exc:  # noqa: BLE001
        logger.warning(f"pdf_structure failed: {exc}")
        return _fail(str(exc))


async def run_citation_extractor(request_id: str, params: dict[str, Any]) -> dict[str, Any]:
    from reactor_tool.tool.docread.pdf_research_core import extract_citations

    workspace_root = _workspace(params)
    clean = _strip_internal(params)
    fp = clean.get("file_path")
    if not fp:
        return _fail("file_path is required")
    try:
        path = resolve_input_path(str(fp), request_id=request_id, workspace_root=workspace_root)
        citations = extract_citations(str(path))
        return _ok({"citations": citations, "count": len(citations)})
    except Exception as exc:  # noqa: BLE001
        logger.warning(f"citation_extractor failed: {exc}")
        return _fail(str(exc))


async def run_image_ocr(request_id: str, params: dict[str, Any]) -> dict[str, Any]:
    from reactor_tool.tool.docread.image_ocr import run_image_ocr_sync

    workspace_root = _workspace(params)
    clean = _strip_internal(params)
    fp = clean.get("file_path")
    if not fp:
        return _fail("file_path is required")
    try:
        path = resolve_input_path(str(fp), request_id=request_id, workspace_root=workspace_root)
        data = run_image_ocr_sync(
            str(path),
            prompt=clean.get("prompt") if isinstance(clean.get("prompt"), str) else None,
            lang=clean.get("lang") if isinstance(clean.get("lang"), str) else None,
        )
        return _ok(data)
    except Exception as exc:  # noqa: BLE001
        logger.warning(f"image_ocr failed: {exc}")
        return _fail(str(exc))


RUNNERS = {
    "csv_processor": run_csv_processor,
    "excel_reader": run_excel_reader,
    "html_processor": run_html_processor,
    "markdown_processor": run_markdown_processor,
    "text_processor": run_text_processor,
    "word_reader": run_word_reader,
    "pdf_reader": run_pdf_reader,
    "pdf_structure": run_pdf_structure,
    "citation_extractor": run_citation_extractor,
    "image_ocr": run_image_ocr,
}
