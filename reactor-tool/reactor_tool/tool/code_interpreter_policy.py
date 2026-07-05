# -*- coding: utf-8 -*-
import ast
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Literal


PermissionProfile = Literal["analysis", "workspace"]
PathAccessMode = Literal["read", "write"]


@dataclass(frozen=True)
class CodeInterpreterPermissionPolicy:
    """代码解释器权限策略。"""

    profile: PermissionProfile
    workspace_root: str
    output_dir: str
    input_file_paths: dict[str, str]
    allowed_read_paths: tuple[str, ...]
    allowed_read_roots: tuple[str, ...]
    allowed_write_roots: tuple[str, ...]
    authorized_imports: tuple[str, ...]

    def to_prompt_context(self) -> dict[str, Any]:
        """构建 prompt 中可直接使用的上下文。"""
        helper_names = ["build_output_path", "resolve_input_path", "read_text_file", "write_text_file"]
        if self.profile == "workspace":
            helper_names.append("build_workspace_path")
        return {
            "permission_profile": self.profile,
            "available_helpers": helper_names,
            "input_file_names": list(self.input_file_paths.keys()),
        }

    def to_runtime_variables(self) -> dict[str, Any]:
        """构建注入解释器状态的变量。"""
        return {
            "permission_profile": self.profile,
            "workspace_root": self.workspace_root,
            "output_dir": self.output_dir,
            "input_file_paths": dict(self.input_file_paths),
            "input_files": [
                {"name": file_name, "path": file_path}
                for file_name, file_path in self.input_file_paths.items()
            ],
        }


class CodeExecutionPermissionError(Exception):
    """代码执行权限拒绝错误。"""

    def __init__(
        self,
        blocked_reason: str,
        message: str,
        *,
        detail: str | None = None,
        policy: CodeInterpreterPermissionPolicy | None = None,
    ):
        super().__init__(message)
        self.blocked_reason = blocked_reason
        self.detail = detail or message
        self.policy = policy

    def to_public_payload(self) -> dict[str, Any]:
        """返回面向前端的结构化错误。"""
        payload = {
            "error": str(self),
            "blockedReason": self.blocked_reason,
            "detail": self.detail,
        }
        if self.policy is not None:
            payload["permissionProfile"] = self.policy.profile
            payload["allowedReadRoots"] = list(self.policy.allowed_read_roots)
            payload["allowedWriteRoots"] = list(self.policy.allowed_write_roots)
            payload["allowedInputFiles"] = list(self.policy.input_file_paths.keys())
        return payload


def _env_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def is_pre_execution_validation_enabled() -> bool:
    return _env_bool("CODE_INTERPRETER_ENABLE_PRE_EXECUTION_VALIDATION", False)


def is_path_sandbox_enabled() -> bool:
    return _env_bool("CODE_INTERPRETER_ENABLE_PATH_SANDBOX", False)


def is_runtime_double_check_enabled() -> bool:
    return _env_bool("CODE_INTERPRETER_ENABLE_RUNTIME_DOUBLE_CHECK", False)


_COMMON_AUTHORIZED_IMPORTS = (
    "altair",
    "csv",
    "json",
    "matplotlib",
    "matplotlib.*",
    "numpy",
    "openpyxl",
    "pandas",
    "pathlib",
    "plotly",
    "plotly.*",
    "scipy",
    "scipy.*",
    "seaborn",
    "sklearn",
    "sklearn.*",
    "sqlalchemy",
    "sqlalchemy.*",
    "statsmodels",
    "statsmodels.*",
    "tabulate",
    "yaml",
)

_BLOCKED_IMPORT_MODULES = {
    "ctypes",
    "os",
    "pickle",
    "shutil",
    "subprocess",
    "xlrd",
}

_BLOCKED_CALL_NAMES = {
    "compile",
    "eval",
    "exec",
    "__import__",
    "globals",
    "locals",
}

_DESTRUCTIVE_CALL_NAMES = {
    "rmdir",
    "unlink",
}

