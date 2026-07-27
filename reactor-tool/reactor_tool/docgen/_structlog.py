"""Minimal structlog-compatible facade backed by loguru."""
from __future__ import annotations

from typing import Any

from loguru import logger as _loguru


class _BoundLogger:
    def __init__(self, name: str = ""):
        self._name = name

    def bind(self, **kwargs: Any) -> "_BoundLogger":
        return self

    def _fmt(self, event: str, kwargs: dict[str, Any]) -> str:
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
    return _BoundLogger(name or "")
