"""文件名归一化工具，供文件服务和上传客户端共享。"""

import os
import re


_INVALID_FILE_NAME_CHARS = re.compile(r'[<>:"/\\|?*\x00-\x1f]')
_WINDOWS_RESERVED_FILE_NAMES = {
    "CON",
    "PRN",
    "AUX",
    "NUL",
    *(f"COM{index}" for index in range(1, 10)),
    *(f"LPT{index}" for index in range(1, 10)),
}
_MAX_STORED_FILE_NAME_LENGTH = 120
_MAX_STORED_FILE_NAME_BYTES = 240
MAX_REPORT_FILE_NAME_LENGTH = 20


def _truncate_utf8(value: str, max_bytes: int) -> str:
    """按 UTF-8 字节数截断字符串，并保留完整字符。"""
    if max_bytes <= 0:
        return ""
    encoded = value.encode("utf-8")
    if len(encoded) <= max_bytes:
        return value
    return encoded[:max_bytes].decode("utf-8", errors="ignore")


def normalize_stored_file_name(file_name: str) -> str:
    """统一文件名，避免 Windows 非法字符、保留名和超长路径导致落盘失败。"""
    normalized = os.path.basename((file_name or "").strip())
    if not normalized:
        raise ValueError("file_name is empty")

    normalized = _INVALID_FILE_NAME_CHARS.sub("_", normalized).rstrip(" .")
    if not normalized:
        raise ValueError("file_name is empty")

    stem, suffix = os.path.splitext(normalized)
    if stem.upper() in _WINDOWS_RESERVED_FILE_NAMES:
        stem = f"_{stem}"

    suffix = suffix[: _MAX_STORED_FILE_NAME_LENGTH - 1]
    suffix = _truncate_utf8(suffix, _MAX_STORED_FILE_NAME_BYTES)
    max_stem_length = max(1, _MAX_STORED_FILE_NAME_LENGTH - len(suffix))
    stem = _truncate_utf8(
        stem[:max_stem_length],
        _MAX_STORED_FILE_NAME_BYTES - len(suffix.encode("utf-8")),
    )
    return f"{stem}{suffix}"


def normalize_report_file_name(file_name: str | None, fallback: str) -> str:
    """按 DeepSearch 规则归一化 Markdown 报告名，长度按 Unicode 字符计数。"""
    requested = (file_name or "").strip()
    if not requested:
        requested = fallback
    requested = requested.replace("/", "_").replace("\\", "_")
    if not requested.lower().endswith(".md"):
        requested = f"{requested}.md"
    normalized = normalize_stored_file_name(requested)
    if len(normalized) <= MAX_REPORT_FILE_NAME_LENGTH:
        return normalized

    fallback_name = (fallback or "报告.md").strip()
    if not fallback_name.lower().endswith(".md"):
        fallback_name = f"{fallback_name}.md"
    fallback_name = normalize_stored_file_name(fallback_name)
    return (
        fallback_name
        if len(fallback_name) <= MAX_REPORT_FILE_NAME_LENGTH
        else "报告.md"
    )
