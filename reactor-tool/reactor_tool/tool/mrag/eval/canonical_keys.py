"""MRAG 评测稳定键生成工具。"""

import hashlib
import os
import re
from typing import Any


def _normalize_text(text: str | None) -> str:
    """将文本归一化为稳定 hash 输入。"""

    if not text:
        return ""
    normalized = text.replace("\\n", " ").replace("\\r", " ").replace("\\t", " ")
    return re.sub(r"\s+", " ", normalized).strip()


def _safe_filename(payload: dict[str, Any]) -> str:
    """优先使用 filename，没有则退化到路径 basename。"""

    filename = payload.get("filename")
    if filename:
        return str(filename)
    file_path = payload.get("file_path") or payload.get("image_path") or payload.get("page_path") or "unknown"
    return os.path.basename(str(file_path))


def build_canonical_key(payload: dict[str, Any]) -> str:
    """根据 payload 构建稳定证据键。"""

    chunk_type = str(payload.get("chunk_type") or "").lower()
    filename = _safe_filename(payload)

    if chunk_type in {"text", "ocr_text", "caption"}:
        normalized_text = _normalize_text(payload.get("text"))
        text_hash = hashlib.sha256(normalized_text.encode("utf-8")).hexdigest()[:12]
        return f"text:{filename}:{text_hash}"

    if chunk_type == "image":
        image_name = os.path.basename(str(payload.get("image_path") or "unknown"))
        return f"image:{filename}:{image_name}"

    if chunk_type == "page":
        page_name = os.path.basename(str(payload.get("page_path") or "unknown"))
        return f"page:{filename}:{page_name}"

    raise ValueError(f"Unsupported chunk_type for canonical key: {chunk_type}")


def build_runtime_key(payload: dict[str, Any]) -> str:
    """根据当前 payload 选取最稳定的运行时定位键。"""

    for key in ("file_sorted", "image_id", "page_id", "page_path", "image_path"):
        value = payload.get(key)
        if value:
            return str(value)
    raise ValueError("Payload does not contain a usable runtime key")
