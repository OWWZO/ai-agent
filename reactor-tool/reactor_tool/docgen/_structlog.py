"""Minimal structlog-compatible facade backed by loguru."""
from __future__ import annotations

from typing import Any

from loguru import logger as _loguru


class _BoundLogger:
    """轻量日志门面，保留 docgen 所需的 bind 和级别方法形状。"""

    def __init__(self, name: str = ""):
        self._name = name

    def bind(self, **kwargs: Any) -> "_BoundLogger":
        # 当前兼容层不保存绑定上下文，只保证调用方无需依赖完整 structlog。
        return self

    def _fmt(self, event: str, kwargs: dict[str, Any]) -> str:
        # 所有级别共用同一格式，便于从工具日志中检索事件名和键值。
        if not kwargs:
            return f"[{self._name}] {event}" if self._name else event
        parts = " ".join(f"{k}={v!r}" for k, v in kwargs.items())
        base = f"[{self._name}] {event}" if self._name else event
        return f"{base} {parts}"

    def debug(self, event: str, **kwargs: Any) -> None:
        _loguru.debug(self._fmt(event, kwargs))

    def info(self, event: str, **kwargs: Any) -> None:
        _loguru.info(self._fmt(event, kwargs))

    def warning(self, event: str, **kwargs: Any) -> None:
        _loguru.warning(self._fmt(event, kwargs))

    def error(self, event: str, **kwargs: Any) -> None:
        _loguru.error(self._fmt(event, kwargs))

    def exception(self, event: str, **kwargs: Any) -> None:
        _loguru.exception(self._fmt(event, kwargs))


def get_logger(name: str | None = None) -> _BoundLogger:
    """返回带可选名称前缀的轻量 logger。"""
    return _BoundLogger(name or "")
