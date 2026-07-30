# -*- coding: utf-8 -*-
"""Dataprep service: LeAgent-aligned tabular data tools."""
from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from loguru import logger

from reactor_tool.tool.docread._compat import ToolContext
from reactor_tool.tool.docread.paths import resolve_input_path, resolve_output_path
from reactor_tool.util.file_util import upload_file_by_path

_MAX_JSON_CHARS = 200_000


def _ctx(request_id: str, workspace_root: str | None = None) -> ToolContext:
    temp = resolve_output_path(
        "dataprep_spill",
        request_id=request_id,
        workspace_root=workspace_root,
        default_name="dataprep_spill",
    ).parent
    return ToolContext(
        session_id=request_id,
        request_id=request_id,
        temp_dir=str(temp),
        extra={
            "request_id": request_id,
            "workspace_root": workspace_root or "",
            "temp_dir": str(temp),
        },
    )


def _workspace(params: dict[str, Any]) -> str | None:
    wr = params.get("workspace_root") or params.get("workspaceRoot")
    return str(wr).strip() if wr else None


def _strip_internal(params: dict[str, Any]) -> dict[str, Any]:
    out = dict(params)
    for k in ("request_id", "requestId", "workspace_root", "workspaceRoot"):
        out.pop(k, None)
    return out


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


def _ok(data: Any) -> dict[str, Any]:
    return {"success": True, "message": "ok", "data": _truncate(data)}


def _fail(message: str) -> dict[str, Any]:
    return {"success": False, "message": message, "data": None}


def _resolve_path_like(value: Any, request_id: str, workspace_root: str | None) -> Any:
    if isinstance(value, str) and value.strip():
        text = value.strip()
        if text.startswith("minio://") or text.startswith("memory://"):
            return text
        try:
            return str(resolve_input_path(text, request_id=request_id, workspace_root=workspace_root))
        except FileNotFoundError:
            return text
    if isinstance(value, dict) and isinstance(value.get("uri"), str):
        uri = value["uri"]
        if uri.startswith("file://"):
            raw = uri[len("file://") :]
            try:
                resolved = str(resolve_input_path(raw, request_id=request_id, workspace_root=workspace_root))
                out = dict(value)
                out["uri"] = f"file://{resolved}" if not resolved.startswith("file://") else resolved
                # bare absolute path is also accepted by records loader
                out["uri"] = resolved
                return out
            except FileNotFoundError:
                return value
        if not uri.startswith("minio://") and not uri.startswith("memory://"):
            try:
                resolved = str(resolve_input_path(uri, request_id=request_id, workspace_root=workspace_root))
                out = dict(value)
                out["uri"] = resolved
                return out
            except FileNotFoundError:
                return value
    return value


def _prepare_params(params: dict[str, Any], request_id: str) -> tuple[dict[str, Any], ToolContext]:
    workspace_root = _workspace(params)
    clean = _strip_internal(params)
    # common single-table path
    if "source_path" in clean:
        clean["source_path"] = _resolve_path_like(clean.get("source_path"), request_id, workspace_root)
    if "artifact" in clean:
        clean["artifact"] = _resolve_path_like(clean.get("artifact"), request_id, workspace_root)
    # merge
    for key in ("left_artifact", "right_artifact"):
        if key in clean:
            clean[key] = _resolve_path_like(clean.get(key), request_id, workspace_root)
    if isinstance(clean.get("datasets"), list):
        clean["datasets"] = [
            _resolve_path_like(item, request_id, workspace_root) if isinstance(item, (str, dict)) else item
            for item in clean["datasets"]
        ]
    # sql tables map
    if isinstance(clean.get("tables"), dict):
        tables = {}
        for name, val in clean["tables"].items():
            tables[name] = _resolve_path_like(val, request_id, workspace_root) if isinstance(val, (str, dict)) else val
        clean["tables"] = tables
    return clean, _ctx(request_id, workspace_root)


