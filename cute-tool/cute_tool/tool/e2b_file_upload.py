"""有限重试 E2B 文件上传的共用实现。"""

from __future__ import annotations

import os
import random
import time
from typing import Any

import httpx
from loguru import logger


# 同一路径覆盖写入具备幂等性，可以安全地重发整批文件。
E2B_FILE_UPLOAD_MAX_RETRIES = 2
E2B_FILE_UPLOAD_BASE_DELAY_SEC = 0.5
E2B_FILE_UPLOAD_MAX_DELAY_SEC = 4.0
# E2B SDK 默认 request/read timeout 是 60s；经 Mihomo 上传会被掐断。
E2B_FILE_UPLOAD_TIMEOUT_SEC = 300.0
# 单次 multipart 体积上限。超大单文件仍单独发送。
E2B_FILE_UPLOAD_MAX_BYTES = 512 * 1024
_RETRYABLE_EXCEPTION_NAMES = frozenset(
    {
        "connecterror",
        "connecttimeout",
        "pooltimeout",
        "readerror",
        "readtimeout",
        "remoteprotocolerror",
        "writerror",
        "writetimeout",
    }
)
_RETRYABLE_MARKERS = (
    "connection aborted",
    "connection closed",
    "connection error",
    "connection reset",
    "eof",
    "operation timed out",
    "peer closed",
    "protocol error",
    "timed out",
    "timeout",
    "tls close_notify",
)


def write_e2b_files(files_api: Any, files: list[dict[str, Any]], *, label: str) -> None:
    """按体积拆成多次写入，针对 E2B 瞬态传输错误做有限指数退避重试。"""
    if not files:
        return
    timeout = _upload_timeout_sec()
    write_files = getattr(files_api, "write_files", None)
    for chunk in _split_upload_batches(files, _upload_max_bytes()):
        _write_chunk_with_retry(files_api, write_files, chunk, timeout, label)


def _is_retryable_error(exc: BaseException) -> bool:
    if isinstance(
        exc,
        (
            httpx.RemoteProtocolError,
            httpx.TimeoutException,
            httpx.NetworkError,
            TimeoutError,
            ConnectionError,
            BrokenPipeError,
            ConnectionResetError,
        ),
    ):
        return True
    if type(exc).__name__.casefold() in _RETRYABLE_EXCEPTION_NAMES:
        return True
    text = str(exc).casefold()
    return any(marker in text for marker in _RETRYABLE_MARKERS)


def _write_chunk_with_retry(
    files_api: Any,
    write_files: Any,
    chunk: list[dict[str, Any]],
    timeout: float,
    label: str,
) -> None:
    for attempt in range(E2B_FILE_UPLOAD_MAX_RETRIES + 1):
        try:
            if callable(write_files):
                _call_with_timeout(write_files, chunk, timeout=timeout)
            else:
                for item in chunk:
                    _call_with_timeout(
                        files_api.write,
                        item["path"],
                        item["data"],
                        timeout=timeout,
                    )
            return
        except Exception as exc:
            if attempt >= E2B_FILE_UPLOAD_MAX_RETRIES or not _is_retryable_error(exc):
                raise
            delay = _retry_delay(attempt)
            logger.warning(
                "[{}] e2b file upload transient failure type={} retry={}/{} delay={:.2f}s err={}",
                label,
                type(exc).__name__,
                attempt + 1,
                E2B_FILE_UPLOAD_MAX_RETRIES,
                delay,
                str(exc)[:240],
            )
            if delay > 0:
                time.sleep(delay)


def _payload_bytes(item: dict[str, Any]) -> int:
    data = item.get("data")
    if isinstance(data, (bytes, bytearray, memoryview)):
        return len(data)
    if isinstance(data, str):
        return len(data.encode("utf-8"))
    return 0


def _split_upload_batches(
    files: list[dict[str, Any]], max_bytes: int
) -> list[list[dict[str, Any]]]:
    batches: list[list[dict[str, Any]]] = []
    current: list[dict[str, Any]] = []
    current_bytes = 0
    for item in files:
        size = _payload_bytes(item)
        if current and current_bytes + size > max_bytes:
            batches.append(current)
            current = []
            current_bytes = 0
        current.append(item)
        current_bytes += size
    if current:
        batches.append(current)
    return batches


def _upload_max_bytes() -> int:
    raw = (os.getenv("E2B_FILE_UPLOAD_MAX_BYTES") or "").strip()
    if raw:
        return max(64 * 1024, int(raw))
    return E2B_FILE_UPLOAD_MAX_BYTES


def _upload_timeout_sec() -> float:
    raw = (os.getenv("E2B_FILE_UPLOAD_TIMEOUT_SEC") or "").strip()
    if raw:
        return max(60.0, float(raw))
    return E2B_FILE_UPLOAD_TIMEOUT_SEC


def _call_with_timeout(fn: Any, *args: Any, timeout: float) -> Any:
    try:
        return fn(*args, request_timeout=timeout)
    except TypeError:
        return fn(*args)


def _retry_delay(attempt: int) -> float:
    delay = min(
        E2B_FILE_UPLOAD_MAX_DELAY_SEC,
        E2B_FILE_UPLOAD_BASE_DELAY_SEC * (2**attempt),
    )
    jitter = random.uniform(0.0, min(0.25, delay * 0.2 if delay > 0 else 0.0))
    return delay + jitter