_READ_CALLS = {
    "read_csv",
    "read_excel",
    "read_html",
    "read_json",
    "read_table",
    "read_text",
    "read_bytes",
    "resolve_input_path",
    "read_text_file",
}

_WRITE_CALLS = {
    "build_output_path",
    "build_workspace_path",
    "savefig",
    "to_csv",
    "to_excel",
    "to_html",
    "to_json",
    "to_markdown",
    "write_text",
    "write_bytes",
    "write_text_file",
}

_PATH_HELPER_NAMES = frozenset(
    {
        "build_output_path",
        "build_workspace_path",
        "resolve_input_path",
    }
)

_RESERVED_HELPER_NAMES = frozenset(
    _PATH_HELPER_NAMES
    | {
        "read_text_file",
        "write_text_file",
    }
)


@dataclass(frozen=True)
class HelperPathReference:
    """标记由受控 helper 构造、运行时继续校验的路径引用。"""

    helper_name: Literal["build_output_path", "build_workspace_path", "resolve_input_path"]


def build_permission_policy(
    profile: str,
    workspace_root: str,
    output_dir: str,
    input_files: list[dict[str, str]] | None,
) -> CodeInterpreterPermissionPolicy:
    """根据权限档位构建固定策略。"""
    normalized_profile = _normalize_profile(profile)
    workspace_path = str(Path(workspace_root).resolve())
    output_path = str(Path(output_dir).resolve())
    input_file_paths = _normalize_input_files(input_files)

    allowed_read_paths = tuple(sorted(set(input_file_paths.values())))
    if normalized_profile == "workspace":
        allowed_read_roots = (workspace_path,)
        allowed_write_roots = (workspace_path,)
        authorized_imports = _COMMON_AUTHORIZED_IMPORTS
    else:
        allowed_read_roots = (output_path,)
        allowed_write_roots = (output_path,)
        authorized_imports = _COMMON_AUTHORIZED_IMPORTS

    return CodeInterpreterPermissionPolicy(
        profile=normalized_profile,
        workspace_root=workspace_path,
        output_dir=output_path,
        input_file_paths=input_file_paths,
        allowed_read_paths=allowed_read_paths,
        allowed_read_roots=allowed_read_roots,
        allowed_write_roots=allowed_write_roots,
        authorized_imports=tuple(sorted(set(authorized_imports))),
    )


def validate_authorized_path(
    file_path: str,
    *,
    policy: CodeInterpreterPermissionPolicy,
    access_mode: PathAccessMode,
) -> str:
    """按当前权限档位校验并规范化路径。"""
    normalized_path = _resolve_policy_path(file_path, policy=policy, access_mode=access_mode)
    if not is_path_sandbox_enabled():
        return normalized_path

    if access_mode == "read":
        if normalized_path in policy.allowed_read_paths:
            return normalized_path
        if _is_within_roots(normalized_path, policy.allowed_read_roots):
            return normalized_path
        raise CodeExecutionPermissionError(
            "path_outside_allowed_roots",
            f"文件访问超出授权范围：{normalized_path}",
            detail=f"allowed read roots: {list(policy.allowed_read_roots)}",
            policy=policy,
        )

    if normalized_path in policy.allowed_read_paths:
        raise CodeExecutionPermissionError(
            "input_file_read_only",
            "输入文件路径仅允许读取，不能作为写入目标。",
            detail=f"input path: {normalized_path}",
            policy=policy,
        )

    if _is_within_roots(normalized_path, policy.allowed_write_roots):
        return normalized_path

    raise CodeExecutionPermissionError(
        "path_outside_allowed_roots",
        f"文件访问超出授权范围：{normalized_path}",
        detail=f"allowed write roots: {list(policy.allowed_write_roots)}",
        policy=policy,
    )


