# -*- coding: utf-8 -*-
"""Minimal LeAgent tools.base compatibility shims for docread ports."""
from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Callable


class ToolCategory(str, Enum):
    DOC = "doc"
    WEB = "web"
    DATA = "data"
    GEN = "gen"
    UTIL = "util"


@dataclass
class ToolResult:
    success: bool
    data: Any = None
    error: str | None = None
    duration_ms: int = 0
    metadata: dict[str, Any] = field(default_factory=dict)
    produced_files: list[Any] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        # 兼容层只暴露稳定的基础协议，内部 produced_files 等扩展字段不改变旧消费者结构。
        return {
            "success": self.success,
            "data": self.data,
            "error": self.error,
            "duration_ms": self.duration_ms,
            "metadata": self.metadata,
        }

    @classmethod
    def ok(cls, data: Any, duration_ms: int = 0, **metadata: Any) -> "ToolResult":
        return cls(success=True, data=data, duration_ms=duration_ms, metadata=metadata)

    @classmethod
    def fail(cls, error: str, duration_ms: int = 0, *, data: Any = None, **metadata: Any) -> "ToolResult":
        return cls(success=False, error=error, data=data, duration_ms=duration_ms, metadata=dict(metadata))


@dataclass
class ValidationResult:
    valid: bool
    message: str = ""
    error_code: int = 0


@dataclass
class ToolContext:
    session_id: str | None = None
    request_id: str | None = None
    extra: dict[str, Any] = field(default_factory=dict)
    llm: Any = None
    temp_dir: str | None = None
    file_store: Any = None


class NonRetryableToolError(Exception):
    pass


ToolProgressCallback = Callable[[dict[str, Any]], None]


class SyncTool:
    """Base for ported sync tools. Only provides require_param helpers."""

    name: str = ""
    description: str = ""
    category = ToolCategory.DOC
    path_params: tuple[str, ...] = ()
    output_path_params: tuple[str, ...] = ()

    def require_param(self, params: dict[str, Any], key: str) -> Any:
        # 缺参统一抛不可重试错误，避免执行器对明显的调用契约错误重复调度。
        if key not in params or params[key] is None:
            raise NonRetryableToolError(
                f"Missing required parameter '{key}' for tool '{getattr(self, 'name', 'tool')}'"
            )
        return params[key]

    def execute_sync(self, params: dict[str, Any], context: ToolContext) -> Any:
        raise NotImplementedError


class BaseTool(SyncTool):
    """Async-capable base; ports may implement execute() or execute_sync()."""

    async def execute(self, params: dict[str, Any], context: ToolContext) -> Any:
        # 同步工具通过线程/异步执行器复用同一实现，子类只需覆盖 execute_sync。
        return self.execute_sync(params, context)


class _StructLikeLogger:
    """Accept structlog-style keyword logging; forward to loguru."""

    def __init__(self, name: str):
        self._name = name

    def _emit(self, level: str, event: str, **kwargs: Any) -> None:
        from loguru import logger

        extra = " ".join(f"{k}={v!r}" for k, v in kwargs.items())
        msg = f"[{self._name}] {event}" + (f" {extra}" if extra else "")
        getattr(logger, level)(msg)

    def info(self, event: str, **kwargs: Any) -> None:
        self._emit("info", event, **kwargs)

    def debug(self, event: str, **kwargs: Any) -> None:
        self._emit("debug", event, **kwargs)

    def warning(self, event: str, **kwargs: Any) -> None:
        self._emit("warning", event, **kwargs)

    def error(self, event: str, **kwargs: Any) -> None:
        self._emit("error", event, **kwargs)


def get_struct_logger(name: str) -> _StructLikeLogger:
    return _StructLikeLogger(name)
