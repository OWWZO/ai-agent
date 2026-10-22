# -*- coding: utf-8 -*-
"""E2B 远程命令执行 seam。

8 个调用点原先各自重复两段样板：
``getattr(commands, "run", None)`` 取 runner，以及
"exit_code ∈ {None,0} 且没有 error 字段"的成败判定。
这些判定已经漂移过（有的还额外检查 ``result.error``，有的没有），
所以这里收敛成一组原语，迁移时逐个替换而不是继续复制。
"""

from typing import Any, Callable, Optional

SANDBOX_RUN_UNSUPPORTED = "E2B sandbox does not support commands.run"


def remote_runner(sandbox: Any, *, unsupported_message: str = SANDBOX_RUN_UNSUPPORTED) -> Callable:
    """取出沙箱的 ``commands.run``；不支持时抛 ``RuntimeError``。"""
    commands = getattr(sandbox, "commands", None)
    run = getattr(commands, "run", None) if commands is not None else None
    if not callable(run):
        raise RuntimeError(unsupported_message)
    return run


def command_succeeded(result: Any) -> bool:
    """E2B 命令是否成功：exit_code 为 None（未上报）或 0，且没有 error 字段。"""
    return getattr(result, "exit_code", 0) in (None, 0) and not getattr(
        result, "error", None
    )


def command_detail(result: Any) -> str:
    """把 stdout/stderr 拼成一行错误详情，供异常消息使用。"""
    return " ".join(
        part.strip()
        for part in (
            str(getattr(result, "stdout", "") or ""),
            str(getattr(result, "stderr", "") or ""),
        )
        if part and part.strip()
    )


def check_command(
    result: Any,
    *,
    failure_prefix: str,
) -> Any:
    """校验单次远程命令结果，失败时抛 ``RuntimeError(detail 或 prefix + exit_code)``。"""
    exit_code = getattr(result, "exit_code", None)
    if exit_code not in (None, 0) or getattr(result, "error", None):
        raise RuntimeError(command_detail(result) or f"{failure_prefix} {exit_code}")
    return result


def run_checked(
    sandbox: Any,
    command: str,
    *,
    timeout: int,
    failure_prefix: str,
    user: Optional[str] = None,
    unsupported_message: str = SANDBOX_RUN_UNSUPPORTED,
) -> Any:
    """执行远程命令并校验结果；返回原始 result 供调用方读取 stdout。

    ``user`` 仅在需要 root 时传（例如 VNC 启动）；其余调用点不传。
    """
    run = remote_runner(sandbox, unsupported_message=unsupported_message)
    kwargs: dict[str, Any] = {"timeout": timeout}
    if user is not None:
        kwargs["user"] = user
    result = run(command, **kwargs)
    return check_command(result, failure_prefix=failure_prefix)