def build_runtime_helpers(policy: CodeInterpreterPermissionPolicy) -> dict[str, Callable]:
    """构建注入解释器的受控 helper。"""
    input_name_mapping = dict(policy.input_file_paths)

    def build_output_path(file_name: str) -> str:
        target_path = Path(policy.output_dir).joinpath(file_name)
        return validate_authorized_path(str(target_path), policy=policy, access_mode="write")

    def build_workspace_path(relative_path: str) -> str:
        if policy.profile != "workspace":
            raise CodeExecutionPermissionError(
                "profile_capability_denied",
                "当前权限档位不允许构建工作区任意路径，请改用 build_output_path().",
                policy=policy,
            )
        target_path = Path(policy.workspace_root).joinpath(relative_path)
        return validate_authorized_path(str(target_path), policy=policy, access_mode="write")

    def resolve_input_path(file_name: str) -> str:
        normalized_name = (file_name or "").strip()
        if normalized_name not in input_name_mapping:
            raise CodeExecutionPermissionError(
                "input_file_not_found",
                f"未找到输入文件：{normalized_name}",
                detail=f"allowed input files: {sorted(input_name_mapping)}",
                policy=policy,
            )
        return input_name_mapping[normalized_name]

    def read_text_file(file_path: str, encoding: str = "utf-8") -> str:
        normalized_path = validate_authorized_path(file_path, policy=policy, access_mode="read")
        return Path(normalized_path).read_text(encoding=encoding)

    def write_text_file(file_path: str, content: str, encoding: str = "utf-8") -> str:
        normalized_path = validate_authorized_path(file_path, policy=policy, access_mode="write")
        target = Path(normalized_path)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding=encoding)
        return normalized_path

    helpers: dict[str, Callable] = {
        "build_output_path": build_output_path,
        "resolve_input_path": resolve_input_path,
        "read_text_file": read_text_file,
        "write_text_file": write_text_file,
    }
    if policy.profile == "workspace":
        helpers["build_workspace_path"] = build_workspace_path
    return helpers


def validate_code_against_policy(code: str, policy: CodeInterpreterPermissionPolicy) -> None:
    """在执行前做静态权限校验。"""
    if not is_pre_execution_validation_enabled():
        return
    try:
        tree = ast.parse(code)
    except SyntaxError:
        return

    resolved_names: dict[str, Any] = policy.to_runtime_variables()
    helper_functions = build_runtime_helpers(policy)

    for statement in tree.body:
        _validate_statement(
            statement=statement,
            policy=policy,
            resolved_names=resolved_names,
            helper_functions=helper_functions,
        )
        _capture_simple_assignments(
            statement=statement,
            resolved_names=resolved_names,
            helper_functions=helper_functions,
        )


def _normalize_profile(profile: str | None) -> PermissionProfile:
    normalized = (profile or "analysis").strip().lower()
    if normalized not in {"analysis", "workspace"}:
        raise CodeExecutionPermissionError(
            "invalid_permission_profile",
            f"不支持的权限档位：{profile}",
            detail="supported profiles: analysis, workspace",
        )
    return normalized  # type: ignore[return-value]


def _normalize_input_files(input_files: list[dict[str, str]] | None) -> dict[str, str]:
    normalized: dict[str, str] = {}
    for file_info in input_files or []:
        raw_name = (file_info.get("name") or file_info.get("file_name") or "").strip()
        raw_path = (file_info.get("path") or file_info.get("file_path") or "").strip()
        if not raw_name or not raw_path:
            continue
        normalized[raw_name] = str(Path(raw_path).resolve())
    return normalized


def _validate_statement(
    statement: ast.AST,
    policy: CodeInterpreterPermissionPolicy,
    resolved_names: dict[str, Any],
    helper_functions: dict[str, Callable],
) -> None:
    _ensure_helper_names_not_overridden(statement, policy)

    if isinstance(statement, ast.Import):
        for alias in statement.names:
            _ensure_import_allowed(alias.name, policy)
    elif isinstance(statement, ast.ImportFrom):
        _ensure_import_allowed(statement.module or "", policy)

    for node in ast.walk(statement):
        if isinstance(node, ast.Call):
            _ensure_call_allowed(node, policy)
            _validate_path_call(
                node=node,
                policy=policy,
                resolved_names=resolved_names,
                helper_functions=helper_functions,
            )
        elif isinstance(node, ast.Import):
            for alias in node.names:
                _ensure_import_allowed(alias.name, policy)
        elif isinstance(node, ast.ImportFrom):
            _ensure_import_allowed(node.module or "", policy)


