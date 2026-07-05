# -*- coding: utf-8 -*-
import contextlib
import contextvars
import json
from functools import wraps
from pathlib import Path
from typing import Any, Callable

import pandas as pd

from reactor_tool.tool.code_interpreter_policy import (
    CodeExecutionPermissionError,
    CodeInterpreterPermissionPolicy,
    is_runtime_double_check_enabled,
    validate_authorized_path,
)


_RUNTIME_PERMISSION_MARKER = "__CI_PERMISSION__"
_ACTIVE_POLICY: contextvars.ContextVar[CodeInterpreterPermissionPolicy | None] = contextvars.ContextVar(
    "code_interpreter_runtime_policy",
    default=None,
)
_GUARD_BYPASS_DEPTH: contextvars.ContextVar[int] = contextvars.ContextVar(
    "code_interpreter_runtime_guard_bypass_depth",
    default=0,
)
_PATCHES_INSTALLED = False


@contextlib.contextmanager
def activate_runtime_io_guard(policy: CodeInterpreterPermissionPolicy):
    """在代码执行期间启用运行时文件 I/O 守卫。"""
    if not is_runtime_double_check_enabled():
        yield
        return
    _ensure_runtime_patches_installed()
    token = _ACTIVE_POLICY.set(policy)
    try:
        yield
    finally:
        _ACTIVE_POLICY.reset(token)


def extract_runtime_permission_error(error_message: str) -> CodeExecutionPermissionError | None:
    """从执行器异常文本中恢复权限错误。"""
    marker_index = error_message.rfind(_RUNTIME_PERMISSION_MARKER)
    if marker_index < 0:
        return None

    payload_text = error_message[marker_index + len(_RUNTIME_PERMISSION_MARKER) :].strip()
    try:
        payload = json.loads(payload_text)
    except Exception:
        return None

    policy_payload = payload.get("policy") or {}
    policy = None
    if policy_payload:
        policy = CodeInterpreterPermissionPolicy(
            profile=policy_payload.get("profile", "analysis"),
            workspace_root=policy_payload.get("workspace_root", ""),
            output_dir=policy_payload.get("output_dir", ""),
            input_file_paths=policy_payload.get("input_file_paths", {}),
            allowed_read_paths=tuple(policy_payload.get("allowed_read_paths", [])),
            allowed_read_roots=tuple(policy_payload.get("allowed_read_roots", [])),
            allowed_write_roots=tuple(policy_payload.get("allowed_write_roots", [])),
            authorized_imports=tuple(policy_payload.get("authorized_imports", [])),
        )

    return CodeExecutionPermissionError(
        payload.get("blockedReason", "permission_denied"),
        payload.get("message", "代码执行权限校验失败。"),
        detail=payload.get("detail"),
        policy=policy,
    )


@contextlib.contextmanager
def _bypass_runtime_guard():
    """在内部路径规范化时临时关闭 guard，避免自递归。"""
    current_depth = _GUARD_BYPASS_DEPTH.get()
    token = _GUARD_BYPASS_DEPTH.set(current_depth + 1)
    try:
        yield
    finally:
        _GUARD_BYPASS_DEPTH.reset(token)


def _ensure_runtime_patches_installed() -> None:
    global _PATCHES_INSTALLED
    if _PATCHES_INSTALLED:
        return

    _patch_path_method("open", _guard_path_open)
    _patch_path_method("read_text", _guard_path_read_method("read_text"))
    _patch_path_method("read_bytes", _guard_path_read_method("read_bytes"))
    _patch_path_method("write_text", _guard_path_write_method("write_text"))
    _patch_path_method("write_bytes", _guard_path_write_method("write_bytes"))
    _patch_path_method("mkdir", _guard_path_write_method("mkdir"))
    _patch_path_method("touch", _guard_path_write_method("touch"))
    _patch_path_method("exists", _guard_path_read_method("exists"))
    _patch_path_method("is_dir", _guard_path_read_method("is_dir"))
    _patch_path_method("is_file", _guard_path_read_method("is_file"))
    _patch_path_method("iterdir", _guard_path_read_method("iterdir"))
    _patch_path_method("glob", _guard_path_read_method("glob"))
    _patch_path_method("rglob", _guard_path_read_method("rglob"))
    _patch_path_method("stat", _guard_path_read_method("stat"))
    _patch_path_method("rename", _guard_path_move_method("rename"))
    _patch_path_method("replace", _guard_path_move_method("replace"))

    _patch_pandas_function("read_csv", access_mode="read", keyword_names=("filepath_or_buffer", "path"))
    _patch_pandas_function("read_excel", access_mode="read", keyword_names=("io", "path"))
    _patch_pandas_function("read_html", access_mode="read", keyword_names=("io", "path"))
    _patch_pandas_function("read_json", access_mode="read", keyword_names=("path_or_buf", "path"))
    _patch_pandas_function("read_table", access_mode="read", keyword_names=("filepath_or_buffer", "path"))

    _patch_dataframe_method("to_csv", access_mode="write", keyword_names=("path_or_buf", "path"))
    _patch_dataframe_method("to_excel", access_mode="write", keyword_names=("excel_writer", "path"))
    _patch_dataframe_method("to_html", access_mode="write", keyword_names=("buf", "path"))
    _patch_dataframe_method("to_json", access_mode="write", keyword_names=("path_or_buf", "path"))
    _patch_dataframe_method("to_markdown", access_mode="write", keyword_names=("buf", "path"))
    _patch_excel_writer_init()

    _patch_matplotlib_savefig()
    _PATCHES_INSTALLED = True


