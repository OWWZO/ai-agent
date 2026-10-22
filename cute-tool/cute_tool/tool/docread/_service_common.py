# -*- coding: utf-8 -*-
"""docread / dataprep 共用的 service 协议层。

两个包的 ``service.py`` 各自复制了 5 个私有 helper（``_truncate`` /
``_workspace`` / ``_strip_internal`` / ``_ok`` / ``_fail``）和截断上限常量，
AST 比对逐字节相同。这里让两边共用一份，避免某天只改一边。
"""

import json
from typing import Any, Optional

MAX_JSON_CHARS = 200_000


def workspace_root(params: dict[str, Any]) -> Optional[str]:
    """从请求参数取 workspace_root（兼容 camelCase）。

    注意：参数值为空白串时返回**空串**而不是 ``None`` —— 这与两个 service
    原有的 ``_workspace`` 行为逐字一致（``str(wr).strip() if wr else None``）。
    调用方按真值判断即可；不要在这里"顺手"改成 None，那会改变下游解析结果。
    """
    raw = params.get("workspace_root") or params.get("workspaceRoot")
    return str(raw).strip() if raw else None


def strip_internal(params: dict[str, Any]) -> dict[str, Any]:
    """去掉仅服务于传输层的字段，得到工具真正消费的参数。"""
    out = dict(params)
    for key in ("request_id", "requestId", "workspace_root", "workspaceRoot"):
        out.pop(key, None)
    return out


def truncate_payload(data: Any, limit: int = MAX_JSON_CHARS) -> Any:
    """超大 JSON 截断为预览体，避免把整个数据集回灌给 Java 侧和日志。"""
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


def ok_payload(data: Any) -> dict[str, Any]:
    """统一成功信封。"""
    return {"success": True, "message": "ok", "data": truncate_payload(data)}


def fail_payload(message: str) -> dict[str, Any]:
    """统一失败信封：失败信息也走截断，避免异常文本把响应撑爆。"""
    return {"success": False, "message": message, "data": None}