def _ensure_helper_names_not_overridden(
    statement: ast.AST,
    policy: CodeInterpreterPermissionPolicy,
) -> None:
    for node in ast.walk(statement):
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
            _raise_if_reserved_helper_name(node.name, policy)
        elif isinstance(node, ast.Name) and isinstance(node.ctx, ast.Store):
            _raise_if_reserved_helper_name(node.id, policy)
        elif isinstance(node, ast.alias):
            imported_name = node.asname or node.name.split(".")[0]
            _raise_if_reserved_helper_name(imported_name, policy)
        elif isinstance(node, ast.arg):
            _raise_if_reserved_helper_name(node.arg, policy)
        elif isinstance(node, ast.ExceptHandler) and node.name:
            _raise_if_reserved_helper_name(node.name, policy)


def _raise_if_reserved_helper_name(name: str | None, policy: CodeInterpreterPermissionPolicy) -> None:
    if not name or name not in _RESERVED_HELPER_NAMES:
        return
    raise CodeExecutionPermissionError(
        "helper_name_override",
        f"禁止重定义受控 helper 名称：{name}",
        detail=f"reserved helper names: {sorted(_RESERVED_HELPER_NAMES)}",
        policy=policy,
    )


def _ensure_import_allowed(module_name: str, policy: CodeInterpreterPermissionPolicy) -> None:
    if not module_name:
        return
    root_module = module_name.split(".")[0]
    if root_module in _BLOCKED_IMPORT_MODULES:
        raise CodeExecutionPermissionError(
            "unauthorized_import",
            f"禁止导入高风险模块：{module_name}",
            policy=policy,
        )
    if not _is_authorized_import(module_name, policy.authorized_imports):
        raise CodeExecutionPermissionError(
            "unauthorized_import",
            f"当前权限档位不允许导入模块：{module_name}",
            detail=f"authorized imports: {list(policy.authorized_imports)}",
            policy=policy,
        )


def _is_authorized_import(module_name: str, authorized_imports: tuple[str, ...]) -> bool:
    for candidate in authorized_imports:
        if candidate.endswith(".*"):
            prefix = candidate[:-2]
            if module_name == prefix or module_name.startswith(prefix + "."):
                return True
            continue
        if module_name == candidate:
            return True
    return False


def _ensure_call_allowed(node: ast.Call, policy: CodeInterpreterPermissionPolicy) -> None:
    function_name = _extract_call_name(node)
    if function_name in _DESTRUCTIVE_CALL_NAMES:
        raise CodeExecutionPermissionError(
            "destructive_operation_denied",
            f"禁止执行删除类文件操作：{function_name}()",
            policy=policy,
        )
    if function_name in _BLOCKED_CALL_NAMES:
        raise CodeExecutionPermissionError(
            "blocked_call",
            f"禁止调用高风险函数：{function_name}()",
            policy=policy,
        )


def _validate_path_call(
    node: ast.Call,
    policy: CodeInterpreterPermissionPolicy,
    resolved_names: dict[str, Any],
    helper_functions: dict[str, Callable],
) -> None:
    function_name = _extract_call_name(node)
    if function_name in _PATH_HELPER_NAMES:
        _resolve_path_expression(
            node=node,
            resolved_names=resolved_names,
            helper_functions=helper_functions,
        )
        return

    if function_name not in _READ_CALLS and function_name not in _WRITE_CALLS and function_name != "open":
        return

    access_mode = _infer_access_mode(node, function_name)
    target_path_node = _extract_path_node(node, function_name)
    if target_path_node is None:
        return

    resolved_path = _resolve_path_expression(
        node=target_path_node,
        resolved_names=resolved_names,
        helper_functions=helper_functions,
    )
    if resolved_path is None:
        return

    if isinstance(resolved_path, HelperPathReference):
        _validate_helper_path_reference(
            reference=resolved_path,
            access_mode=access_mode,
            policy=policy,
        )
        return

    validate_authorized_path(resolved_path, policy=policy, access_mode=access_mode)