def _patch_path_method(method_name: str, guard_factory: Callable[[Callable[..., Any]], Callable[..., Any]]) -> None:
    original = getattr(Path, method_name, None)
    if original is None or getattr(original, "__ci_guard_patched__", False):
        return
    guarded = guard_factory(original)
    guarded.__ci_guard_patched__ = True
    setattr(Path, method_name, guarded)


def _guard_path_open(original: Callable[..., Any]) -> Callable[..., Any]:
    @wraps(original)
    def guarded(self, *args, **kwargs):
        if _should_bypass_guard():
            return original(self, *args, **kwargs)
        mode = "r"
        if args:
            mode = args[0] or mode
        if "mode" in kwargs and kwargs["mode"]:
            mode = kwargs["mode"]
        access_mode = "write" if any(flag in str(mode) for flag in ("w", "a", "x", "+")) else "read"
        normalized_self = Path(_normalize_path(self, access_mode=access_mode))
        return original(normalized_self, *args, **kwargs)

    return guarded


def _guard_path_read_method(method_name: str) -> Callable[[Callable[..., Any]], Callable[..., Any]]:
    def guard_factory(original: Callable[..., Any]) -> Callable[..., Any]:
        @wraps(original)
        def guarded(self, *args, **kwargs):
            if _should_bypass_guard():
                return original(self, *args, **kwargs)
            normalized_self = Path(_normalize_path(self, access_mode="read"))
            return original(normalized_self, *args, **kwargs)

        guarded.__name__ = method_name
        return guarded

    return guard_factory


def _guard_path_write_method(method_name: str) -> Callable[[Callable[..., Any]], Callable[..., Any]]:
    def guard_factory(original: Callable[..., Any]) -> Callable[..., Any]:
        @wraps(original)
        def guarded(self, *args, **kwargs):
            if _should_bypass_guard():
                return original(self, *args, **kwargs)
            normalized_self = Path(_normalize_path(self, access_mode="write"))
            return original(normalized_self, *args, **kwargs)

        guarded.__name__ = method_name
        return guarded

    return guard_factory


def _guard_path_move_method(method_name: str) -> Callable[[Callable[..., Any]], Callable[..., Any]]:
    def guard_factory(original: Callable[..., Any]) -> Callable[..., Any]:
        @wraps(original)
        def guarded(self, target, *args, **kwargs):
            if _should_bypass_guard():
                return original(self, target, *args, **kwargs)
            normalized_self = Path(_normalize_path(self, access_mode="write"))
            normalized_target = Path(_normalize_path(target, access_mode="write"))
            return original(normalized_self, normalized_target, *args, **kwargs)

        guarded.__name__ = method_name
        return guarded

    return guard_factory


def _patch_pandas_function(function_name: str, *, access_mode: str, keyword_names: tuple[str, ...]) -> None:
    original = getattr(pd, function_name, None)
    if original is None or getattr(original, "__ci_guard_patched__", False):
        return

    @wraps(original)
    def guarded(*args, **kwargs):
        if _should_bypass_guard():
            return original(*args, **kwargs)
        normalized_args, normalized_kwargs = _normalize_first_path_argument(
            args=args,
            kwargs=kwargs,
            access_mode=access_mode,
            keyword_names=keyword_names,
        )
        return original(*normalized_args, **normalized_kwargs)

    guarded.__ci_guard_patched__ = True
    setattr(pd, function_name, guarded)


def _patch_dataframe_method(method_name: str, *, access_mode: str, keyword_names: tuple[str, ...]) -> None:
    original = getattr(pd.DataFrame, method_name, None)
    if original is None or getattr(original, "__ci_guard_patched__", False):
        return

    @wraps(original)
    def guarded(self, *args, **kwargs):
        if _should_bypass_guard():
            return original(self, *args, **kwargs)
        normalized_args, normalized_kwargs = _normalize_first_path_argument(
            args=args,
            kwargs=kwargs,
            access_mode=access_mode,
            keyword_names=keyword_names,
        )
        return original(self, *normalized_args, **normalized_kwargs)

    guarded.__ci_guard_patched__ = True
    setattr(pd.DataFrame, method_name, guarded)


