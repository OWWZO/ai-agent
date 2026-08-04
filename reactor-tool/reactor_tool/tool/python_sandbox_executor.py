"""Parent-side controller for the persistent code-interpreter Python runner."""
from __future__ import annotations

import json
import os
import subprocess
import sys
import threading
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from reactor_tool.tool.code_interpreter_policy import CodeInterpreterPermissionPolicy


@dataclass
class PythonSandboxExecutionResult:
    stdout: str
    stderr: str
    produced_files: list[dict[str, Any]] = field(default_factory=list)
    result: Any = None
    duration_ms: int = 0
    stdout_truncated: bool = False
    stderr_truncated: bool = False
    returncode: int = 0


class PythonSandboxExecutionError(RuntimeError):
    def __init__(self, response: dict[str, Any]):
        self.stdout = str(response.get("stdout") or "")
        self.stderr = str(response.get("stderr") or "")
        self.produced_files = list(response.get("produced_files") or [])
        self.result = response.get("result")
        self.duration_ms = int(response.get("duration_ms") or 0)
        self.returncode = int(response.get("returncode") or 1)
        super().__init__("\n".join(filter(None, [response.get("error"), self.stderr])) or "Python sandbox execution failed")


class PythonSandboxExecutor:
    """Keeps one isolated Python process for a single code-interpreter run."""

    def __init__(self,
                 policy: CodeInterpreterPermissionPolicy,
                 timeout_seconds: float = 120.0,
                 initial_variables: dict[str, Any] | None = None):
        self._policy = policy
        self._timeout_seconds = timeout_seconds
        self._initial_variables = initial_variables or {}
        self._process: subprocess.Popen[str] | None = None
        self._lock = threading.Lock()
        self._produced_by_path: dict[str, dict[str, Any]] = {}

    def execute(self, code: str) -> PythonSandboxExecutionResult:
        with self._lock:
            # 同一个解释器会话可能保留变量和导入状态，因此请求必须串行发送，不能并发写入 JSONL 管道。
            self._ensure_started()
            response = self._request({"type": "execute", "code": code})
            produced_files = list(response.get("produced_files") or [])
            for item in produced_files:
                path = str(item.get("file_path") or "")
                if path:
                    # 以绝对路径合并多次执行的产物，后续上传只保留每个文件的最新快照。
                    self._produced_by_path[path] = item
            if response.get("status") != "success":
                raise PythonSandboxExecutionError(response)
            return PythonSandboxExecutionResult(
                stdout=str(response.get("stdout") or ""),
                stderr=str(response.get("stderr") or ""),
                produced_files=produced_files,
                result=response.get("result"),
                duration_ms=int(response.get("duration_ms") or 0),
                stdout_truncated=bool(response.get("stdout_truncated")),
                stderr_truncated=bool(response.get("stderr_truncated")),
                returncode=int(response.get("returncode") or 0),
            )

    def produced_files(self) -> list[dict[str, Any]]:
        return list(self._produced_by_path.values())

    def close(self) -> None:
        with self._lock:
            if self._process is None:
                return
            try:
                # 先给 runner 一个协议级 close 机会；请求卡住或进程异常时 finally 再强制终止并回收管道。
                if self._process.poll() is None:
                    self._request({"type": "close"}, allow_closed=True)
            finally:
                if self._process.poll() is None:
                    self._process.kill()
                self._process.wait(timeout=5)
                if self._process.stdin is not None:
                    self._process.stdin.close()
                if self._process.stdout is not None:
                    self._process.stdout.close()
                self._process = None

    def _ensure_started(self) -> None:
        if self._process is not None and self._process.poll() is None:
            return
        runner = Path(__file__).with_name("python_sandbox_runner.py")
        creation_flags = subprocess.CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0
        # cwd 固定到 output/，相对路径落盘（如 savefig('a.png')）默认可被采集上传
        output_dir = Path(self._policy.output_dir).resolve()
        # 子进程 cwd 固定为输出目录，使未显式指定目录的相对产物仍落在可控边界内。
        output_dir.mkdir(parents=True, exist_ok=True)
        self._process = subprocess.Popen(
            [sys.executable, "-I", str(runner)],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            encoding="utf-8",
            errors="replace",
            cwd=str(output_dir),
            env=_sandbox_environment(),
            creationflags=creation_flags,
            start_new_session=os.name != "nt",
        )
        response = self._request({
            "type": "init",
            "policy": _policy_payload(self._policy),
            "initial_variables": self._initial_variables,
        })
        if response.get("type") != "ready":
            # 初始化失败不能复用半初始化进程，否则下一次 execute 会把 execute 请求发给未知状态的 runner。
            if self._process.poll() is None:
                self._process.kill()
                self._process.wait(timeout=5)
            if self._process.stdin is not None:
                self._process.stdin.close()
            if self._process.stdout is not None:
                self._process.stdout.close()
            self._process = None
            raise RuntimeError("Python sandbox runner failed to initialize")

    def _request(self, payload: dict[str, Any], allow_closed: bool = False) -> dict[str, Any]:
        process = self._process
        if process is None or process.stdin is None or process.stdout is None:
            raise RuntimeError("Python sandbox runner is unavailable")
        # JSONL 使用 ASCII-only wire format，避免 Windows 隔离进程的默认编码破坏协议中的非 ASCII 文本。
        # when the child is launched with ``-I`` (PYTHONIOENCODING ignored).
        process.stdin.write(json.dumps(payload, ensure_ascii=True, default=str) + "\n")
        process.stdin.flush()
        response_line = _read_line_with_timeout(process.stdout, self._timeout_seconds)
        if response_line is None:
            # 超时后杀掉当前 runner；持久会话已不再可信，下一次请求会重新初始化干净进程。
            process.kill()
            process.wait(timeout=5)
            if allow_closed:
                return {"type": "closed"}
            raise TimeoutError(f"Python sandbox exceeded {self._timeout_seconds:.0f}s")
        try:
            return json.loads(response_line)
        except json.JSONDecodeError as exc:
            raise RuntimeError("Python sandbox returned an invalid response") from exc