def _validate_helper_path_reference(
    reference: HelperPathReference,
    access_mode: PathAccessMode,
    policy: CodeInterpreterPermissionPolicy,
) -> None:
    if not is_path_sandbox_enabled():
        return
    if access_mode == "write" and reference.helper_name == "resolve_input_path":
        raise CodeExecutionPermissionError(
            "input_file_read_only",
            "输入文件路径仅允许读取，不能作为写入目标。",
            detail=f"helper: {reference.helper_name}",
            policy=policy,
        )


def _capture_simple_assignments(
    statement: ast.AST,
    resolved_names: dict[str, Any],
    helper_functions: dict[str, Callable],
) -> None:
    if not isinstance(statement, ast.Assign):
        return
    if len(statement.targets) != 1 or not isinstance(statement.targets[0], ast.Name):
        return
    resolved_value = _resolve_path_expression(
        node=statement.value,
        resolved_names=resolved_names,
        helper_functions=helper_functions,
    )
    if resolved_value is not None:
        resolved_names[statement.targets[0].id] = resolved_value


def _extract_call_name(node: ast.Call) -> str:
    if isinstance(node.func, ast.Name):
        return node.func.id
    if isinstance(node.func, ast.Attribute):
        return node.func.attr
    return ""


def _infer_access_mode(node: ast.Call, function_name: str) -> PathAccessMode:
    if function_name == "open":
        mode_value = "r"
        if len(node.args) >= 2:
            mode_value = _extract_constant_string(node.args[1]) or mode_value
        for keyword in node.keywords:
            if keyword.arg == "mode":
                mode_value = _extract_constant_string(keyword.value) or mode_value
        return "write" if any(flag in mode_value for flag in ("w", "a", "x", "+")) else "read"
    if function_name in _READ_CALLS:
        return "read"
    return "write"


def _extract_path_node(node: ast.Call, function_name: str) -> ast.AST | None:
    if function_name in {"read_text", "read_bytes", "write_text", "write_bytes"} and isinstance(node.func, ast.Attribute):
        return node.func.value

    keyword_mapping = {
        "read_csv": ("filepath_or_buffer", "path"),
        "read_excel": ("io", "path"),
        "read_html": ("io", "path"),
        "read_json": ("path_or_buf", "path"),
        "read_table": ("filepath_or_buffer", "path"),
        "savefig": ("fname", "filename"),
        "to_csv": ("path_or_buf", "path"),
        "to_excel": ("excel_writer", "path"),
        "to_html": ("buf", "path"),
        "to_json": ("path_or_buf", "path"),
        "to_markdown": ("buf", "path"),
        "open": ("file", "path"),
        "read_text_file": ("file_path", "path"),
        "write_text_file": ("file_path", "path"),
    }

    if function_name in _PATH_HELPER_NAMES:
        return node.args[0] if node.args else None

    if node.args:
        return node.args[0]

    for keyword_name in keyword_mapping.get(function_name, ()):
        for keyword in node.keywords:
            if keyword.arg == keyword_name:
                return keyword.value
    return None


