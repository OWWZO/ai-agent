# -*- coding: utf-8 -*-
"""MRAG 侧 LLM / Embedding 请求的统一瞬态错误重试。"""

from __future__ import annotations

import os
import random
import time
from typing import Any, Callable, Iterator, Optional, TypeVar

from reactor_tool.tool.mrag.utils.logger_utils import logger

T = TypeVar("T")

_DEFAULT_MAX_RETRIES = 2
_DEFAULT_BASE_DELAY = 0.5
_DEFAULT_MAX_DELAY = 4.0

_TRANSIENT_STATUS_CODES = {408, 409, 425, 429, 500, 502, 503, 504}
_TRANSIENT_MARKERS = (
    "upstream request failed",
    "temporarily unavailable",
    "service unavailable",
    "gateway timeout",
    "bad gateway",
    "timeout",
    "timed out",
    "connection reset",
    "connection aborted",
    "connection error",
    "remote end closed",
    "broken pipe",
    "too many requests",
    "rate limit",
    "overloaded",
    "server error",
    "internal server error",
    "try again",
    "temporar",
    "eof",
    "ssl",
    "tls",
    "handshake",
    "unexpected_eof",
    "connection closed",
    "protocol error",
)

# 证书/配置类错误通常不可恢复，避免误重试。
_NON_TRANSIENT_MARKERS = (
    "certificate_verify_failed",
    "certificate verify failed",
    "hostname mismatch",
    "wrong version number",
    "unsupported protocol",
    "unknown ca",
    "self signed certificate",
)


def get_mrag_retry_max_attempts() -> int:
    """读取最大重试次数（不含首次请求）。"""
    raw = (os.getenv("MRAG_LLM_MAX_RETRIES") or os.getenv("MRAG_MAX_RETRIES") or str(_DEFAULT_MAX_RETRIES)).strip()
    try:
        return max(0, int(raw))
    except ValueError:
        return _DEFAULT_MAX_RETRIES


def get_mrag_retry_base_delay() -> float:
    raw = (os.getenv("MRAG_LLM_RETRY_BASE_DELAY") or str(_DEFAULT_BASE_DELAY)).strip()
    try:
        return max(0.0, float(raw))
    except ValueError:
        return _DEFAULT_BASE_DELAY


def get_mrag_retry_max_delay() -> float:
    raw = (os.getenv("MRAG_LLM_RETRY_MAX_DELAY") or str(_DEFAULT_MAX_DELAY)).strip()
    try:
        return max(0.0, float(raw))
    except ValueError:
        return _DEFAULT_MAX_DELAY


def _extract_status_code(exc: BaseException) -> Optional[int]:
    for attr in ("status_code", "status", "http_status"):
        value = getattr(exc, attr, None)
        if isinstance(value, int):
            return value
        if isinstance(value, str) and value.isdigit():
            return int(value)

    response = getattr(exc, "response", None)
    if response is not None:
        for attr in ("status_code", "status"):
            value = getattr(response, attr, None)
            if isinstance(value, int):
                return value
            if isinstance(value, str) and value.isdigit():
                return int(value)
    return None


def is_transient_error(exc: BaseException) -> bool:
    """判断是否值得对上游瞬态故障重试。"""
    text = str(exc or "").strip().lower()
    if text and any(marker in text for marker in _NON_TRANSIENT_MARKERS):
        return False

    try:
        import ssl

        if isinstance(exc, ssl.SSLError):
            # 握手中断/连接被对端关闭等可重试；证书校验失败已在上面排除。
            return True
    except Exception:
        pass

    if isinstance(exc, (TimeoutError, ConnectionError, BrokenPipeError, ConnectionResetError, OSError)):
        # OSError 覆盖部分 TLS/网络中断；再用文案过滤非瞬态错误。
        if text and any(marker in text for marker in _NON_TRANSIENT_MARKERS):
            return False
        if isinstance(exc, (TimeoutError, ConnectionError, BrokenPipeError, ConnectionResetError)):
            return True
        # 纯 OSError 仅在命中瞬态文案时重试，避免把本地文件错误也重试。
        return bool(text) and any(marker in text for marker in _TRANSIENT_MARKERS)

    status_code = _extract_status_code(exc)
    if status_code in _TRANSIENT_STATUS_CODES:
        return True

    if not text:
        return False
    return any(marker in text for marker in _TRANSIENT_MARKERS)


def _compute_delay(attempt: int, base_delay: float, max_delay: float) -> float:
    delay = min(max_delay, base_delay * (2 ** attempt))
    jitter = random.uniform(0.0, min(0.25, delay * 0.2 if delay > 0 else 0.0))
    return delay + jitter


def call_with_retry(
    func: Callable[[], T],
    *,
    max_retries: Optional[int] = None,
    base_delay: Optional[float] = None,
    max_delay: Optional[float] = None,
    label: str = "mrag-request",
) -> T:
    """对同步请求做指数退避重试。"""
    retries = get_mrag_retry_max_attempts() if max_retries is None else max(0, max_retries)
    delay_base = get_mrag_retry_base_delay() if base_delay is None else max(0.0, base_delay)
    delay_cap = get_mrag_retry_max_delay() if max_delay is None else max(0.0, max_delay)

    last_error: Optional[BaseException] = None
    for attempt in range(retries + 1):
        try:
            return func()
        except Exception as exc:
            last_error = exc
            if attempt >= retries or not is_transient_error(exc):
                raise
            sleep_for = _compute_delay(attempt, delay_base, delay_cap)
            logger.warning(
                f"[{label}] transient failure (attempt {attempt + 1}/{retries + 1}): {exc}; "
                f"retry in {sleep_for:.2f}s"
            )
            if sleep_for > 0:
                time.sleep(sleep_for)

    assert last_error is not None
    raise last_error


def stream_with_retry(
    open_stream: Callable[[], Iterator[Any]],
    *,
    max_retries: Optional[int] = None,
    base_delay: Optional[float] = None,
    max_delay: Optional[float] = None,
    label: str = "mrag-stream",
) -> Iterator[Any]:
    """
    对流式请求做重试：仅在尚未产出任何 chunk 前允许重开连接。
    """
    retries = get_mrag_retry_max_attempts() if max_retries is None else max(0, max_retries)
    delay_base = get_mrag_retry_base_delay() if base_delay is None else max(0.0, base_delay)
    delay_cap = get_mrag_retry_max_delay() if max_delay is None else max(0.0, max_delay)

    last_error: Optional[BaseException] = None
    for attempt in range(retries + 1):
        yielded_any = False
        try:
            stream = open_stream()
            for chunk in stream:
                yielded_any = True
                yield chunk
            return
        except Exception as exc:
            last_error = exc
            if yielded_any or attempt >= retries or not is_transient_error(exc):
                raise
            sleep_for = _compute_delay(attempt, delay_base, delay_cap)
            logger.warning(
                f"[{label}] stream transient failure before first chunk "
                f"(attempt {attempt + 1}/{retries + 1}): {exc}; retry in {sleep_for:.2f}s"
            )
            if sleep_for > 0:
                time.sleep(sleep_for)

    assert last_error is not None
    raise last_error