async def _maybe_upload_spill(request_id: str, data: dict[str, Any]) -> dict[str, Any]:
    """If result spilled to a local file:// artifact, upload for Java clients."""
    artifact = data.get("artifact") if isinstance(data, dict) else None
    if not isinstance(artifact, dict):
        return data
    uri = str(artifact.get("uri") or "")
    path_str = uri[len("file://") :] if uri.startswith("file://") else uri
    path = Path(path_str)
    if not path.is_file():
        return data
    try:
        info = await upload_file_by_path(str(path), request_id=request_id)
        if info:
            data = dict(data)
            data["fileInfo"] = [info]
            data.setdefault("output_path", str(path))
    except Exception as exc:  # noqa: BLE001
        logger.warning(f"dataprep spill upload failed: {exc}")
    return data


def _run_tool(tool: Any, params: dict[str, Any], request_id: str) -> dict[str, Any]:
    clean, context = _prepare_params(params, request_id)
    try:
        result = tool.execute_sync(clean, context)
        return _ok(result if isinstance(result, dict) else {"result": result})
    except Exception as exc:  # noqa: BLE001
        logger.warning(f"dataprep tool={getattr(tool, 'name', type(tool).__name__)} failed: {exc}")
        return _fail(str(exc))


async def run_data_aggregate(request_id: str, params: dict[str, Any]) -> dict[str, Any]:
    from reactor_tool.tool.dataprep.data_aggregate import DataAggregateTool

    result = _run_tool(DataAggregateTool(), params, request_id)
    if result.get("success") and isinstance(result.get("data"), dict):
        result["data"] = await _maybe_upload_spill(request_id, result["data"])
        if "fileInfo" in result["data"]:
            result["fileInfo"] = result["data"].pop("fileInfo")
    return result


async def run_data_clean(request_id: str, params: dict[str, Any]) -> dict[str, Any]:
    from reactor_tool.tool.dataprep.data_clean import DataCleanTool

    result = _run_tool(DataCleanTool(), params, request_id)
    if result.get("success") and isinstance(result.get("data"), dict):
        result["data"] = await _maybe_upload_spill(request_id, result["data"])
        if "fileInfo" in result["data"]:
            result["fileInfo"] = result["data"].pop("fileInfo")
    return result


async def run_data_merge(request_id: str, params: dict[str, Any]) -> dict[str, Any]:
    from reactor_tool.tool.dataprep.data_merge import DataMergeTool

    result = _run_tool(DataMergeTool(), params, request_id)
    if result.get("success") and isinstance(result.get("data"), dict):
        result["data"] = await _maybe_upload_spill(request_id, result["data"])
        if "fileInfo" in result["data"]:
            result["fileInfo"] = result["data"].pop("fileInfo")
    return result


async def run_data_transform(request_id: str, params: dict[str, Any]) -> dict[str, Any]:
    from reactor_tool.tool.dataprep.data_transform import DataTransformTool

    result = _run_tool(DataTransformTool(), params, request_id)
    if result.get("success") and isinstance(result.get("data"), dict):
        result["data"] = await _maybe_upload_spill(request_id, result["data"])
        if "fileInfo" in result["data"]:
            result["fileInfo"] = result["data"].pop("fileInfo")
    return result


async def run_data_validate(request_id: str, params: dict[str, Any]) -> dict[str, Any]:
    from reactor_tool.tool.dataprep.data_validate import DataValidateTool

    return _run_tool(DataValidateTool(), params, request_id)


async def run_sql_query(request_id: str, params: dict[str, Any]) -> dict[str, Any]:
    from reactor_tool.tool.dataprep.sql_query import SQLQueryTool

    result = _run_tool(SQLQueryTool(), params, request_id)
    if result.get("success") and isinstance(result.get("data"), dict):
        result["data"] = await _maybe_upload_spill(request_id, result["data"])
        if "fileInfo" in result["data"]:
            result["fileInfo"] = result["data"].pop("fileInfo")
    return result


RUNNERS = {
    "data_aggregate": run_data_aggregate,
    "data_clean": run_data_clean,
    "data_merge": run_data_merge,
    "data_transform": run_data_transform,
    "data_validate": run_data_validate,
    "sql_query": run_sql_query,
}