def _resolve_path_expression(
    node: ast.AST,
    resolved_names: dict[str, Any],
    helper_functions: dict[str, Callable],
) -> str | HelperPathReference | None:
    if isinstance(node, ast.Constant) and isinstance(node.value, str):
        return node.value

    if isinstance(node, ast.Name):
        value = resolved_names.get(node.id)
        return value if isinstance(value, (str, HelperPathReference)) else None

    if isinstance(node, ast.JoinedStr):
        parts: list[str] = []
        for value in node.values:
            if isinstance(value, ast.Constant) and isinstance(value.value, str):
                parts.append(value.value)
                continue
            if isinstance(value, ast.FormattedValue):
                resolved_part = _resolve_path_expression(value.value, resolved_names, helper_functions)
                if not isinstance(resolved_part, str):
                    return None
                parts.append(resolved_part)
                continue
            return None
        return "".join(parts)

    if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Add):
        left = _resolve_path_expression(node.left, resolved_names, helper_functions)
        right = _resolve_path_expression(node.right, resolved_names, helper_functions)
        if not isinstance(left, str) or not isinstance(right, str):
            return None
        return left + right

    if isinstance(node, ast.Call):
        helper_name = _extract_call_name(node)
        if helper_name in {"Path", "str"}:
            return _resolve_path_expression(node.args[0], resolved_names, helper_functions) if node.args else None
        if helper_name in _PATH_HELPER_NAMES:
            helper = helper_functions.get(helper_name)
            if helper is None:
                return None
            positional_arguments: list[str] = []
            keyword_arguments: dict[str, str] = {}
            for arg in node.args:
                resolved_arg = _resolve_helper_argument(arg, resolved_names, helper_functions)
                if resolved_arg is None:
                    return HelperPathReference(helper_name=helper_name)
                positional_arguments.append(resolved_arg)
            for keyword in node.keywords:
                if keyword.arg is None:
                    return HelperPathReference(helper_name=helper_name)
                resolved_arg = _resolve_helper_argument(keyword.value, resolved_names, helper_functions)
                if resolved_arg is None:
                    return HelperPathReference(helper_name=helper_name)
                keyword_arguments[keyword.arg] = resolved_arg
            try:
                result = helper(*positional_arguments, **keyword_arguments)
            except CodeExecutionPermissionError:
                raise
            except Exception:
                return None
            return result if isinstance(result, str) else None

    if isinstance(node, ast.Subscript) and isinstance(node.value, ast.Name):
        container = resolved_names.get(node.value.id)
        subscript_key = _extract_subscript_key(node.slice)
        if isinstance(container, dict) and subscript_key in container:
            raw_value = container[subscript_key]
            return raw_value if isinstance(raw_value, str) else None

    return None


def _resolve_helper_argument(
    node: ast.AST,
    resolved_names: dict[str, Any],
    helper_functions: dict[str, Callable],
) -> str | None:
    if isinstance(node, ast.Constant) and isinstance(node.value, str):
        return node.value
    resolved_value = _resolve_path_expression(node, resolved_names, helper_functions)
    return resolved_value if isinstance(resolved_value, str) else None


def _extract_subscript_key(node: ast.AST) -> str | None:
    if isinstance(node, ast.Constant) and isinstance(node.value, str):
        return node.value
    if isinstance(node, ast.Index):
        return _extract_subscript_key(node.value)
    return None


def _extract_constant_string(node: ast.AST) -> str | None:
    if isinstance(node, ast.Constant) and isinstance(node.value, str):
        return node.value
    return None


def _resolve_policy_path(
    file_path: str,
    *,
    policy: CodeInterpreterPermissionPolicy,
    access_mode: PathAccessMode,
) -> str:
    raw_path = str(file_path or "").strip()
    if not raw_path:
        raise CodeExecutionPermissionError(
            "empty_path",
            "文件路径不能为空。",
            policy=policy,
        )

    candidate_path = Path(raw_path)
    if candidate_path.is_absolute():
        return str(candidate_path.resolve())

    if access_mode == "read":
        mapped_input_path = _resolve_input_file_name(raw_path, policy)
        if mapped_input_path is not None:
            return mapped_input_path

    base_dir = policy.workspace_root if policy.profile == "workspace" else policy.output_dir
    return str(Path(base_dir).joinpath(candidate_path).resolve())


def _resolve_input_file_name(
    raw_path: str,
    policy: CodeInterpreterPermissionPolicy,
) -> str | None:
    candidate_path = Path(raw_path)
    if len(candidate_path.parts) != 1:
        return None
    return policy.input_file_paths.get(candidate_path.name)


def _is_within_roots(file_path: str, allowed_roots: tuple[str, ...]) -> bool:
    candidate_path = Path(file_path).resolve()
    for root in allowed_roots:
        root_path = Path(root).resolve()
        if candidate_path == root_path or root_path in candidate_path.parents:
            return True
    return False
