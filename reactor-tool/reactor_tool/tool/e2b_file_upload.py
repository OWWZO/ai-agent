"""有限重试 E2B 文件上传的共用实现。"""

from __future__ import annotations

import random
import time
from typing import Any

import httpx
from loguru import logger


# 同一路径覆盖写入具备幂等性，可以安全地重发整批文件。
E2B_FILE_UPLOAD_MAX_RETRIES = 2
E2B_FILE_UPLOAD_BASE_DELAY_SEC = 0.5
E2B_FILE_UPLOAD_MAX_DELAY_SEC = 4.0
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
    """写入一批文件，针对 E2B 瞬态传输错误做有限指数退避重试。"""
    for attempt in range(E2B_FILE_UPLOAD_MAX_RETRIES + 1):
        try:
            write_files = getattr(files_api, "write_files", None)
            if callable(write_files):
                write_files(files)
            else:
                for item in files:
                    files_api.write(item["path"], item["data"])
            return
        except Exception as exc:
            if attempt >= E2B_FILE_UPLOAD_MAX_RETRIES or not _is_retryable_error(exc):
                raise
            delay = _retry_delay(attempt)
            logger.warning(
                "[{}] e2b file upload transient failure type={} retry={}/{} delay={:.2f}s",
                label,
                type(exc).__name__,
                attempt + 1,
                E2B_FILE_UPLOAD_MAX_RETRIES,
                delay,
            )
            if delay > 0:
                time.sleep(delay)


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


def _retry_delay(attempt: int) -> float:
    delay = min(
        E2B_FILE_UPLOAD_MAX_DELAY_SEC,
        E2B_FILE_UPLOAD_BASE_DELAY_SEC * (2**attempt),
    )
    jitter = random.uniform(0.0, min(0.25, delay * 0.2 if delay > 0 else 0.0))
    return delay + jitter