def _patch_excel_writer_init() -> None:
    excel_writer = getattr(pd, "ExcelWriter", None)
    original = getattr(excel_writer, "__init__", None) if excel_writer is not None else None
    if original is None or getattr(original, "__ci_guard_patched__", False):
        return

    @wraps(original)
    def guarded(self, path, *args, **kwargs):
        if _should_bypass_guard():
            return original(self, path, *args, **kwargs)
        normalized_path = _normalize_if_pathlike(path, access_mode="write")
        return original(self, normalized_path, *args, **kwargs)

    guarded.__ci_guard_patched__ = True
    setattr(excel_writer, "__init__", guarded)


def _patch_matplotlib_savefig() -> None:
    try:
        import matplotlib.pyplot as plt
        from matplotlib.figure import Figure
    except Exception:
        return

    for owner in (plt, Figure):
        original = getattr(owner, "savefig", None)
        if original is None or getattr(original, "__ci_guard_patched__", False):
            continue

        @wraps(original)
        def guarded(*args, __original=original, __owner=owner, **kwargs):
            if _should_bypass_guard():
                return __original(*args, **kwargs)
            path_arg_index = 1 if __owner is Figure else 0
            normalized_args, normalized_kwargs = _normalize_path_argument(
                args=args,
                kwargs=kwargs,
                access_mode="write",
                keyword_names=("fname", "filename"),
                path_arg_index=path_arg_index,
            )
            return __original(*normalized_args, **normalized_kwargs)

        guarded.__ci_guard_patched__ = True
        setattr(owner, "savefig", guarded)


def _normalize_first_path_argument(
    *,
    args: tuple[Any, ...],
    kwargs: dict[str, Any],
    access_mode: str,
    keyword_names: tuple[str, ...],
) -> tuple[tuple[Any, ...], dict[str, Any]]:
    return _normalize_path_argument(
        args=args,
        kwargs=kwargs,
        access_mode=access_mode,
        keyword_names=keyword_names,
        path_arg_index=0,
    )


def _normalize_path_argument(
    *,
    args: tuple[Any, ...],
    kwargs: dict[str, Any],
    access_mode: str,
    keyword_names: tuple[str, ...],
    path_arg_index: int,
) -> tuple[tuple[Any, ...], dict[str, Any]]:
    normalized_args = list(args)
    normalized_kwargs = dict(kwargs)

    if len(normalized_args) > path_arg_index and _is_pathlike(normalized_args[path_arg_index]):
        normalized_args[path_arg_index] = _normalize_path(
            normalized_args[path_arg_index],
            access_mode=access_mode,
        )
        return tuple(normalized_args), normalized_kwargs

    for keyword_name in keyword_names:
        if keyword_name in normalized_kwargs and _is_pathlike(normalized_kwargs[keyword_name]):
            normalized_kwargs[keyword_name] = _normalize_path(
                normalized_kwargs[keyword_name],
                access_mode=access_mode,
            )
            break

    return tuple(normalized_args), normalized_kwargs


def _normalize_if_pathlike(value: Any, *, access_mode: str) -> Any:
    if not _is_pathlike(value):
        return value
    return _normalize_path(value, access_mode=access_mode)


def _normalize_path(value: Any, *, access_mode: str) -> str:
    policy = _ACTIVE_POLICY.get()
    if policy is None:
        return str(value)
    try:
        with _bypass_runtime_guard():
            return validate_authorized_path(str(value), policy=policy, access_mode=access_mode)
    except CodeExecutionPermissionError as error:
        raise _encode_runtime_permission_error(error) from error


def _is_pathlike(value: Any) -> bool:
    return isinstance(value, (str, Path))


def _should_bypass_guard() -> bool:
    return _ACTIVE_POLICY.get() is None or _GUARD_BYPASS_DEPTH.get() > 0


def _encode_runtime_permission_error(error: CodeExecutionPermissionError) -> CodeExecutionPermissionError:
    policy_payload = None
    if error.policy is not None:
        policy_payload = {
            "profile": error.policy.profile,
            "workspace_root": error.policy.workspace_root,
            "output_dir": error.policy.output_dir,
            "input_file_paths": error.policy.input_file_paths,
            "allowed_read_paths": list(error.policy.allowed_read_paths),
            "allowed_read_roots": list(error.policy.allowed_read_roots),
            "allowed_write_roots": list(error.policy.allowed_write_roots),
            "authorized_imports": list(error.policy.authorized_imports),
        }

    payload = {
        "message": str(error),
        "blockedReason": error.blocked_reason,
        "detail": error.detail,
        "policy": policy_payload,
    }

    return CodeExecutionPermissionError(
        error.blocked_reason,
        f"{error}\n{_RUNTIME_PERMISSION_MARKER}{json.dumps(payload, ensure_ascii=False)}",
        detail=error.detail,
        policy=error.policy,
    )