def _read_line_with_timeout(stream, timeout_seconds: float) -> str | None:
    # readline 本身不可取消，用守护线程等待并由父线程控制超时；超时路径由调用方负责杀进程。
    result: list[str] = []
    reader = threading.Thread(target=lambda: result.append(stream.readline()), daemon=True)
    reader.start()
    reader.join(timeout_seconds)
    if reader.is_alive():
        return None
    return result[0] if result and result[0] else None


def _policy_payload(policy: CodeInterpreterPermissionPolicy) -> dict[str, Any]:
    return {
        "profile": policy.profile,
        "workspace_root": policy.workspace_root,
        "output_dir": policy.output_dir,
        "input_file_paths": policy.input_file_paths,
        "allowed_read_paths": list(policy.allowed_read_paths),
        "allowed_read_roots": list(policy.allowed_read_roots),
        "allowed_write_roots": list(policy.allowed_write_roots),
        "authorized_imports": list(policy.authorized_imports),
    }


def _sandbox_environment() -> dict[str, str]:
    allowed = {"PATH", "PYTHONIOENCODING", "LC_ALL", "LC_CTYPE", "LANG", "HOME", "TMPDIR", "TEMP", "TMP"}
    environment = {key: value for key, value in os.environ.items() if key in allowed}
    if os.name == "nt":
        for key in ("SYSTEMROOT", "WINDIR", "USERPROFILE", "APPDATA", "LOCALAPPDATA", "COMSPEC", "PATHEXT"):
            if os.environ.get(key):
                environment[key] = os.environ[key]
    environment["PYTHONIOENCODING"] = "utf-8"
    environment["PYTHONDONTWRITEBYTECODE"] = "1"
    return environment
